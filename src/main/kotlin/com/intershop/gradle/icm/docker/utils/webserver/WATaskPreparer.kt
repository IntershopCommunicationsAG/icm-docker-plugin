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
import com.intershop.gradle.icm.docker.utils.Configuration.WS_CONTAINER_HTTPS_PORT
import com.intershop.gradle.icm.docker.utils.Configuration.WS_CONTAINER_HTTP_PORT
import com.intershop.gradle.icm.docker.utils.Configuration.WS_HTTPS_PORT
import com.intershop.gradle.icm.docker.utils.Configuration.WS_HTTP_PORT
import com.intershop.gradle.icm.docker.utils.PortMapping
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

        // Ports below this value are privileged and require NET_BIND_SERVICE for a non-root process to bind them
        private const val PRIVILEGED_PORT_LIMIT: Int = 1024

        // The WebAdapter runs as a non-root user inside the container
        // Binding privileged ports (< 1024, e.g. 80/443) therefore requires the NET_BIND_SERVICE capability
        @JvmStatic
        fun needsNetBindCapability(vararg containerPorts: Int): Boolean = containerPorts.any { it < PRIVILEGED_PORT_LIMIT }

        // The WebAdapter advertises its container (listen) port in generated URLs (links, redirects, server name)
        // If it differs from the published host port, those URLs point at a port the browser cannot reach (e.g. secure
        // links ending up on :8443 instead of :443)
        @JvmStatic
        fun portMismatchWarning(
            portMapping: PortMapping,
            hostPortProperty: String,
            containerPortProperty: String,
        ): String? {
            if (portMapping.hostPort == portMapping.containerPort) {
                return null
            }
            return "WebAdapter '${portMapping.name}' host port ($hostPortProperty=${portMapping.hostPort}) differs " +
                "from container port ($containerPortProperty=${portMapping.containerPort}). The WebAdapter " +
                "advertises its container port ${portMapping.containerPort} in generated URLs (links, redirects, " +
                "server name), which may not be reachable via the published host port ${portMapping.hostPort}. " +
                "Set '$containerPortProperty' equal to '$hostPortProperty' to avoid broken URLs."
        }
    }

    override fun getExtensionName(): String = extName
    override fun getImage(): Provider<String> = dockerExtension.images.webadapter
    override fun getUseHostUserConfigProperty(): String = Configuration.WA_USE_HOST_USER
    override fun getAutoRemoveContainerConfigProperty() : String = Configuration.WA_AUTOREMOVE_CONTAINER
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

        // Warn on mismatching host/container ports
        warnOnPortMismatch(httpPortMapping, WS_HTTP_PORT, WS_CONTAINER_HTTP_PORT)
        warnOnPortMismatch(httpsPortMapping, WS_HTTPS_PORT, WS_CONTAINER_HTTPS_PORT)

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

            // Tell the WebAdapter which http port it listens on internally
            env.add("ICM_WA_HTTP_PORT", httpPortMapping.containerPort.toString())

            // Tell the WebAdapter which https port it listens on internally
            env.add("ICM_WA_HTTPS_PORT", httpsPortMapping.containerPort.toString())

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

            if (needsNetBindCapability(httpPortMapping.containerPort, httpsPortMapping.containerPort)) {
                task.hostConfig.capAdd.add("NET_BIND_SERVICE")
            }
        }

        registerStartContainerTask(createTask).configure { task ->
            // add socketProbes to http and https ports
            with(dockerExtension.developmentConfig) {
                task.withSocketProbe(
                        httpPortMapping.hostPort,
                        getDurationProperty(Configuration.WS_READINESS_PROBE_INTERVAL,
                            Configuration.WS_READINESS_PROBE_INTERVAL_VALUE),
                        getDurationProperty(Configuration.WS_READINESS_PROBE_TIMEOUT,
                            Configuration.WS_READINESS_PROBE_TIMEOUT_VALUE)
                )
                task.withSocketProbe(
                        httpsPortMapping.hostPort,
                        getDurationProperty(Configuration.WS_READINESS_PROBE_INTERVAL,
                            Configuration.WS_READINESS_PROBE_INTERVAL_VALUE),
                        getDurationProperty(Configuration.WS_READINESS_PROBE_TIMEOUT,
                            Configuration.WS_READINESS_PROBE_TIMEOUT_VALUE)
                )
            }
        }
    }

    private fun warnOnPortMismatch(portMapping: PortMapping, hostPortProperty: String, containerPortProperty: String) {
        portMismatchWarning(portMapping, hostPortProperty, containerPortProperty)?.let { portMismatchMessage ->
            project.logger.warn(portMismatchMessage)
        }
    }
}
