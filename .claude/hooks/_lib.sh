# Shared helpers for Claude Code hook scripts (bash + jq).
# Hook contract: JSON arrives on stdin; we answer with JSON on stdout (or nothing) and exit 0.
# A hook must never block work because the hook itself is broken: on internal errors, exit 0.
source "$(dirname "${BASH_SOURCE[0]}")/../../tools/_lib.sh"

if ! have jq; then
    echo "hook $(basename "$0"): jq not installed - guard rails disabled. Install jq." >&2
    exit 0
fi

HOOK_INPUT="$(cat)"
# Reads a field from the hook input, e.g. hin '.tool_input.file_path'
hin() { jq -r "$1 // empty" <<<"$HOOK_INPUT" 2>/dev/null; }

pre_tool_decision() { # allow|deny|ask reason
    jq -nc --arg d "$1" --arg r "$2" \
        '{hookSpecificOutput: {hookEventName: "PreToolUse", permissionDecision: $d, permissionDecisionReason: $r}}'
    exit 0
}

state_dir() { mkdir -p "$ROOT/.claude/state" && printf '%s\n' "$ROOT/.claude/state"; }

# Project-relative path of a tool's file argument, or empty when outside the project.
rel_path() {
    local p="$1"
    [[ -z "$p" ]] && return 0
    [[ "$p" != /* ]] && p="$ROOT/$p"
    p="$(realpath -m -- "$p")"
    case "$p" in "$ROOT"/*) printf '%s\n' "${p#"$ROOT"/}" ;; esac
}

is_tracked() { git -C "$ROOT" ls-files --error-unmatch -- "$1" >/dev/null 2>&1; }
