

import kotlin.experimental.and


class LiteXmlReader private constructor(source: String) {

    companion object {

        fun read(xml: String) = LiteXmlReader(xml).read()


        internal fun String.debug(start: Int, length: Int = 10): String {
            if (start > 0) {
                if (this.length > length) return "...[${substring(start, length)}]..."
                return "...[${drop(start)}]"
            }
            if (this.length > length) return "[${substring(start, length)}]..."
            return "[${drop(start)}]"
        }

        internal fun String.skipWhiteSpaces(start: Int): Int {
            var i = start
            while (this[i] <= ' ') i++
            return i
        }

        internal fun String.skipAnnotation(start: Int): Int {
            val i = this.indexOf("?>", start)
            if (i < 0) throw Error("'?>' not found: ${debug(start)}")
            return i + 2
        }

        internal fun String.skipComment(start: Int): Int {
            if (!this.startsWith("<!--", start)) throw Error("Unexpected token ${debug(start)}")
            val i = this.indexOf("-->")
            if (i < 0) throw Error("'-->' not found")
            return i + 3
        }

        internal fun String.startsWith(start: Int, cmpStart: Int, cmpEnd: Int): Boolean {
            val len = cmpEnd - cmpStart
            var i = 0
            while (i < len) {
                if (this[start + i] != this[cmpStart + i]) return false
                i++
            }
            return true
        }

        internal fun getSpanEntry(start: Int, end: Int, type: Byte): Long =
            ((start and 0x7fff_ffff).toLong() shl 33) or ((end and 0x7fff_ffff).toLong() shl 2) or (type and 0b11).toLong()

        internal fun getElementEntry(start: Int, end: Int, type: Byte, content: Boolean): Long =
            ((start and 0x3fff_ffff).toLong() shl 35) or ((end and 0x3fff_ffff).toLong() shl 4) or (if (content) 0b100 else 0) or (type and 0b11).toLong()


        internal fun Long.getType() = (this and 0b11).toByte()
        internal fun Long.getSpanStart() = ((this shr 33) and 0x7fff_ffff).toInt()
        internal fun Long.getSpanEnd() = ((this shr 2) and 0x7fff_ffff).toInt()
        internal fun Long.getElementStart() = ((this shr 35) and 0x3fff_ffff).toInt()
        internal fun Long.getElementEnd() = ((this shr 4) and 0x3fff_ffff).toInt()
        internal fun Long.getElementHasContent() = (this and 0b100) != 0L
    }

    private val src: String = source

    private fun read(): LiteXmlElement {
        val s = src

        val len = s.length
        var i = 0;

        while (i < len) {
            i = s.skipWhiteSpaces(i)
            if (s[i] != '<') throw Error("Expected '<' ${s.debug(i)}")
            i = when (s[i + 1]) {
                '!' -> s.skipComment(i)
                '?' -> s.skipAnnotation(i)
                else -> {
                    if (runningIndex != 0) throw Error("Xml must contain one root tag. ${s.debug(i)}")
                    readTag(s, i);
                }
            }
        }

        return LiteXmlElement(s, list, 0)
    }

    private var capacity = 256
    private var list = LongArray(256)
    private var runningIndex: Int = 0


    /**
     * Set entry
     */
    private fun setElementEntry(index: Int, start: Int, end: Int, type: Byte, hasContent: Boolean) {
        if (index >= capacity) {
            capacity = (capacity * 2).coerceAtLeast(index + 1)
            val newList = LongArray(capacity)
            list.copyInto(newList)
            list = newList
        }
        list[index] = getElementEntry(start, end, type, hasContent)
    }

    /**
     * Add entry
     */
    private fun addSpanEntry(start: Int, end: Int, type: Byte) {
        val index = runningIndex++
        if (index >= capacity) {
            val newCapacity = (capacity * 2).coerceAtLeast(index + 1)
            val newList = LongArray(newCapacity)
            list.copyInto(newList)
            list = newList
        }
        list[index] = getSpanEntry(start, end, type)
    }

