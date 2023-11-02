/*
 * Copyright 2016-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.gradle.plugin.test.cases.smoke

import kotlinx.atomicfu.gradle.plugin.test.framework.checker.ArtifactChecker
import java.io.File
import java.nio.file.Files
import kotlin.test.*
import kotlin.text.*

class ArtifactCheckerSmokeTest {
    val tempDir = Files.createTempDirectory("sample").toFile()
    
    private class MyArtifactChecker(tempDir: File) : ArtifactChecker(tempDir) {
        private val atomicfuString = "public final void doWork(int);\n" +
                "    descriptor: (I)V\n" +
                "    flags: (0x0011) ACC_PUBLIC, ACC_FINAL\n" +
                "    Code:\n" +
                "      stack=3, locals=2, args_size=2\n" +
                "         0: aload_0\n" +
                "         1: getfield      #18                 // Field _x:Lkotlinx/atomicfu/AtomicInt;\n" +
                "         4: iconst_0\n" +
                "         5: sipush        556\n" +
                "         8: invokevirtual #28                 // Method kotlinx/atomicfu/AtomicInt.compareAndSet:(II)Z\n" +
                "        11: pop\n" +
                "        12: return\n" +
                "      LineNumberTable:\n" +
                "        line 14: 0\n" +
                "        line 19: 12\n" +
                "      LocalVariableTable:\n" +
                "        Start  Length  Slot  Name   Signature\n" +
                "            0      13     0  this   LIntArithmetic;\n" +
                "            0      13     1 finalValue   I"
        
        
        val noAtomicfuString = "  public final void doWork(int);\n" +
                "    descriptor: (I)V\n" +
                "    flags: (0x0011) ACC_PUBLIC, ACC_FINAL\n" +
                "    Code:\n" +
                "      stack=4, locals=2, args_size=2\n" +
                "         0: aload_0\n" +
                "         1: getstatic     #22                 // Field _x\$FU:Ljava/util/concurrent/atomic/AtomicIntegerFieldUpdater;\n" +
                "         4: swap\n" +
                "         5: iconst_0\n" +
                "         6: sipush        556\n" +
                "         9: invokevirtual #28                 // Method java/util/concurrent/atomic/AtomicIntegerFieldUpdater.compareAndSet:(Ljava/lang/Object;II)Z\n" +
                "        12: pop\n" +
                "        13: return\n" +
                "      LineNumberTable:\n" +
                "        line 14: 0\n" +
                "        line 19: 13\n" +
                "      LocalVariableTable:\n" +
                "        Start  Length  Slot  Name   Signature\n" +
                "            0      14     0  this   LIntArithmetic;\n" +
                "            0      14     1 finalValue   I"
        
        val metadataString = "RuntimeVisibleAnnotations:\n" +
                "  0: #32(#33=[I#34,I#35,I#36],#37=I#34,#38=I#39,#40=[s#41],#42=[s#20,s#43,s#6,s#15,s#16,s#21,s#43,s#29,s#43,s#44])\n" +
                "    kotlin.Metadata(\n" +
                "      mv=[1,9,0]\n" +
                "      k=1\n" +
                "      xi=48\n" +
                "      d1=[\"\\u0000\\u001e\\n\\u0002\\u0018\\u0002\\n\\u0002\\u0010\\u0000\\n\\u0002\\b\\u0002\\n\\u0002\\u0018\\u0002\\n\\u0000\\n\\u0002\\u0010\\u0002\\n\\u0000\\n\\u0002\\u0010\\b\\n\\u0000\\u0018\\u00002\\u00020\\u0001B\\u0005¢\\u0006\\u0002\\u0010\\u0002J\\u000e\\u0010\\u0005\\u001a\\u00020\\u00062\\u0006\\u0010\\u0007\\u001a\\u00020\\bR\\u000e\\u0010\\u0003\\u001a\\u00020\\u0004X\\u0082\\u0004¢\\u0006\\u0002\\n\\u0000¨\\u0006\\t\"]\n" +
                "      d2=[\"LIntArithmetic;\",\"\",\"()V\",\"_x\",\"Lkotlinx/atomicfu/AtomicInt;\",\"doWork\",\"\",\"finalValue\",\"\",\"jvm-sample\"]\n" +
                "    )"
        
        override fun checkReferences() {
            assertTrue(atomicfuString.toByteArray().findAtomicfuRef())
            assertFalse(noAtomicfuString.toByteArray().findAtomicfuRef())
            assertTrue(metadataString.toByteArray().findAtomicfuRef())
        }
    }
    
    private val checker = MyArtifactChecker(tempDir)
    
    @Test
    fun testAtomicfuReferenciesLookup() {
        checker.checkReferences()
    }
}
