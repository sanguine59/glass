package com.example.myapplication.data.runtime

import java.io.File

class ShebangFixer {

    fun fix(project: File, node: File): Int {
        val bin = File(project, "node_modules/.bin")
        if (!bin.isDirectory) return 0
        val replacement = "#!${node.absolutePath}".toByteArray()
        return bin.listFiles().orEmpty()
            .mapNotNull { runCatching { it.canonicalFile }.getOrNull() }
            .distinct()
            .count { it.isFile && rewrite(it, replacement) }
    }

    private fun rewrite(file: File, replacement: ByteArray): Boolean = runCatching {
        if (!startsWithEnvNode(file)) return@runCatching false
        val bytes = file.readBytes()
        file.writeBytes(replacement + bytes.copyOfRange(ENV_NODE.size, bytes.size))
        true
    }.getOrDefault(false)

    private fun startsWithEnvNode(file: File): Boolean {
        val head = ByteArray(ENV_NODE.size)
        val read = file.inputStream().use { it.read(head) }
        return read == ENV_NODE.size && head.contentEquals(ENV_NODE)
    }

    private companion object {
        val ENV_NODE = "#!/usr/bin/env node".toByteArray()
    }
}
