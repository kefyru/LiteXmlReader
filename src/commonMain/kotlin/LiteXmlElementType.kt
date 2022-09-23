
enum class LiteXmlElementType {
    Unknown, Attribute, Tag;

    companion object {
        const val name: Byte = 0
        const val attr: Byte = 1
        const val tag: Byte = 2
        const val content: Byte = 3
    }
}