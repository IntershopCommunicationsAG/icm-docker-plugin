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

package com.intershop.gradle.icm.docker.utils.webserver

import com.intershop.gradle.icm.docker.tasks.PrepareNetwork
import com.intershop.gradle.icm.docker.tasks.utils.ContainerEnvironment
import com.intershop.gradle.icm.docker.utils.AbstractTaskPreparer
import com.intershop.gradle.icm.docker.utils.Configuration
import com.intershop.gradle.icm.docker.utils.Configuration.WA_USE_HOST_USER
import com.intershop.gradle.icm.docker.utils.Configuration.WS_READINESS_PROBE_INTERVAL
import com.intershop.gradle.icm.docker.utils.Configuration.WS_READINESS_PROBE_INTERVAL_VALUE
import com.intershop.gradle.icm.docker.utils.Configuration.WS_READINESS_PROBE_TIMEOUT
import com.intershop.gradle.icm.docker.utils.Configuration.WS_READINESS_PROBE_TIMEOUT_VALUE
import com.intershop.gradle.icm.docker.utils.appsrv.ASTaskPreparer
import org.gradle.api.Project
import org.gradle.api.provider.Provider

class WATaskPreparer(
        project: Project,
        networkTask: Provider<PrepareNetwork>,
        volumes: Map<String, String>,
) : AbstractTaskPreparer(project, networkTask) {

    companion object {
        const val extName: String = "WA"
    }

    override fun getExtensionName(): String = extName
    override fun getImage(): Provider<String> = dockerExtension.images.webadapter
    override fun getUseHostUserConfigProperty(): String = WA_USE_HOST_USER
    override fun getTaskGroupExt(): String = "webserver"

    init {
        initBaseTasks()

        val httpPortMapping = dockerExtension.developmentConfig.getPortMapping(
                "http",
                Configuration.WS_HTTP_PORT,
                Configuration.WS_HTTP_PORT_VALUE,
                Configuration.WS_CONTAINER_HTTP_PORT,
                Configuration.WS_CONTAINER_HTTP_PORT_VALUE,
        )
        val httpsPortMapping = dockerExtension.developmentConfig.getPortMapping(
                "https",
                Configuration.WS_HTTPS_PORT,
                Configuration.WS_HTTPS_PORT_VALUE,
                Configuration.WS_CONTAINER_HTTPS_PORT,
                Configuration.WS_CONTAINER_HTTPS_PORT_VALUE,
                true
        )

        val env = with(dockerExtension.developmentConfig) {
            val env = ContainerEnvironment()

            val serverCertName = getConfigProperty(Configuration.WS_SERVER_CERT, "")
            if (serverCertName.isNotBlank()) {
                env.add("ICM_SERVERCERT", serverCertName)
            }

            val privateKeyName = getConfigProperty(Configuration.WS_SERVER_PRIVAT, "")
            if (privateKeyName.isNotBlank()) {
                env.add("ICM_SERVERPRIVATEKEY", privateKeyName)
            }

            val usehttp2 = getConfigProperty(Configuration.WS_SERVER_HTTP2, "false")
            if (usehttp2 == "true") {
                env.add("USEHTTP2", "true")
            }

            val servletUrlProvider = project.provider {
                val portMapping = asPortConfiguration.managementConnector.get()

                val host: String
                val port: Int
                if (appserverAsContainer) {
                    // started as container
                    host = "${dockerExtension.containerPrefix}-${ASTaskPreparer.extName.lowercase()}"
                    port = portMapping.containerPort
                } else {

                    // started externally
                    host = getConfigProperty(
                            Configuration.LOCAL_CONNECTOR_HOST,
                            Configuration.LOCAL_CONNECTOR_HOST_VALUE
                    )
                    port = portMapping.hostPort
                }
                return@provider "cs.url.0=http://$host:$port/servlet/ConfigurationServlet"
            }

            env.add("ICM_ICMSERVLETURLS", servletUrlProvider)
            return@with env
        }

        val createTask = registerCreateContainerTask(findTask, volumes, env)
        createTask.configure { task ->
            task.withPortMappings(httpPortMapping, httpsPortMapping)
        }

        registerStartContainerTask(createTask).configure { task ->
            // add socketProbes to http and https ports
            with(dockerExtension.developmentConfig) {
                task.withSocketProbe(
                        httpPortMapping.hostPort,
                        getDurationProperty(WS_READINESS_PROBE_INTERVAL, WS_READINESS_PROBE_INTERVAL_VALUE),
                        getDurationProperty(WS_READINESS_PROBE_TIMEOUT, WS_READINESS_PROBE_TIMEOUT_VALUE)
                )
                task.withSocketProbe(
                        httpsPortMapping.hostPort,
                        getDurationProperty(WS_READINESS_PROBE_INTERVAL, WS_READINESS_PROBE_INTERVAL_VALUE),
                        getDurationProperty(WS_READINESS_PROBE_TIMEOUT, WS_READINESS_PROBE_TIMEOUT_VALUE)
                )
            }
        }
    }
}
