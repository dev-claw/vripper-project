package me.vripper.utilities

data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: List<String> = emptyList(),
) : Comparable<SemanticVersion> {

    override fun compareTo(other: SemanticVersion): Int {
        major.compareTo(other.major).let { if (it != 0) return it }
        minor.compareTo(other.minor).let { if (it != 0) return it }
        patch.compareTo(other.patch).let { if (it != 0) return it }
        return comparePreRelease(preRelease, other.preRelease)
    }

    companion object {
        private val versionPattern =
            Regex("""^[vV]?(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$""")

        fun parse(value: String): SemanticVersion? {
            val match = versionPattern.matchEntire(value.trim()) ?: return null
            return SemanticVersion(
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].ifEmpty { "0" }.toInt(),
                patch = match.groupValues[3].ifEmpty { "0" }.toInt(),
                preRelease = match.groupValues[4].takeIf { it.isNotEmpty() }?.split('.') ?: emptyList(),
            )
        }

        private fun comparePreRelease(left: List<String>, right: List<String>): Int {
            if (left.isEmpty() && right.isEmpty()) return 0
            if (left.isEmpty()) return 1
            if (right.isEmpty()) return -1

            for (i in 0 until minOf(left.size, right.size)) {
                val a = left[i]
                val b = right[i]
                val aNumber = a.toIntOrNull()
                val bNumber = b.toIntOrNull()
                val comparison = when {
                    aNumber != null && bNumber != null -> aNumber.compareTo(bNumber)
                    aNumber != null -> -1
                    bNumber != null -> 1
                    else -> a.compareTo(b)
                }
                if (comparison != 0) return comparison
            }
            return left.size.compareTo(right.size)
        }
    }
}