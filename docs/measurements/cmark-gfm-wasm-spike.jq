# cmark-gfm compiled to WebAssembly, per runtime, from cmark-gfm-wasm-spike.jsonl:
#   jq -r -f docs/measurements/cmark-gfm-wasm-spike.jq docs/measurements/cmark-gfm-wasm-spike.jsonl
# For each: how many of the conformance fixtures (at `commit`) render the same as native cmark-gfm,
# as HTML and as XML with source positions; the time to load the module; and the median time to
# render the corpus (1.9 MB of this repository's Markdown) as HTML, warm.
def median: sort | .[(length / 2 | floor)];
"\(.date) \(.runtime): "
+ (if .htmlMatches == null then "" else "\(.htmlMatches)/\(.fixtures) html, \(.xmlMatches)/\(.fixtures) xml match native; " end)
+ (if .loadedMs == null then "" else "loads in \(.loadedMs | round) ms; " end)
+ "corpus median \(.roundsMs | median | . * 10 | round / 10) ms"
+ (if .wasmBytes != null and .htmlMatches != null then " (wasm \(.wasmBytes) bytes)" else "" end)
