#!/usr/bin/env bash
# PreToolUse (Edit|Write|MultiEdit|NotebookEdit): protect generated files, applied Flyway migrations,
# secrets and the guard rails themselves.
source "$(dirname "$0")/_lib.sh"
path="$(hin '.tool_input.file_path')"; [[ -z "$path" ]] && path="$(hin '.tool_input.notebook_path')"
rel="$(rel_path "$path")"
[[ -z "$rel" ]] && exit 0

case "$rel" in
    backend/build/*|backend/.gradle/*|frontend/node_modules/*|frontend/dist/*|frontend/test-results/*|frontend/playwright-report/*|logs/*|captures/*)
        pre_tool_decision deny "$rel is generated or machine-local output; never edit it. Change the source instead." ;;
    frontend/src/api/schema.gen.ts)
        pre_tool_decision deny "schema.gen.ts is generated from the backend's OpenAPI spec. Change the Java controller/DTO, then run 'cd frontend && npm run api:types' with the backend running (tools/dev.sh up)." ;;
    frontend/package-lock.json)
        pre_tool_decision deny "package-lock.json is written by npm. Change dependencies with npm install/uninstall (in frontend/)." ;;
    backend/gradle/wrapper/gradle-wrapper.jar)
        pre_tool_decision deny "The Gradle wrapper jar is binary and generated. Upgrade with: cd backend && ./gradlew wrapper --gradle-version <x>" ;;
    backend/gradle/wrapper/gradle-wrapper.properties)
        pre_tool_decision ask "Changing the Gradle wrapper by hand. Prefer: cd backend && ./gradlew wrapper --gradle-version <x> (updates jar + scripts consistently)." ;;
    backend/src/main/resources/db/migration/V*)
        if is_tracked "$rel" || { ! git -C "$ROOT" rev-parse --git-dir >/dev/null 2>&1 && [[ -e "$ROOT/$rel" ]]; }; then
            pre_tool_decision ask "$rel is an already-committed Flyway migration. Editing it changes its checksum and breaks every database that already applied it. Add a NEW migration (next V number) instead - proceed only if this migration has never left your machine."
        fi ;;
    .githooks/*|.claude/hooks/*|.claude/settings.json)
        pre_tool_decision ask "Editing the project's guard rails ($rel). Proceed only if the user asked for it." ;;
esac
base="$(basename "$rel")"
if [[ "$base" =~ ^\.env(\..+)?$ && "$base" != .env.example ]]; then
    pre_tool_decision ask "$rel holds environment secrets and is gitignored. Put placeholders in .env.example instead; edit .env only if the user asked."
fi
exit 0
