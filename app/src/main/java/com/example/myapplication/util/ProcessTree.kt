package com.example.myapplication.util

import java.io.File

object ProcessTree {

    const val SIGTERM = 15
    const val SIGKILL = 9

    fun pidOf(process: Process): Int? = runCatching {
        process.javaClass.getDeclaredField("pid").apply { isAccessible = true }.getInt(process)
    }.getOrNull()

    fun withDescendants(rootPid: Int): List<Int> {
        val children = mutableMapOf<Int, MutableList<Int>>()
        File("/proc").listFiles().orEmpty().forEach { dir ->
            val pid = dir.name.toIntOrNull() ?: return@forEach
            val stat = runCatching { File(dir, "stat").readText() }.getOrNull() ?: return@forEach
            val ppid = stat.substringAfterLast(") ").split(' ').getOrNull(1)?.toIntOrNull() ?: return@forEach
            children.getOrPut(ppid) { mutableListOf() }.add(pid)
        }
        val result = mutableListOf<Int>()
        val queue = ArrayDeque(listOf(rootPid))
        while (queue.isNotEmpty()) {
            val pid = queue.removeFirst()
            result += pid
            children[pid]?.let(queue::addAll)
        }
        return result
    }

    fun signal(pids: List<Int>, signal: Int) {
        pids.forEach { pid -> runCatching { android.os.Process.sendSignal(pid, signal) } }
    }
}
