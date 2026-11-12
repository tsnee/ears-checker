# How often a property test's input generator yields a valid requirement (one that classifies), before and
# after a change to it, from generator-reach.jsonl:
#   jq -s -r -f docs/measurements/generator-reach.jq docs/measurements/generator-reach.jsonl
def percent: (. * 10000 | round) / 100;
"| Property | Before | After |",
"|---|---|---|",
(group_by(.property) | .[]
  | (map(select(.stage == "before")) | first) as $b
  | (map(select(.stage == "after")) | first) as $a
  | "| \(.[0].property) | \($b.classified)/\($b.draws) (\($b.classified / $b.draws | percent)%) | \($a.classified)/\($a.draws) (\($a.classified / $a.draws | percent)%) |")
