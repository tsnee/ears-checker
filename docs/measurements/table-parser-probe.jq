# How often each candidate parser, with GitHub's table extension, gives the same blocks as GitHub's
# renderer on the Markdown conformance fixtures, per run and parser, then per suite where it
# differs, from table-parser-probe.jsonl:
#   jq -s -r -f docs/measurements/table-parser-probe.jq docs/measurements/table-parser-probe.jsonl
# Each record is one fixture (its suite, name, and content, from the suite at `commit`) rendered by
# one parser; `blocks` and `github` are the two HTML outputs reduced to their tables, rows, cells,
# paragraphs, headings, list items, code, breaks, and quotes, each with its text.
group_by([.date, .commit, .parser, .version]) | map(
  "\(.[0].date) \(.[0].commit[0:7]) \(.[0].parser) \(.[0].version): "
  + "\(map(select(.agrees)) | length) of \(length) agree"
  + (map(select(.agrees | not)) | group_by(.suite)
     | map("\n  \(.[0].suite): \(length) differ (\(map(.fixture | tostring) | join(", ")))")
     | join(""))
) | .[]
