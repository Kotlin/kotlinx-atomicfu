import kotlinx.atomicfu.examples.mpp_sample.*
import kotlin.test.*

fun doWorld()  {
    val sampleClass = AtomicSampleClass()
    sampleClass.doWork(1234)
    assertEquals(1234, sampleClass.x)
    assertEquals(42, sampleClass.synchronizedFoo(42))
}
