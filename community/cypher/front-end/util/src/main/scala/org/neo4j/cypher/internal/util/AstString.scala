/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.cypher.internal.util

import scala.collection.Map

object AstString {

  final case class Config(
    width: Int = 120,
    indentStep: Int = 2,
    maxDepth: Int = 60,
    maxElements: Int = 200
  )

  def render(value: Any, config: Config = Config()): String =
    new Renderer(config).renderTop(value)

  final private class Renderer(cfg: Config) {
    private val inProgress = new java.util.IdentityHashMap[AnyRef, java.lang.Boolean]()

    def renderTop(value: Any): String =
      choose(value, indent = 0, col = 0, depth = cfg.maxDepth)

    private def choose(value: Any, indent: Int, col: Int, depth: Int): String = {
      val flat = renderFlat(value, depth)
      if (!flat.contains('\n') && (col + flat.length) <= cfg.width) flat
      else renderBroken(value, indent, depth)
    }

    private def renderFlat(value: Any, depth: Int): String = {
      if (depth <= 0) return "…"
      value match {
        case null => "null"

        // Avoid consuming iterators in debug printing
        case it: Iterator[?] => s"${className(it)}(<iterator>)"

        case a: Array[?] =>
          val prefix = "Array"
          val elems = a.iterator.take(cfg.maxElements).map(renderFlat(_, depth - 1)).mkString(", ")
          val suffix = if (a.length > cfg.maxElements) s", … +${a.length - cfg.maxElements} more" else ""
          s"$prefix($elems$suffix)"

        case m: Map[?, ?] =>
          val prefix = stringPrefix(m)
          val shown = m.iterator.take(cfg.maxElements).map { case (k, v) =>
            s"${renderFlat(k, depth - 1)} -> ${renderFlat(v, depth - 1)}"
          }.mkString(", ")
          val suffix = if (m.size > cfg.maxElements) s", … +${m.size - cfg.maxElements} more" else ""
          s"$prefix($shown$suffix)"

        case it: Iterable[?] =>
          val prefix = stringPrefix(it)
          val sizeHint = it.knownSize
          val shown = it.iterator.take(cfg.maxElements).map(renderFlat(_, depth - 1)).mkString(", ")
          val suffix =
            if (sizeHint >= 0 && sizeHint > cfg.maxElements) s", … +${sizeHint - cfg.maxElements} more"
            else ""
          s"$prefix($shown$suffix)"

        case p: Product if isScalaTuple(p) =>
          val elems = p.productIterator.map(renderFlat(_, depth - 1)).mkString(", ")
          s"($elems)"

        case p: Product =>
          if (p.productArity == 0) p.productPrefix
          else {
            val elems = p.productIterator.map(renderFlat(_, depth - 1)).mkString(", ")
            s"${p.productPrefix}($elems)"
          }

        case other =>
          other.toString
      }
    }

    private def renderBroken(value: Any, indent: Int, depth: Int): String = {
      if (depth <= 0) return "…"

      value match {
        case null            => "null"
        case it: Iterator[?] => s"${className(it)}(<iterator>)"

        case ref: AnyRef =>
          if (inProgress.containsKey(ref)) return s"<recursion:${className(ref)}@${System.identityHashCode(ref)}>"
          inProgress.put(ref, true)
          try return renderBrokenNonRecursive(value, indent, depth)
          finally inProgress.remove(ref)
        case _ =>
          renderBrokenNonRecursive(value, indent, depth)
      }
    }

    private def renderBrokenNonRecursive(value: Any, indent: Int, depth: Int): String = value match {
      case a: Array[?] =>
        brokenContainer(
          prefix = "Array",
          elems = a.iterator.toList,
          indent = indent,
          depth = depth
        )

      case m: Map[?, ?] =>
        val prefix = stringPrefix(m)
        val elems = m.iterator.toList.map { case (k, v) => Arrow(k, v) }
        brokenContainer(prefix, elems, indent, depth)

      case it: Iterable[?] =>
        brokenContainer(stringPrefix(it), it.iterator.toList, indent, depth)

      case p: Product if isScalaTuple(p) =>
        brokenParenTuple(p, indent, depth)

      case p: Product =>
        if (p.productArity == 0) p.productPrefix
        else brokenProduct(p.productPrefix, p.productIterator.toList, indent, depth)

      case other =>
        other.toString
    }

    private def brokenParenTuple(p: Product, indent: Int, depth: Int): String = {
      val elems = p.productIterator.toList
      brokenDelimited("(", ")", elems, indent, depth, renderElem = (x, ind) => choose(x, ind, ind, depth - 1))
    }

    private def brokenProduct(prefix: String, args: List[Any], indent: Int, depth: Int): String =
      brokenDelimited(s"$prefix(", ")", args, indent, depth, renderElem = (x, ind) => choose(x, ind, ind, depth - 1))

    private def brokenContainer(prefix: String, elems: List[Any], indent: Int, depth: Int): String =
      brokenDelimited(
        s"$prefix(",
        ")",
        elems,
        indent,
        depth,
        renderElem = (x, ind) =>
          x match {
            case Arrow(k, v) =>
              val left = choose(k, ind, ind, depth - 1)
              val right = choose(v, ind, ind + left.length + 4, depth - 1)
              s"$left -> $right"
            case other =>
              choose(other, ind, ind, depth - 1)
          }
      )

    private def brokenDelimited(
      open: String,
      close: String,
      elems0: List[Any],
      indent: Int,
      depth: Int,
      renderElem: (Any, Int) => String
    ): String = {
      val elems =
        if (elems0.length <= cfg.maxElements) elems0
        else elems0.take(cfg.maxElements) :+ s"… +${elems0.length - cfg.maxElements} more"

      val childIndent = indent + cfg.indentStep
      val rendered = elems.map(e => (" " * childIndent) + renderElem(e, childIndent))
      open + "\n" + rendered.mkString(",\n") + "\n" + (" " * indent) + close
    }

    private def stringPrefix(x: Any): String = x match {
      // Keep the one you explicitly care about
      case _: scala.collection.immutable.ArraySeq[?] => "ArraySeq"

      // Common Scala collection surface prefixes
      case _: List[?]                                => "List"
      case _: Vector[?]                              => "Vector"
      case _: scala.collection.immutable.LazyList[?] => "LazyList"

      case _: scala.collection.Map[?, ?]   => "Map"
      case _: scala.collection.Set[?]      => "Set"
      case _: scala.collection.Seq[?]      => "Seq"
      case _: scala.collection.Iterable[?] => "Iterable"

      // Fallback: readable-ish class name, normalized
      case other => normalizeCollectionClassName(className(other))
    }

    private def className(x: Any): String = {
      val n = x.getClass.getSimpleName
      if (n != null && n.nonEmpty) n else x.getClass.getName
    }

    private def normalizeCollectionClassName(n: String): String = {
      // Typical Scala impl names you’ll otherwise leak into output
      val base = n.stripSuffix("$")
      if (base.startsWith("Vector")) "Vector"
      else if (base.startsWith("Map")) "Map"
      else if (base.startsWith("Set")) "Set"
      else base.split('$').headOption.getOrElse(base) // e.g. ArraySeq$ofRef -> ArraySeq
    }

    private def isScalaTuple(p: Product): Boolean =
      p.getClass.getName.startsWith("scala.Tuple")
  }
}

protected case class Arrow(k: Any, v: Any)
