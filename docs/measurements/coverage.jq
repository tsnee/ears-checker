# Each module's latest coverage, as a Markdown table, from coverage.jsonl:
#   jq -s -r -f docs/measurements/coverage.jq docs/measurements/coverage.jsonl
# The latest record is the last one written for the module; rates are shown to two decimals
# whatever jq version reads them.
def percent: (. * 100 | round) as $c
  | "\($c / 100 | floor).\($c % 100 | tostring | if length < 2 then "0" + . else . end)%";
"| Module | Statements | Invoked | Statement rate | Branch rate | Commit | Measured |",
"|---|---|---|---|---|---|---|",
(group_by(.module) | map(last) | sort_by(.module) | .[]
  | "| \(.module) | \(.statements) | \(.invoked) | \(.statement_rate | percent) | "
    + "\(.branch_rate | percent) | \(.commit) | \(.measured // "") |")
