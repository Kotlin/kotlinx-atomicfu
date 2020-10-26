@file:JvmName("PackagePrivateTopLevel")

package bytecode_test

import kotlinx.atomicfu.*
import kotlin.jvm.JvmName

internal val d = atomic(0) // accessed from the same package PublicTopLevel.class