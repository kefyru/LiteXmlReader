

@Suppress("ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty")
class CharSpan : CharSequence {

    companion object {
        fun CharSequence.getSpan(start: Int, end: Int): CharSpan {
            return CharSpan(this, start, end - start)
        }
    }

    private val source: CharSequence
    private val offset: Int
    override val length: Int

    constructor(source: CharSequence, offset: Int, length: Int) {
        if (offset + length > source.length) throw IndexOutOfBoundsException()

        if (source is CharSpan) {
            this.source = source.source
            this.offset = source.offset + offset
            this.length = length
        } else {
            this.source = source
            this.offset = offset
            this.length = length
        }
    }

    private constructor(source: CharSpan, offset: Int) {
        if (offset > source.length) throw IndexOutOfBoundsException()
        this.source = source.source
        this.offset = source.offset + offset
        this.length = source.length - offset
    }

    private constructor(source: CharSpan, offset: Int, length: Int) {
        if (offset + length > source.length) throw IndexOutOfBoundsException()
        this.source = source.source
        this.offset = source.offset + offset
        this.length = length
    }

    constructor(source: CharSequence) {
        if (source is CharSpan) {
            this.source = source.source
            this.offset = source.offset
            this.length = source.length
        } else {
            this.source = source
            this.offset = 0
            this.length = source.length
        }
    }

    override fun get(index: Int): Char = source[offset + index]

    fun drop(count: Int): CharSpan = CharSpan(this, count)

    fun debug(max: Int = 10): String {
        if (max < length) return " ${this.subSequence(0, this.length.coerceAtMost(max))}..."
        return this.toString()
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        if (startIndex > endIndex || endIndex > length) throw IndexOutOfBoundsException()
        return CharSpan(source, offset + startIndex, endIndex - startIndex)
    }

    override fun toString(): String = StringBuilder(this).toString()

    fun skipWhiteSpaces(start: Int): Int {
        var i = start
        var l = length
        while (i < l) {
            if (this[i] > ' ') break
            i++
        }
        return i
    }

    fun left(length: Int) = CharSpan(source, offset, length)

    fun slice(offset: Int) = CharSpan(this, offset)

    fun subSpan(start: Int, end: Int): CharSpan = CharSpan(this, offset + start, end - start)

    fun slice(offset: Int, length: Int) = CharSpan(this, offset, length)

    fun isEmpty() = length == 0

    fun isNotEmpty() = length != 0
}