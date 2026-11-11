# A scripts/time-cli run's Markdown table, as in docs/performance.md, from time-cli.jsonl. The
# latest run by default, or the one named with --arg run:
#   jq -s -r -f docs/measurements/time-cli.jq docs/measurements/time-cli.jsonl
#   jq -s -r --arg run '<run>' -f docs/measurements/time-cli.jq docs/measurements/time-cli.jsonl
# One row per input (Markdown, then plain text, by size), one column per build that ran.
def seconds: (. * 100 | round) as $c
  | "\($c / 100 | floor).\($c % 100 | tostring | if length < 2 then "0" + . else . end)";
($ARGS.named.run // (last | .run)) as $run
| map(select(.run == $run)) as $cells
| if ($cells | length) == 0 then error("no records for run \($run)") else . end
| [ "jvm", "node", "native" | select(. as $b | $cells | any(.build == $b)) ] as $builds
| ("| Input | Bytes |" + ($builds | map(" \(.) |") | add)),
  ("|---|---|" + ($builds | map("---|") | add)),
  ($cells | group_by([.input, .copies]) | .[]
    | . as $row
    | "| \(.[0].input) x\(.[0].copies) | \(.[0].bytes) |"
      + ($builds | map(. as $b | " \($row | map(select(.build == $b)) | .[0].seconds | seconds) |")
         | add))
