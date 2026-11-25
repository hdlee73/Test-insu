package io.github.hdlee73.insu.domain

/** "v1.2.3", "1.2", "0.1.0-beta" 같은 버전 문자열 비교. 숫자 부분만 본다. */
object VersionCompare {
    fun parse(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
            .split('.').map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    /** a가 b보다 새 버전이면 양수. */
    fun compare(a: String, b: String): Int {
        val x = parse(a)
        val y = parse(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d
        }
        return 0
    }
}
