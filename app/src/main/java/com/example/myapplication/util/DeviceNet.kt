package com.example.myapplication.util

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface

object DeviceNet {

    data class Address(val iface: String, val ip: String, val isIPv4: Boolean)

    fun addresses(): List<Address> = runCatching {
        NetworkInterface.getNetworkInterfaces()
            ?.toList()
            .orEmpty()
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .flatMap { iface ->
                iface.inetAddresses.toList()
                    .filterNot { it.isLoopbackAddress || it.isLinkLocalAddress }
                    .map { Address(iface.name, it.presentation(), it is Inet4Address) }
            }
            .sortedByDescending { it.isIPv4 }
    }.getOrDefault(emptyList())

    fun primary(): String? = addresses().firstOrNull()?.ip

    private fun InetAddress.presentation(): String = when (this) {
        is Inet6Address -> hostAddress?.substringBefore('%').orEmpty()
        else -> hostAddress.orEmpty()
    }

    fun diagnostics(): String = buildString {
        val addrs = addresses()
        if (addrs.isEmpty()) {
            appendLine("ip         none (no network?)")
            return@buildString
        }
        addrs.forEach { appendLine("ip         ${it.iface}  ${it.ip}") }
    }
}
