# The timing table in docs/performance.md, "Compiling Scala Native one at a time", from
# native-compile-rounds.jsonl:
#   jq -s -r -f docs/measurements/native-compile-rounds.jq docs/measurements/native-compile-rounds.jsonl
# Failed rounds (the plugin's race) are left out of the times; the median is the lower middle.
def mean: (add / length * 100 | round) / 100;
"| Jobs | Rounds timed | Min | Median | Max | Mean |",
"|---|---|---|---|---|---|",
(group_by(.jobs) | sort_by(.[0].jobs // 0) | .[]
  | (if .[0].jobs == null then "default (parallel)" else "`-j \(.[0].jobs)`" end) as $label
  | (map(select(.status == 0) | .seconds) | sort) as $s
  | "| \($label) | \($s | length) | \($s[0]) | \($s[(($s | length) - 1) / 2 | floor]) | \($s[-1]) | \($s | mean) |")
