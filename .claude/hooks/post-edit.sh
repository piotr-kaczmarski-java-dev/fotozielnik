#!/usr/bin/env bash
# PostToolUse (Edit|Write|MultiEdit): marks backend/frontend as changed for the Stop check, and catches
# Spring/Java mistakes the compiler does not report.
source "$(dirname "$0")/_lib.sh"
rel="$(rel_path "$(hin '.tool_input.file_path')")"
[[ -z "$rel" ]] && exit 0
full="$ROOT/$rel"
notes=()

case "$rel" in
    backend/*.java|backend/*.kts|backend/*.toml|backend/*.yml|backend/*.yaml|backend/*.properties|backend/*.sql)
        touch "$(state_dir)/backend-dirty" ;;
    frontend/*.ts|frontend/*.tsx|frontend/*.js|frontend/*.mjs|frontend/*.json|frontend/*.css|frontend/*.html)
        touch "$(state_dir)/frontend-dirty" ;;
esac

if [[ "$rel" == backend/src/*.java && -f "$full" ]]; then
    src="$(cat -- "$full")"
    file="$(basename "$rel" .java)"
    # 1. package must match the folder (javac does not enforce it; Spring scanning and tests rely on it)
    if [[ "$rel" =~ ^backend/src/[^/]+/java/(.+)/[^/]+\.java$ ]]; then
        want="${BASH_REMATCH[1]//\//.}"
        have_pkg="$(grep -oP '^\s*package\s+\K[\w.]+(?=\s*;)' <<<"$src" | head -1)"
        [[ -n "$have_pkg" && "$have_pkg" != "$want" ]] && notes+=("$rel declares package $have_pkg but lives in $want.")
    fi
    # 2. public top-level type == file name
    pub="$(grep -oP '^public\s+(?:(?:final|abstract|sealed|non-sealed|static)\s+)*(?:class|interface|enum|record|@interface)\s+\K\w+' <<<"$src" | head -1)"
    [[ -n "$pub" && "$pub" != "$file" && "$file" != package-info ]] && notes+=("public type $pub is in $file.java - javac requires the names to match.")
    if [[ "$rel" == backend/src/main/* ]]; then
        # 3. field injection
        grep -qP '^\s*@Autowired\s*(\n|\r|$)|@Autowired\s+(private|protected)?\s*(final\s+)?[\w<>, ?]+\s+\w+\s*;' <<<"$src" \
            && notes+=("$rel uses @Autowired field injection - use constructor injection (final fields, single constructor).")
        # 4. console output instead of logging
        grep -qP 'System\.(out|err)\.print|\.printStackTrace\(\)' <<<"$src" \
            && notes+=("$rel writes to System.out/printStackTrace - use an SLF4J Logger.")
        # 5. pre-Jakarta / deprecated nullness imports
        grep -qP '^import\s+javax\.(persistence|validation|servlet|annotation\.(PostConstruct|PreDestroy))' <<<"$src" \
            && notes+=("$rel imports javax.* - Spring Boot 3+ uses jakarta.*.")
        grep -qP '^import\s+(org\.springframework\.lang\.(Nullable|NonNull)|javax\.annotation\.(Nullable|Nonnull)|jakarta\.annotation\.Nullable)' <<<"$src" \
            && notes+=("$rel uses a non-JSpecify nullness annotation - use org.jspecify.annotations.Nullable (NullAway).")
        # 6. API change -> frontend types drift
        grep -qP '@(RestController|RequestMapping|GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping)\b' <<<"$src" \
            && notes+=("API code changed: if request/response shapes changed, regenerate frontend types (backend running): cd frontend && npm run api:types.")
    fi
fi

if [[ "$rel" == backend/src/main/resources/db/migration/* ]]; then
    base="$(basename "$rel")"
    if [[ ! "$base" =~ ^(V([0-9]+)(_[0-9]+)*__[A-Za-z0-9_]+|R__[A-Za-z0-9_]+)\.sql$ ]]; then
        notes+=("Flyway will not pick up '$base': name it V<number>__<snake_description>.sql.")
    elif [[ -n "${BASH_REMATCH[2]}" ]] && git -C "$ROOT" rev-parse --verify -q HEAD >/dev/null 2>&1 && ! is_tracked "$rel"; then
        v=$((10#${BASH_REMATCH[2]}))
        max="$(git -C "$ROOT" ls-files -- 'backend/src/main/resources/db/migration/V*' | grep -oP '/V\K[0-9]+' | sort -n | tail -1)"
        [[ -n "$max" ]] && (( v <= 10#$max )) && notes+=("New migration $base has version $v but V$max is already committed - Flyway rejects out-of-order versions. Use V$((10#$max + 1)).")
    fi
fi

if [[ "$rel" =~ ^backend/src/main/resources/application.*\.(yml|yaml|properties)$ && -f "$full" ]]; then
    grep -qP 'ddl-auto\s*[:=]\s*(update|create|create-drop)' "$full" \
        && notes+=("$rel sets hibernate ddl-auto to create/update - the schema is owned by Flyway; keep 'validate'.")
fi

if (( ${#notes[@]} )); then
    jq -nc --arg c "Project check: $(printf '%s | ' "${notes[@]}")" \
        '{hookSpecificOutput: {hookEventName: "PostToolUse", additionalContext: $c}}'
fi
exit 0
