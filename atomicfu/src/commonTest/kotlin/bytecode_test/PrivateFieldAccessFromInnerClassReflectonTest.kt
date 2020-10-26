package bytecode_test

import kotlinx.atomicfu.atomic

class PrivateFieldAccessFromInnerClassReflectonTest {
    private val state = atomic(0)

    inner class InnerClass {
        fun m() {
            state.compareAndSet(0, 77)
        }
    }
}