    /**
     * Read tag
     */
    private fun readTag(s: String, start: Int): Int {
        val tagIndex = runningIndex++
        val nameStart = start + 1
        var i = nameStart
        val len = s.length

        while (true) {
            val c = s[i]
            i++
            if (c > '>') continue
            if (c <= ' ' || c == '/' || c == '>') {
                i--
                break
            }
            // does not check if name is correct
        }
        addSpanEntry(nameStart, i, LiteXmlElementType.name)
        val nameEnd = i

        val mark = runningIndex

        while (i < len) {
            while (s[i] <= ' ') i++

            when (s[i]) {
                '/' -> {
                    if (s[i + 1] != '>') throw Error("Expected '/>' ${s.debug(i)}")
                    setElementEntry(tagIndex, mark, runningIndex, LiteXmlElementType.tag, false)
                    return i + 2
                }
                '>' -> {
                    val contentMark = runningIndex
                    i = readTagContent(s, i + 1)
                    if (s[i] != '<' || s[i + 1] != '/') throw Error("Expected '</' ${s.debug(i)}")
                    i += 2
                    while (s[i] <= ' ') i++
                    if (!s.startsWith(i, nameStart, nameEnd))
                        throw Error("Expected '</${s.substring(nameStart, nameEnd)}>' ...</${s.debug(i)}")
                    i += nameEnd - nameStart
                    while (s[i] <= ' ') i++
                    if (s[i] != '>') throw Error("Expected '>' ${s.debug(i)}")
                    val hasTextContent = runningIndex - contentMark == 1
                    setElementEntry(tagIndex, mark, runningIndex, LiteXmlElementType.tag, hasTextContent)
                    i++
                    return i
                }
                else -> {
                    i = readAttribute(s, i)
                }
            }
        }

        throw Error("Unexpected end of xml")
    }

    /**
     * Read tag content
     */
    private fun readTagContent(s: String, start: Int): Int {
        var i = start

        while (s[i] <= ' ') i++

        if (s[i] != '<') {
            i = s.indexOf('<', i)
            if (i < 0) throw Error("Tag close not found ${s.debug(start)}")
            addSpanEntry(start, i, LiteXmlElementType.content)
            return i
        }

        val len = s.length

        while (i < len) {
            if (s[i + 1] == '/') return i
            i = readTag(s, i)
            while (s[i] <= ' ') i++
        }

        throw Error("Unexpected end of xml")
    }

    /**
     * Read next attribute
     */
    private fun readAttribute(s: String, start: Int): Int {
        val attrIndex = runningIndex++
        var i = start
        i = s.indexOf('=', i)
        if (i < 0) throw Error("Cant find '=' ${s.debug(start)}")
        var mark = i
        i--
        while (s[i] <= ' ') i--
        i++
        addSpanEntry(start, i, LiteXmlElementType.name)

        i = mark + 1

        while (s[i] <= ' ') i++
        if (s[i] != '"') throw Error("Expected '\"' ${s.debug(i)}")
        i++
        mark = i

        i = s.indexOf('"', i)
        if (i < 0) throw Error("Cant find close [\"] ${s.debug(mark)}")
        addSpanEntry(mark, i, LiteXmlElementType.content)
        setElementEntry(attrIndex, runningIndex - 1, runningIndex, LiteXmlElementType.attr, true)
        return i + 1
    }

    internal class EmptyIterator : Iterator<LiteXmlElement> {
        override fun hasNext(): Boolean = false
        override fun next(): LiteXmlElement = throw Error("Nothing to iterate")
    }

    internal class TagIterator : Iterator<LiteXmlElement> {
        private val entries: LongArray
        private val end: Int
        private var running: Int
        private val src: String

        constructor(source: String, entries: LongArray, index: Int) {
            src = source
            this.entries = entries
            val e = entries[index]
            running = e.getElementStart()
            end = if (e.getElementHasContent()) e.getElementEnd() - 1 // content always last
            else e.getElementEnd()

            if (end - running == 1) {
                // means text content, nothing to iterate
                running++
            }
        }

        override fun hasNext(): Boolean {
            return running < end
        }

        override fun next(): LiteXmlElement {
            val i = running
            val e = entries[i]
            running = e.getElementEnd()
            val type = e.getType()
            if (type == LiteXmlElementType.tag || type == LiteXmlElementType.attr) return LiteXmlElement(
                src,
                entries,
                i
            )
            else throw Error("Unexpected xml type $type")
        }
    }
}


