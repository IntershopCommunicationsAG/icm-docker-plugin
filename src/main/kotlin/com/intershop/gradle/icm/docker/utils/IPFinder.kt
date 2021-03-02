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
    fun getSystemIP(): String? {
        return try {
            var sysIP: String?
            val osName = System.getProperty("os.name")
            if (osName.contains("Windows")) {
                sysIP = InetAddress.getLocalHost().hostAddress
            } else {
                sysIP = getSystemIP4Linux("eth0")
                if (sysIP == null) {
                    sysIP = getSystemIP4Linux("eth1")
                    if (sysIP == null) {
                        sysIP = getSystemIP4Linux("eth2")
                        if (sysIP == null) {
                            sysIP = getSystemIP4Linux("usb0")
                        }
                    }
                }
            }
            sysIP
        } catch (E: Exception) {
            System.err.println("System IP Exp : " + E.message)
            null
        }
    }

    //For Linux OS
    private fun getSystemIP4Linux(name: String): String? {
        return try {
            var ip : String?=null
            val networkInterface = NetworkInterface.getByName(name)
            val inetAddress = networkInterface.inetAddresses
            var currentAddress = inetAddress.nextElement()
            while (inetAddress.hasMoreElements()) {
                currentAddress = inetAddress.nextElement()
                if (currentAddress is Inet4Address && !currentAddress.isLoopbackAddress()) {
                    ip = currentAddress.toString()
                    break
                }
            }
            if (ip != null) {
                if (ip.startsWith("/")) {
                    ip = ip.substring(1)
                }
            }
            ip
        } catch (E: Exception) {
            System.err.println("System Linux IP Exp : " + E.message)
            null
        }
    }
}
