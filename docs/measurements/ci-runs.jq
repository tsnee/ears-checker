# Minutes per CI run attempt, newest first, from ci-runs.jsonl (each job's wall-clock time rounded
# up to a whole minute; public repositories' runs are free):
#   jq -s -r -f docs/measurements/ci-runs.jq docs/measurements/ci-runs.jsonl
# One line per attempt: when it started, the run and attempt, the commit, its jobs' conclusions,
# and the minutes of the jobs that ran in it (a rerun's carried jobs count in the earlier one).
def count($c): map(select(.conclusion == $c)) | length;
group_by([.run, .attempt]) | map({
  started: (map(select(.carried | not) | .started) | min), run: .[0].run,
  attempt: .[0].attempt, commit: .[0].commit[0:7], jobs: length,
  passed: count("success"), skipped: count("skipped"),
  failed: (length - count("success") - count("skipped")),
  rerun: (map(select(.carried | not)) | length),
  minutes: (map(select(.carried | not) | .minutes) | add // 0)
}) | sort_by(.started) | reverse | .[]
| "\(.started) run \(.run) attempt \(.attempt) \(.commit): \(.passed) passed, "
  + "\(.skipped) skipped, \(.failed) failed, "
  + (if .attempt > 1 then "\(.rerun) rerun, " else "" end) + "\(.minutes) minutes"
