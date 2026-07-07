package me.vripper.model

data class ImageChunk(
    val missing: Boolean,
    val imageId: Long,
    val offset: Long,
    val data: ByteArray,
    val isLast: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImageChunk

        if (missing != other.missing) return false
        if (imageId != other.imageId) return false
        if (offset != other.offset) return false
        if (isLast != other.isLast) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = missing.hashCode()
        result = 31 * result + imageId.hashCode()
        result = 31 * result + offset.hashCode()
        result = 31 * result + isLast.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}
