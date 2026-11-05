package io.github.tsnee.ears.markdown

import scala.annotation.tailrec

/** Tree walks with an explicit stack: Markdown can nest thousands of levels deep (emphasis, block
  * quotes), deeper than a recursive walk's stack allows on Node or Scala Native.
  */
private[markdown] object Walk:

  /** Every node under `root`, `root` first, in document order. */
  def preorder[N](root: N, children: N => List[N]): List[N] =
    @tailrec
    def loop(stack: List[N], visited: List[N]): List[N] = stack match
      case Nil          => visited.reverse
      case node :: rest => loop(children(node) ++ rest, node :: visited)
    loop(List(root), Nil)
