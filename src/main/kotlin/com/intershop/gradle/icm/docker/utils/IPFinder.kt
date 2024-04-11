/*
 * Copyright 2020 Intershop Communications AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.intershop.gradle.icm.docker.utils

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface


object IPFinder {

    // Function to Find out IP Address
    fun getSystemIP(): Pair<String?,String?> {
        return try {
            val sysIPHostName:  Pair<String?,String?>
            val osName = System.getProperty("os.name")
            sysIPHostName = when {
                osName.contains("Windows") -> {
                    val ip = InetAddress.getLocalHost()
                    Pair(ip.hostAddress, ip.canonicalHostName)
                }
                else -> {
                    val ip: InetAddress? = getSystemIP4Linux()
                    when(ip) {
                        is InetAddress -> Pair(ip.hostAddress, ip.canonicalHostName)
                        else -> Pair(null, null)
                    }
                }
            }
            sysIPHostName
        } catch (e: Exception) {
            System.err.println("System IP Detection Exception: " + e.message)
            Pair(null, null)
        }
    }

    // For Linux OS
    private fun getSystemIP4Linux(): InetAddress? {
        return try {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { networkInterface ->
                    networkInterface.isUp && !networkInterface.isLoopback && !networkInterface.isVirtual
                }.filter { networkInterface ->
                    networkInterface.name.substring(0, 2) in listOf("en", "wl") ||
                        networkInterface.name.substring(0, 3) in listOf("eth", "usb")
                }.flatMap { networkInterface ->
                    networkInterface.inetAddresses.asSequence()
                }.filter { inetAddress ->
                    !inetAddress.isLoopbackAddress && inetAddress is Inet4Address
                }.elementAtOrElse(0) {
                    // Fallback to local host if sequence is empty
                    InetAddress.getLocalHost()
                }
        } catch (e: Exception) {
            System.err.println("Linux System IP Detection Exception: " + e.message)
            null
        }
    }
}
