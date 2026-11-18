#!/usr/bin/env bash
# PreToolUse (Bash): block commands that silently destroy source or data, and ask before commands
# with destructive or outward-facing effects.
source "$(dirname "$0")/_lib.sh"
cmd="$(hin '.tool_input.command')"
[[ -z "$cmd" ]] && exit 0
# Ignore the body of heredocs that only write text (cat/tee > file): a script being written is not run.
# Heredocs fed to a shell or interpreter (bash <<EOF) are still scanned.
if have perl; then
    cmd="$(perl -0pe 's/((?:\bcat|\btee)\b[^\n]*?<<-?[ \t]*([\x27"]?)(\w+)\2[^\n]*\n).*?^[ \t]*\3[ \t]*$/$1/gms' <<<"$cmd")"
fi

shopt -s nocasematch
del='(^|[[:space:];&|(])(rm|rmdir|unlink|shred)[[:space:]][^|;&]*'
top='(\./)?(backend|frontend|docs|tools|\.git|\.githooks|\.claude|backend/src|frontend/src)/?([[:space:]"'"'"';&|)]|$)'
if [[ "$cmd" =~ ${del}(^|[[:space:]\"\'])${top} ]]; then
    pre_tool_decision deny "This command deletes a top-level project folder. Delete specific files instead."
fi
if [[ "$cmd" =~ ${del}db/migration/V ]]; then
    pre_tool_decision deny "Deleting a Flyway migration breaks databases that applied it (and lint-repo rejects it if committed). Undo its effect with a new migration. If it was never committed or applied, ask the user to delete it."
fi

ask_if() { [[ "$cmd" =~ $1 ]] && pre_tool_decision ask "$2"; }
ask_if 'docker([[:space:]]+-[^[:space:]]+)*[[:space:]]+compose[^|;&]*[[:space:]]down[^|;&]*[[:space:]](-v|--volumes)' 'docker compose down -v deletes the local database volume (all dev data).'
ask_if 'docker[[:space:]]+(volume[[:space:]]+(rm|prune)|system[[:space:]]+prune)' 'Deletes Docker volumes/data (local databases).'
ask_if '(flywayClean|flyway[[:space:]]+clean)' 'Flyway clean drops every object in the schema.'
ask_if '(psql|pg_|docker[^|;&]*exec)[^|;&]*(drop[[:space:]]+(database|schema|table)|truncate[[:space:]])' 'Destructive SQL against a database.'
ask_if 'git[[:space:]]+clean[^|;&]*[[:space:]]-[a-z]*[xX]' 'git clean -x/-X also deletes ignored files (node_modules, build output, .env).'
ask_if 'git[[:space:]]+reset[^|;&]*--hard' 'git reset --hard discards uncommitted work.'
ask_if 'git[[:space:]]+push[^|;&]*([[:space:]]-f([[:space:]]|$)|--force)' 'Force push rewrites remote history.'
ask_if 'git[[:space:]]+(commit|push)[^|;&]*--no-verify' 'Bypasses the git hooks (lint/check/tests). AGENTS.md: only when the user says so.'
ask_if 'npm[[:space:]]+(i|install|add)[[:space:]]+([^-|;&[:space:]][^|;&]*|[^|;&]*[[:space:]][^-|;&[:space:]][^|;&]*)$' 'Adds an npm dependency (runs third-party install scripts). AGENTS.md: new dependencies need a reason in the summary.'
ask_if 'npm[[:space:]]+(uninstall|remove|rm)[[:space:]]' 'Removes an npm dependency.'
ask_if '(npm[[:space:]]+publish|gh[[:space:]]+repo[[:space:]]+(delete|archive|edit[^|;&]*--visibility))' 'Publishes or changes a remote repository/package.'
exit 0
