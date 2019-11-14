package kotlinx.atomicfu.test

private fun ByteArray.equalsAt(i: Int, bs: ByteArray): Boolean {
    if (i + bs.size >= size) return false
    for (k in bs.indices) {
        if (this[i + k] != bs[k]) return false
    }
    return true
}

fun ByteArray.findString(strings: List<String>): FindResult? {
    for (ss in strings) {
        val bs = ss.toByteArray()
        for (i in indices) {
            if (equalsAt(i, bs)) return FindResult(ss, i)
        }
    }
    return null
}

class FindResult(val string: String, val offset: Int) {
    override fun toString(): String = "Found string '$string' at offset 0x${offset.toString(16)}"
}
