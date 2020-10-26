package kotlinx.atomicfu.test

import org.junit.Test
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*
import kotlin.test.*

/**
 * Make sure metadata is intact after transformation.
 */
class MetadataReflectionTest {
    @Test
    fun testReflection() {
        val f =
            Class.forName("kotlinx.atomicfu.test.LockTestKt")
                .methods
                .single { it.name == "reflectionTest" }
                .kotlinFunction ?: error("Kotlin function is not found")
        assertEquals(2, f.typeParameters.size)
        val tp0 = f.typeParameters[0]
        assertEquals("AA", tp0.name)
        val tp1 = f.typeParameters[1]
        assertEquals("BB", tp1.name)
        val r = f.extensionReceiverParameter
            ?: error("extensionReceiverParameter not found")
        assertEquals("kotlin.String", r.type.toString())
        assertEquals(2, f.parameters.size)
        val p = f.parameters[1]
        assertEquals("mapParam", p.name)
        assertEquals("class kotlin.collections.Map", p.type.classifier.toString())
        assertEquals(2, p.type.arguments.size)
        val ta0 = p.type.arguments[0]
        assertEquals(KVariance.IN, ta0.variance)
        val ta0c = ta0.type!!.classifier as KTypeParameter
        assertEquals("AA", ta0c.name)
    }
}