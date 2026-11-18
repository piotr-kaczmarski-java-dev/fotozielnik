#!/usr/bin/env bash
# Stop: if backend or frontend files changed during this turn, run the fast static check
# (tools/check.sh --fix: formats Java, compiles with Error Prone/NullAway, TS typecheck + lint).
# Errors are fed back to Claude (decision=block) up to 3 times in a row, then released to the user.
source "$(dirname "$0")/_lib.sh"
state="$(state_dir)"
sides=()
[[ -f "$state/backend-dirty" ]] && sides+=(backend)
[[ -f "$state/frontend-dirty" ]] && sides+=(frontend)
(( ${#sides[@]} )) || exit 0
target=all; (( ${#sides[@]} == 1 )) && target="${sides[0]}"

out="$("$ROOT/tools/check.sh" "$target" --fix 2>&1)"; code=$?
attempts_file="$state/stop-attempts"

if (( code == 1 )); then
    attempts=$(( $(cat "$attempts_file" 2>/dev/null || echo 0) + 1 ))
    if (( attempts > 3 )); then
        rm -f "$attempts_file"
        jq -nc --arg m "Static check still failing after 3 fix attempts:"$'\n'"$out" '{systemMessage: $m}'
        exit 0
    fi
    echo "$attempts" >"$attempts_file"
    jq -nc --arg r "Static check (attempt $attempts/3) failed for the code you changed. Fix it before finishing. If the code is intentionally incomplete, tell the user so explicitly."$'\n'"$out" \
        '{decision: "block", reason: $r}'
    exit 0
fi

rm -f "$state/backend-dirty" "$state/frontend-dirty" "$attempts_file"
[[ -n "$out" ]] && jq -nc --arg m "$out" '{systemMessage: $m}'
exit 0
