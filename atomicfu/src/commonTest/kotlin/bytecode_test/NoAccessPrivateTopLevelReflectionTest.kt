@file:JvmName("NoAccessPrivateTopLevel")

package bytecode_test

import kotlinx.atomicfu.*
import kotlin.jvm.JvmName

private val a = atomic(1) // no accessors