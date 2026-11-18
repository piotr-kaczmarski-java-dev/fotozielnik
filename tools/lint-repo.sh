#!/usr/bin/env bash
# Repository lint (~1 s, no build): catches mistakes compilers and tests do not.
#   1. Flyway: committed migrations are immutable (edit/delete/rename = checksum failure on every existing
#      database), file names follow V<n>__<desc>.sql / R__<desc>.sql, no duplicate versions.
#   2. Secrets: private keys, cloud/API tokens, .env files.
#   3. Generated or machine-local files: build/, node_modules/, dist/, logs/, captures/, *.class, stray jars.
#   4. Files over 1 MB.  5. Merge-conflict markers.
#
# Usage: tools/lint-repo.sh [--staged | --base <ref>]
#   --staged      only what is about to be committed (pre-commit hook)
#   --base <ref>  migrations are compared with the merge base of <ref> instead of HEAD (CI: origin/main)
set -uo pipefail
source "$(dirname "$0")/_lib.sh"
cd "$ROOT" || exit 3

staged=0 base=""
while (( $# )); do
    case "$1" in
        --staged) staged=1 ;;
        --base) base="${2:-}"; shift ;;
        -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
        *) echo "unknown argument: $1"; exit 3 ;;
    esac
    shift
done
MIG_DIR="backend/src/main/resources/db/migration"
MAX_BYTES=$((1024 * 1024))
problems=()
add() { problems+=("$1"); }

is_git=0; git rev-parse --git-dir >/dev/null 2>&1 && is_git=1
has_head=0; (( is_git )) && git rev-parse --verify -q HEAD >/dev/null && has_head=1

if (( staged )); then
    (( is_git )) || { fail "--staged needs a git repository"; exit 3; }
    mapfile -t files < <(git diff --cached --name-only --diff-filter=ACMR)
elif (( is_git )); then
    mapfile -t files < <(git ls-files --cached --others --exclude-standard | sort -u)
else
    mapfile -t files < <(find . -type f -not -path './.git/*' -not -path '*/node_modules/*' -not -path '*/build/*' \
        -not -path './backend/.gradle/*' -not -path './frontend/dist/*' -not -path './logs/*' -not -path './captures/*' \
        -not -path './frontend/test-results/*' -not -path './frontend/playwright-report/*' -not -path './.claude/state/*' | sed 's#^\./##')
fi

content() { # staged blob or working-tree file
    if (( staged )); then git show ":$1" 2>/dev/null; else cat -- "$1" 2>/dev/null; fi
}
size_of() {
    if (( staged )); then git cat-file -s ":$1" 2>/dev/null || echo 0; else stat -c %s -- "$1" 2>/dev/null || echo 0; fi
}

# ---------------------------------------------------------------- 1. Flyway migrations
if (( has_head )); then
    compare=HEAD
    if [[ -n "$base" ]]; then
        compare="$(git merge-base "$base" HEAD 2>/dev/null)" || { fail "--base: cannot find a merge base with '$base' (fetch it; CI needs fetch-depth: 0)"; exit 3; }
    fi
    if (( staged )); then diff_cmd=(git diff --cached --name-status -M "$compare" -- "$MIG_DIR")
    else diff_cmd=(git diff --name-status -M "$compare" -- "$MIG_DIR"); fi
    while IFS=$'\t' read -r st a b; do
        [[ -z "$st" ]] && continue
        base="$(basename "$a")"
        [[ "$base" == R__* ]] && continue                     # repeatable migrations may change
        case "$st" in
            M*) add "Flyway: $a is already committed and was MODIFIED. Applied migrations are immutable - revert it and add a new V<next>__*.sql." ;;
            D*) add "Flyway: $a is already committed and was DELETED. Restore it; undo its effect with a new migration." ;;
            R*) add "Flyway: $a was RENAMED to $b. Renaming an applied migration changes its version/description - restore the old name." ;;
        esac
    done < <("${diff_cmd[@]}")
