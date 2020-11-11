package kotlinx.atomicfu.test

import java.lang.reflect.Modifier
import kotlin.test.assertEquals

public open class ReflectionTestBase {
    fun checkDeclarations(javaClass: Class<*>, expect: List<FieldDesc>) =
            assertEquals(expect.joinToString(";"), getClassDeclarations(javaClass))

    fun checkClassModifiers(javaClass: Class<*>, modifiers: Int, isSynthetic: Boolean) {
        assertEquals(isSynthetic, javaClass.isSynthetic)
        assertEquals(Modifier.toString(modifiers), Modifier.toString(javaClass.modifiers))
    }

    private fun getClassDeclarations(javaClass: Class<*>) =
            javaClass.declaredFields.joinToString(separator = ";") {
                "${Modifier.toString(it.modifiers)} ${if (it.isSynthetic) "synthetic " else ""}${it.type.name} ${it.name}"
            }

    data class FieldDesc(val modifiers: Int, val isSynthetic: Boolean, val typeName: String, val name: String) {
        override fun toString() = "${Modifier.toString(modifiers)} ${if (isSynthetic) "synthetic " else ""}$typeName $name"
    }
}