val debugTrace: DebugTrace? get() = null

inline fun <R> withClue(clue: Any?, thunk: () -> R): R {
    println("clue: $clue")
    return thunk()
}

fun main() {
    withClue(debugTrace ?: "dcdc") {
        println("dvdvd")
    }
}
