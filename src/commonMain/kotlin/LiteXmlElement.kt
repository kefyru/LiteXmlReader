

import lib.CharSpan.Companion.getSpan
import lib.LiteXmlReader.Companion.getElementEnd
import lib.LiteXmlReader.Companion.getElementHasContent
import lib.LiteXmlReader.Companion.getElementStart
import lib.LiteXmlReader.Companion.getSpanEnd
import lib.LiteXmlReader.Companion.getSpanStart
import lib.LiteXmlReader.Companion.getType

class LiteXmlElement(
    private val src: String,
    private val entries: LongArray,
    private val index: Int
) : Iterable<LiteXmlElement> {

    companion object {
        val nullSpan = CharSpan("")
    }

    private var _type: LiteXmlElementType? = null
    val type: LiteXmlElementType
        get() {
            var r = _type
            if (r != null) return r
            var type = entries[index].getType()
            r = when (type) {
                LiteXmlElementType.tag -> LiteXmlElementType.Tag
                LiteXmlElementType.attr -> LiteXmlElementType.Attribute
                else -> LiteXmlElementType.Unknown
            }
            _type = r
            return r
        }

    private var _nameSpan: CharSpan? = null
    val nameSpan: CharSpan
        get() {
            var r = _nameSpan
            if (r != null) return r
            val name = entries[index + 1] // name always fallows element
            if (name.getType() != LiteXmlElementType.name) throw Error("Expected Name, got ${name.getType()}")
            r = src.getSpan(name.getSpanStart(), name.getSpanEnd())
            _nameSpan = r
            return r
        }

    private var _contentSpan: CharSpan? = null
    val contentSpan: CharSpan?
        get() {
            var r = _contentSpan
            if (r != null) {
                if (r === nullSpan) return null
                return r
            }
            val e = entries[index]
            if (!e.getElementHasContent()) {
                _contentSpan = nullSpan
                return null
            }
            val end = e.getElementEnd()
            val content = entries[end - 1]
            if (content.getType() != LiteXmlElementType.content)
                throw Error("Expected Content, got ${content.getType()}")

            r = src.getSpan(content.getSpanStart(), content.getSpanEnd())
            _contentSpan = r
            return r
        }

    val debug: List<LiteXmlElement> get() = this.toList()

    override fun iterator(): Iterator<LiteXmlElement> {
        val e = entries[index]
        val type = e.getType()
        if (type == LiteXmlElementType.tag) {
            if (e.getElementStart() + 1 >= e.getElementEnd()) return LiteXmlReader.EmptyIterator()
            return LiteXmlReader.TagIterator(src, entries, index)
        } else return LiteXmlReader.EmptyIterator()
    }

    override fun toString(): String {
        val type = entries[index].getType()
        if (type == LiteXmlElementType.attr) return "$nameSpan=\"$contentSpan\""
        else if (type == LiteXmlElementType.tag) {
            val cs = contentSpan
            return if (cs == null || cs.isEmpty()) "<$nameSpan>"
            else "<$nameSpan>$cs</>"
        } else return "Invalid element"
    }
}