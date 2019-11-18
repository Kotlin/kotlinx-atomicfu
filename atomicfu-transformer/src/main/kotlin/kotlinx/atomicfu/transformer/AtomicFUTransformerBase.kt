/*
 * Copyright 2017-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.atomicfu.transformer

import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList
import java.io.File
import org.slf4j.LoggerFactory

abstract class AtomicFUTransformerBase(
    var inputDir: File,
    var outputDir: File
) {
    protected operator fun File.div(child: String) =
        File(this, child)

    protected fun File.toOutputFile(): File =
        outputDir / relativeTo(inputDir).toString()

    private val logger = LoggerFactory.getLogger(this::class.java)

    protected fun File.mkdirsAndWrite(outBytes: ByteArray) {
        parentFile.mkdirs()
        writeBytes(outBytes) // write resulting bytes
    }

    protected fun File.isClassFile() = toString().endsWith(".class")

    var verbose = true
    protected var lastError: Throwable? = null
    protected var transformed = false

    data class SourceInfo(
        val method: MethodId,
        val source: String?,
        val i: AbstractInsnNode? = null,
        val insnList: InsnList? = null
    ) {
        override fun toString(): String = buildString {
            source?.let { append("$it:") }
            i?.line?.let { append("$it:") }
            append(" $method")
        }
    }

    private fun format(message: String, sourceInfo: SourceInfo? = null): String {
        var loc = if (sourceInfo == null) "" else sourceInfo.toString() + ": "
        if (verbose && sourceInfo != null && sourceInfo.i != null)
            loc += sourceInfo.i.atIndex(sourceInfo.insnList)
        return "$loc$message"
    }

    protected fun info(message: String, sourceInfo: SourceInfo? = null) {
        logger.info(format(message, sourceInfo))
    }

    protected fun debug(message: String, sourceInfo: SourceInfo? = null) {
        logger.debug(format(message, sourceInfo))
    }

    protected fun error(message: String, sourceInfo: SourceInfo? = null) {
        logger.error(format(message, sourceInfo))
        if (lastError == null) lastError = TransformerException(message)
    }

    abstract fun transform()
}

class TransformerException(message: String, cause: Throwable? = null) : Exception(message, cause)