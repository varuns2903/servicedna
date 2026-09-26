#!/usr/bin/env bash
# The ServiceDNA GitHub Action's logic (see action.yml), kept in a script so it can be run and
# tested outside GitHub. Reads GitHub's GITHUB_* variables and the action's INPUT_* ones.
set -euo pipefail

event="${GITHUB_EVENT_NAME:-}"
default_branch="$(jq -r '.repository.default_branch // empty' "${GITHUB_EVENT_PATH:-/dev/null}" 2>/dev/null || true)"
flows="${INPUT_FLOWS:-flows}"
report="${RUNNER_TEMP:-/tmp}/servicedna-report.md"
marker="<!-- servicedna-test-flows -->"

decide() { # decide <input> <auto condition>
  case "$1" in
    true) return 0 ;;
    false) return 1 ;;
    *) [ "$2" = yes ] ;;
  esac
}

on_default_push=no
[ "$event" = push ] && [ -n "$default_branch" ] && [ "${GITHUB_REF_NAME:-}" = "$default_branch" ] && on_default_push=yes
pr_with_flows=no
[ "$event" = pull_request ] || [ "$event" = pull_request_target ] && [ -e "$flows" ] && pr_with_flows=yes

status=0
if decide "${INPUT_SCAN:-auto}" "$on_default_push"; then
  echo "::group::sdna scan"
  sdna scan
  echo "::endgroup::"
fi

if decide "${INPUT_TEST:-auto}" "$pr_with_flows"; then
  echo "::group::sdna test run $flows"
  args=(test run "$flows" --report "$report")
  [ -n "${INPUT_ENVIRONMENT:-}" ] && args+=(--env "$INPUT_ENVIRONMENT")
  sdna "${args[@]}" || status=$?
  echo "::endgroup::"

  if [ -s "$report" ]; then
    [ -n "${GITHUB_STEP_SUMMARY:-}" ] && cat "$report" >> "$GITHUB_STEP_SUMMARY"
    pr="$(jq -r '.pull_request.number // empty' "${GITHUB_EVENT_PATH:-/dev/null}" 2>/dev/null || true)"
    if [ "${INPUT_COMMENT:-true}" = true ] && [ -n "$pr" ] && [ -n "${GH_TOKEN:-}" ]; then
      body="$(printf '%s\n%s' "$marker" "$(cat "$report")")"
      # One comment per PR, updated on every push rather than piling up.
      existing="$(gh api "repos/$GITHUB_REPOSITORY/issues/$pr/comments" --paginate \
        --jq ".[] | select(.body | startswith(\"$marker\")) | .id" | head -n1)"
      if [ -n "$existing" ]; then
        gh api -X PATCH "repos/$GITHUB_REPOSITORY/issues/comments/$existing" -f body="$body" >/dev/null
      else
        gh api -X POST "repos/$GITHUB_REPOSITORY/issues/$pr/comments" -f body="$body" >/dev/null
      fi
      echo "Test results posted on #$pr"
    fi
  elif [ "$status" -ne 0 ]; then
    echo "::error::sdna test run failed before any results (status $status)"
  fi
fi
exit "$status"
