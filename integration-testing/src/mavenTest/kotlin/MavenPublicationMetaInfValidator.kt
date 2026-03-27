/*
 * Copyright 2016-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

import java.util.jar.JarFile
import kotlin.test.Test
import kotlin.test.fail

class MavenPublicationMetaInfValidator {

    private val kotlinVersion = System.getProperty("kotlin.version.integration")

    @Test
    fun testMetaInfContents() {
        val clazz = Class.forName("kotlinx.atomicfu.AtomicFU")
        if (kotlinVersion.startsWith("2.4")) {
            // See KT-69701 Gradle: module name is passed inconsistently to different types of compilations
            JarFile(clazz.protectionDomain.codeSource.location.file).compareMetaInfContents(
                setOf(
                    "MANIFEST.MF",
                    "org.jetbrains.kotlinx_atomicfu.kotlin_module",
                    "versions/9/module-info.class"
                )
            )
        } else {
            JarFile(clazz.protectionDomain.codeSource.location.file).compareMetaInfContents(
                setOf(
                    "MANIFEST.MF",
                    "atomicfu.kotlin_module",
                    "versions/9/module-info.class"
                )
            )
        }
    }

    private fun JarFile.compareMetaInfContents(expected: Set<String>) {
        val actual = entries().toList()
                .filter { !it.isDirectory && it.realName.contains("META-INF")}
                .map { it.realName.substringAfter("META-INF/") }
                .toSet()
        if (actual != expected) {
            val intersection = actual.intersect(expected)
            fail("Mismatched files: " + (actual.subtract(intersection) + expected.subtract(intersection)))
        }
        close()
    }
}