package kotlinx.atomicfu.test

import kotlin.test.Test
import java.lang.reflect.Modifier.*

private const val REF_VOLATILE = "RefVolatile"
private const val BYTECODE_PACKAGE = "bytecode_test"
private const val AFU_TYPE = "java.util.concurrent.atomic.AtomicIntegerFieldUpdater"

/**
 * Checks access modifiers, name and type for generated declarations.
 */
class TopLevelGeneratedDeclarationsReflectionTest : ReflectionTestBase() {

    /**
     * Test [bytecode_test.NoAccessPrivateTopLevel]
     */
    @Test
    fun testNoAccessPrivateTopLevel() {
        val javaClass = Class.forName("$BYTECODE_PACKAGE.NoAccessPrivateTopLevel")
        checkDeclarations(javaClass, listOf(
                FieldDesc(PRIVATE or STATIC or FINAL, true, "$BYTECODE_PACKAGE.NoAccessPrivateTopLevel\$A$REF_VOLATILE", "noAccessPrivateTopLevel\$A$REF_VOLATILE")
            )
        )
        val refVolatileClass = Class.forName("$BYTECODE_PACKAGE.NoAccessPrivateTopLevel\$A$REF_VOLATILE")
        checkClassModifiers(refVolatileClass, 0, true)
        checkDeclarations(refVolatileClass, listOf(
                FieldDesc(VOLATILE, false, "int", "a")
            )
        )
    }

    /**
     * Test [bytecode_test.PrivateTopLevel]
     */
    @Test
    fun testPrivateTopLevel() {
        val javaClass = Class.forName("$BYTECODE_PACKAGE.PrivateTopLevel")
        checkDeclarations(javaClass, listOf(
                FieldDesc(STATIC or FINAL, true, "$BYTECODE_PACKAGE.PrivateTopLevel\$B$REF_VOLATILE", "privateTopLevel\$B$REF_VOLATILE"),
                FieldDesc(STATIC or FINAL, true, AFU_TYPE, "b\$FU")
            )
        )
        val refVolatileClass = Class.forName("$BYTECODE_PACKAGE.PrivateTopLevel\$B$REF_VOLATILE")
        checkClassModifiers(refVolatileClass, 0, true)
        checkDeclarations(refVolatileClass, listOf(
                FieldDesc(VOLATILE, false, "int", "b")
            )
        )
    }

    /**
     * Test [bytecode_test.PublicTopLevel]
     */
    @Test
    fun testPublicTopLevelReflectionTest() {
        val javaClass = Class.forName("$BYTECODE_PACKAGE.PublicTopLevel")
        checkDeclarations(javaClass, listOf(
                FieldDesc(PUBLIC or STATIC or FINAL, true, "$BYTECODE_PACKAGE.PublicTopLevel\$C$REF_VOLATILE", "publicTopLevel\$C$REF_VOLATILE"),
                FieldDesc(PUBLIC or STATIC or FINAL, true, AFU_TYPE, "c\$FU\$internal")
            )
        )
        val refVolatileClass = Class.forName("$BYTECODE_PACKAGE.PublicTopLevel\$C$REF_VOLATILE")
        checkClassModifiers(refVolatileClass, PUBLIC, true)
        checkDeclarations(refVolatileClass, listOf(
                FieldDesc(PUBLIC or VOLATILE, false, "int", "c\$internal")
            )
        )
    }

    /**
     * Test [bytecode_test.PackagePrivateTopLevel]
     */
    @Test
    fun testPackagePrivateTopLevelReflectionTest() {
        val javaClass = Class.forName("$BYTECODE_PACKAGE.PackagePrivateTopLevel")
        checkDeclarations(javaClass, listOf(
                FieldDesc(STATIC or FINAL, true, "$BYTECODE_PACKAGE.PackagePrivateTopLevel\$D$REF_VOLATILE", "packagePrivateTopLevel\$D$REF_VOLATILE"),
                FieldDesc(STATIC or FINAL, true, AFU_TYPE, "d\$FU")
        )
        )
        val refVolatileClass = Class.forName("$BYTECODE_PACKAGE.PackagePrivateTopLevel\$D$REF_VOLATILE")
        checkClassModifiers(refVolatileClass, 0, true)
        checkDeclarations(refVolatileClass, listOf(
                FieldDesc(VOLATILE, false, "int", "d")
            )
        )
    }
}