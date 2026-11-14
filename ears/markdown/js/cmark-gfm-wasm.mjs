// cmark-gfm's WebAssembly module, read from beside this file. JavaScript, not Scala.js, so that it
// can sit beside the module wherever the build puts it: the tests import it through an import map
// from a directory holding both, and the CLI's npm package holds it, unbundled, beside main.js
// (whose import the CLI's link points here) and the module.
import { readFileSync } from "node:fs";
export const bytes = readFileSync(new URL("./cmark-gfm.wasm", import.meta.url));