fi
declare -A seen=()
for f in "$MIG_DIR"/*; do
    [[ -e "$f" ]] || continue
    base="$(basename "$f")"
    if [[ ! "$base" =~ ^(V([0-9]+(_[0-9]+)*)__[A-Za-z0-9_]+|R__[A-Za-z0-9_]+)\.sql$ ]]; then
        add "Flyway: bad migration file name '$base' (expected V<number>__<snake_description>.sql or R__<description>.sql)"
        continue
    fi
    v="${BASH_REMATCH[2]}"
    if [[ -n "$v" ]]; then
        v="$(sed -E 's/^0+([0-9])/\1/' <<<"$v")"
        [[ -n "${seen[$v]:-}" ]] && add "Flyway: duplicate version $v in ${seen[$v]} and $base"
        seen[$v]="$base"
    fi
done

# ---------------------------------------------------------------- per-file checks
declare -A gen_seen=()
# Prints the outermost generated/machine-local directory containing $1 (e.g. frontend/node_modules/), or fails.
generated_dir() {
    local IFS=/ seg prefix="" i=0
    local -a segs
    read -ra segs <<<"$1"
    for seg in "${segs[@]:0:${#segs[@]}-1}"; do
        prefix+="$seg/"
        case "$seg" in build|node_modules|dist|.gradle|test-results|playwright-report|coverage) echo "$prefix"; return 0 ;; esac
        if (( i == 0 )) && [[ "$seg" == logs || "$seg" == captures ]]; then echo "$prefix"; return 0; fi
        [[ "$prefix" == .claude/state/ ]] && { echo "$prefix"; return 0; }
        i=$((i + 1))
    done
    return 1
}
secret_rx='-----BEGIN ([A-Z]+ )?PRIVATE KEY-----|AKIA[0-9A-Z]{16}|ghp_[A-Za-z0-9]{36}|github_pat_[A-Za-z0-9_]{50,}|sk-ant-[A-Za-z0-9_-]{20,}|sk-[A-Za-z0-9]{40,}|xox[baprs]-[A-Za-z0-9-]{10,}|AIza[0-9A-Za-z_-]{35}'
for f in "${files[@]}"; do
    [[ -z "$f" ]] && continue
    (( staged )) || [[ -f "$f" ]] || continue
    # 3. generated / machine-local
    if gen_dir="$(generated_dir "$f")"; then
        [[ -z "${gen_seen[$gen_dir]:-}" ]] && add "Generated/machine-local files tracked under $gen_dir (unstage: git rm -r --cached $gen_dir)"
        gen_seen[$gen_dir]=1
        continue
    fi
    [[ "$f" == *.class ]] && add "Compiled class tracked: $f"
    [[ "$f" == *.jar && "$f" != */gradle/wrapper/gradle-wrapper.jar ]] && add "Jar tracked: $f (dependencies come from Maven Central)"
    base="$(basename "$f")"
    if [[ "$base" =~ ^\.env(\..+)?$ && "$base" != .env.example && "$base" != .env.sample ]]; then
        add "Environment file with possible secrets: $f (commit a .env.example instead)"
    fi
    # 4. size
    sz="$(size_of "$f")"
    (( sz > MAX_BYTES )) && add "Large file ($((sz / 1024)) KB): $f - keep binaries out of git or justify it"
    # binary? skip text checks
    if content "$f" | head -c 8000 | grep -qP '\x00'; then continue; fi
    # 2. secrets
    case "$f" in tools/lint-repo.sh) ;; *)
        if content "$f" | grep -qE -- "$secret_rx"; then add "Possible secret in $f (key/token pattern). Use environment variables or a secret store."; fi ;;
    esac
    # 5. conflict markers
    if content "$f" | grep -qE '^(<<<<<<< |>>>>>>> )'; then add "Merge-conflict markers in $f"; fi
done

if (( ${#problems[@]} )); then
    fail "lint-repo: ${#problems[@]} problem(s)"
    printf '  - %s\n' "${problems[@]}"
    exit 1
fi
ok "lint-repo: ${#files[@]} file(s) checked$( (( staged )) && echo ' (staged)')"
