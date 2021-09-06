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

    //Function to Find out IP Address
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
                    val nets = NetworkInterface.getNetworkInterfaces()
                    var pair: Pair<String?,String?> = Pair(null, null)
                    nets.toList().map { it.name }.sorted().forEach {
                        var ip = getSystemIP4Linux(it)
                        if(ip != null) {
                            println(it + "---" + ip)
                            pair = Pair(ip?.hostAddress, ip?.canonicalHostName)
                            return@forEach
                        }
                    }
                    pair
                }
            }
            sysIPHostName
        } catch (E: Exception) {
            System.err.println("System IP Exp : " + E.message)
            Pair(null,null)
        }
    }

    //For Linux OS
    private fun getSystemIP4Linux(name: String): InetAddress? {
        return try {
            var ip: InetAddress? = null
            if(name.substring(0, 2) in  listOf("en", "wl") || name.substring(0, 3) in  listOf("eth", "usb")) {
                val networkInterface = NetworkInterface.getByName(name)
                val inetAddress = networkInterface.inetAddresses
                var currentAddress = inetAddress.nextElement()

                if (!inetAddress.hasMoreElements()) {
                    if (currentAddress is Inet4Address && !currentAddress.isLoopbackAddress()) {
                        ip = currentAddress
                    }
                } else {
                    while (inetAddress.hasMoreElements()) {
                        currentAddress = inetAddress.nextElement()
                        if (currentAddress is Inet4Address && !currentAddress.isLoopbackAddress()) {
                            ip = currentAddress
                            break
                        }
                    }
                }
                if (ip == null) {
                    ip = InetAddress.getLocalHost()
                }
            }
            ip
        } catch (E: Exception) {
            System.err.println("System Linux IP Exp : " + E.message)
            null
        }
    }
}
