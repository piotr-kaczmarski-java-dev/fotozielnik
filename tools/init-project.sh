#!/usr/bin/env bash
# Turns a fresh copy of this template into a new project. Run once, from the copy.
#   - Renames the Java base package (moves source folders, rewrites package/imports), Gradle group,
#     artifact (rootProject.name, spring.application.name) and the frontend package name.
#   - Replaces {{PROJECT_NAME}} placeholders in AGENTS.md, CLAUDE.md, README.md, docs/ and the frontend.
#   - Git: optionally drops the template's history (--reset-git), initialises a repo, enables .githooks.
# Safe to re-run: it reads the current package/group from backend/build.gradle.kts.
#
# Usage: tools/init-project.sh --name "My App" [--group com.acme] [--artifact my-app] [--package com.acme.myapp]
#                              [--reset-git] [--skip-git]
#   defaults: --group com.example, --artifact = name in kebab-case, --package = group + artifact without dashes
set -euo pipefail
shopt -u patsub_replacement 2>/dev/null || true   # '&' in a name must stay literal in ${x//a/b}
source "$(dirname "$0")/_lib.sh"
cd "$ROOT"

name="" group="com.example" artifact="" pkg="" reset_git=0 skip_git=0
while (( $# )); do
    case "$1" in
        --name) name="$2"; shift ;;
        --group) group="$2"; shift ;;
        --artifact) artifact="$2"; shift ;;
        --package) pkg="$2"; shift ;;
        --reset-git) reset_git=1 ;;
        --skip-git) skip_git=1 ;;
        -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
        *) echo "unknown argument: $1"; exit 3 ;;
    esac
    shift
done
[[ -n "$name" ]] || { echo "--name is required"; exit 3; }
[[ -n "$artifact" ]] || artifact="$(tr '[:upper:]' '[:lower:]' <<<"$name" | sed -E 's/[^a-z0-9]+/-/g; s/^-+|-+$//g')"
[[ "$artifact" =~ ^[a-z][a-z0-9-]*$ ]] || { echo "artifact '$artifact' must match [a-z][a-z0-9-]* (pass --artifact)"; exit 3; }
[[ -n "$pkg" ]] || pkg="$group.${artifact//-/}"
java_pkg_rx='^[a-z_][a-z0-9_]*(\.[a-z_][a-z0-9_]*)*$'
[[ "$group" =~ $java_pkg_rx ]] || { echo "group '$group' is not a valid Java package name"; exit 3; }
[[ "$pkg" =~ $java_pkg_rx ]] || { echo "package '$pkg' is not a valid Java package name"; exit 3; }

echo "Initialising '$name' (artifact $artifact, package $pkg) in $ROOT"

replace_in() { # file old new  (literal, all occurrences); returns 1 when nothing to replace
    local f="$1" c
    c="$(<"$f")"
    [[ "$c" == *"$2"* ]] || return 1
    printf '%s\n' "${c//"$2"/"$3"}" >"$f"
}

# ---------------------------------------------------------------- Java package
gradle_file="$BACKEND/build.gradle.kts"
old_pkg="$(grep -oP '^val basePackage = "\K[^"]+' "$gradle_file")"
old_group="$(grep -oP '^group = "\K[^"]+' "$gradle_file")"
if [[ "$old_pkg" != "$pkg" ]]; then
    for set in main test; do
        base="$BACKEND/src/$set/java"
        from="$base/${old_pkg//.//}"
        to="$base/${pkg//.//}"
        [[ -d "$from" ]] || continue
        # via a temp dir, so the new package may also be nested inside the old one
        tmp="$(mktemp -d "$base/.move.XXXXXX")"
        mv "$from" "$tmp/src"
        find "$base" -mindepth 1 -type d -empty -not -path "$tmp*" -delete
        mkdir -p "$(dirname "$to")"
        mv "$tmp/src" "$to"
        rmdir "$tmp"
    done
    # rewrite package/import statements (word boundary: com.example.app must not match com.example.apple)
    old_rx="${old_pkg//./\\.}"
    grep -rlZ --include='*.java' -E "\b${old_rx}\b" "$BACKEND/src" | xargs -0 -r sed -i -E "s/\b${old_rx}\b/${pkg}/g"
    sed -i -E "s/^val basePackage = \".*\"/val basePackage = \"$pkg\"/" "$gradle_file"
    echo "  Java package: $old_pkg -> $pkg"
fi
sed -i -E "s/^group = \".*\"/group = \"$group\"/" "$gradle_file"
[[ "$old_group" != "$group" ]] && echo "  Gradle group: $old_group -> $group"

# ---------------------------------------------------------------- artifact / app name
old_artifact="$(grep -oP '^rootProject\.name = "\K[^"]+' "$BACKEND/settings.gradle.kts")"
if [[ "$old_artifact" != "$artifact" ]]; then
    sed -i -E "s/^rootProject\.name = \".*\"/rootProject.name = \"$artifact\"/" "$BACKEND/settings.gradle.kts"
    # first 'name:' under spring.application in application.yml
    sed -i -E "0,/^(    name: )$old_artifact\$/s//\1$artifact/" "$BACKEND/src/main/resources/application.yml"
    grep -rlZ --include='*Test.java' "value(\"$old_artifact\")" "$BACKEND/src/test" | xargs -0 -r sed -i "s/value(\"$old_artifact\")/value(\"$artifact\")/"
    echo "  Artifact / spring.application.name: $old_artifact -> $artifact"
fi
if have jq; then
    tmpf="$(mktemp)"
    jq --arg n "$artifact-frontend" '.name = $n' "$FRONTEND/package.json" >"$tmpf" && mv "$tmpf" "$FRONTEND/package.json"
    if [[ -f "$FRONTEND/package-lock.json" ]]; then
        tmpf="$(mktemp)"
        jq --arg n "$artifact-frontend" '.name = $n | .packages[""].name = $n' "$FRONTEND/package-lock.json" >"$tmpf" \
            && mv "$tmpf" "$FRONTEND/package-lock.json"
    fi
fi

# ---------------------------------------------------------------- placeholders
n=0
mapfile -t files < <(ls AGENTS.md CLAUDE.md README.md frontend/index.html 2>/dev/null; find docs -name '*.md')
for f in "${files[@]}"; do replace_in "$f" '{{PROJECT_NAME}}' "$name" && n=$((n + 1)) || true; done
js_name="${name//\\/\\\\}"
js_name="${js_name//\'/\\\'}"
replace_in frontend/src/App.tsx "{'{{PROJECT_NAME}}'}" "{'$js_name'}" && n=$((n + 1)) || true
echo "  Placeholders replaced in $n file(s) ({{ONE_LINE_PITCH}} is filled by /kickoff)"

# ---------------------------------------------------------------- git
if (( ! skip_git )); then
    if [[ -d .git && $reset_git == 1 ]]; then
        rm -rf .git
        echo "  Removed template git history"
    fi
    if [[ ! -d .git ]]; then
        git init -q -b main
        echo "  git init (branch main)"
    fi
    git config core.hooksPath .githooks
    chmod +x .githooks/* tools/*.sh .claude/hooks/*.sh backend/gradlew
    echo "  git hooks enabled (.githooks: repo lint on commit, check + unit tests on push)"
fi

cat <<MSG

Next steps:
  1. cd frontend && npm ci && npx playwright install chromium
  2. tools/check.sh && tools/test.sh        (Docker running for *IT tests)
  3. git add -A && git commit -m "Initial project from template"
  4. Start Claude Code here and run /kickoff to define the app.
MSG
