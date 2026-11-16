package com.example.myapplication.util

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

object TextSniffer {

    private val allowedControls = setOf(0x08, 0x09, 0x0A, 0x0C, 0x0D, 0x1B)

    fun isText(sample: ByteArray): Boolean {
        if (sample.isEmpty()) return true
        var controls = 0
        for (byte in sample) {
            val b = byte.toInt() and 0xFF
            if (b == 0) return false
            if (b < 0x20 && b !in allowedControls) controls++
        }
        if (controls * 20 > sample.size) return false
        return isValidUtf8(sample)
    }

    private fun isValidUtf8(sample: ByteArray): Boolean {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val result = decoder.decode(ByteBuffer.wrap(sample), CharBuffer.allocate(sample.size), false)
        return !result.isError
    }
}
