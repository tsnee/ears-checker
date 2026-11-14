# Two scripts/time-cli runs side by side, from time-cli.jsonl: for each build, the seconds of each
# input in the run named --arg before and in the one named --arg after, and after / before.
#   jq -s -r --arg before '<run>' --arg after '<run>' -f docs/measurements/time-cli-compare.jq \
#     docs/measurements/time-cli.jsonl
# One row per input (Markdown, then plain text, by size), one column group per build in both.
def seconds: (. * 100 | round) as $c
  | "\($c / 100 | floor).\($c % 100 | tostring | if length < 2 then "0" + . else . end)";
def ratio: (. * 10 | round) as $t | "\($t / 10 | floor).\($t % 10)×";
$ARGS.named.before as $before | $ARGS.named.after as $after
| (map(select(.run == $before)) | map({key: "\(.build) \(.input) \(.copies)", value: .seconds})
   | from_entries) as $was
| map(select(.run == $after)) as $cells
| if ($was | length) == 0 then error("no records for run \($before)")
  elif ($cells | length) == 0 then error("no records for run \($after)") else . end
| [ "jvm", "node", "native" | select(. as $b | $cells | any(.build == $b)) ] as $builds
| ("| Input |" + ($builds | map(" \(.) before | \(.) after | \(.) ratio |") | add)),
  ("|---|" + ($builds | map("---|---|---|") | add)),
  ( $cells | map({input, copies}) | unique | sort_by((.input != "md"), .copies) | .[]
    | . as $row
    | "| \($row.input) x\($row.copies) |"
      + ($builds | map(. as $b
          | ($cells | map(select(.build == $b and .input == $row.input
              and .copies == $row.copies)) | first | .seconds) as $now
          | $was["\($b) \($row.input) \($row.copies)"] as $then
          | " \($then | seconds) | \($now | seconds) | \($now / $then | ratio) |") | add) )
