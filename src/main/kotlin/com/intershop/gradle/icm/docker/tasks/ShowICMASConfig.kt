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
package com.intershop.gradle.icm.docker.tasks

import com.intershop.gradle.icm.docker.utils.Configuration
import com.intershop.gradle.icm.docker.utils.IPFinder
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.net.UnknownHostException

open class ShowICMASConfig : DefaultTask() {

    @TaskAction
    fun showconfig() {
        val systemIP = IPFinder.getSystemIP()

        val hostname = if(systemIP.second != null) {
            try {
                systemIP.second
            } catch (e: UnknownHostException) {
                e.printStackTrace()
            }
        } else {
            "localhost"
        }

        println(
            """
            ==============================================================
            check your icm.properties file
            --------------------------------------------------------------
            # webserver configuration
            # if youn want change the ports of the webserver, it is necessary to change the ports 
            # in ${GenICMProperties.webserverUrlProp} and ${GenICMProperties.webserverSecureUrlProp} 
            # according to the settings ${Configuration.WS_HTTP_PORT} and ${Configuration.WS_HTTPS_PORT}
            #
            ${GenICMProperties.webserverUrlProp} = http://$hostname:${Configuration.WS_HTTP_PORT_VALUE}
            ${GenICMProperties.webserverSecureUrlProp} = https://$hostname:${Configuration.WS_HTTPS_PORT_VALUE}
                
            # port number to start the servlet engine
            ${Configuration.AS_CONNECTOR_PORT} = ${Configuration.AS_CONNECTOR_PORT_VALUE}
            
            # Host name / IP of the ICM Server (local installation)
            # both values must match    
            ${Configuration.LOCAL_CONNECTOR_HOST} = ${systemIP.first}
            # WebAdapapter container configuration
            ${GenICMProperties.asConnectorAdressProp} = ${systemIP.first}
            ==============================================================
            """.trimIndent())
    }
}
