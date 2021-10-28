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
import com.intershop.gradle.icm.docker.tasks.StartExtraContainer
import com.intershop.gradle.icm.docker.utils.AbstractTaskPreparer
import com.intershop.gradle.icm.docker.utils.Configuration
import com.intershop.gradle.icm.docker.utils.appserver.ServerTaskPreparer
import org.gradle.api.Project
import org.gradle.api.provider.Provider

class WATaskPreparer(project: Project,
                     networkTask: Provider<PrepareNetwork>,
                     volumes: Map<String,String>) : AbstractTaskPreparer(project, networkTask) {

    companion object {
        const val extName: String = "WA"
    }

    override fun getExtensionName(): String = extName
    override fun getImage(): Provider<String> = extension.images.webadapter

    init{
        initBaseTasks()

        pullTask.configure {
            it.group = "icm container webserver"
        }
        stopTask.configure {
            it.group = "icm container webserver"
        }
        removeTask.configure {
            it.group = "icm container webserver"
        }

        project.tasks.register("start${getExtensionName()}", StartExtraContainer::class.java) { task ->
            configureContainerTask(task)
            task.group = "icm container webserver"
            task.description = "Start ICM WebServer with WebAdapter"

            task.targetImageId(project.provider { pullTask.get().image.get() })
            task.image.set(pullTask.get().image)

            with(extension.developmentConfig) {
                val httpPort = getConfigProperty(
                    Configuration.WS_HTTP_PORT,
                    Configuration.WS_HTTP_PORT_VALUE)
                val httpContainerPort = getConfigProperty(
                    Configuration.WS_CONTAINER_HTTP_PORT,
                    Configuration.WS_CONTAINER_HTTP_PORT_VALUE)
                val httpsPort = getConfigProperty(
                    Configuration.WS_HTTPS_PORT,
                    Configuration.WS_HTTPS_PORT_VALUE)
                val httpsContainerPort = getConfigProperty(
                    Configuration.WS_CONTAINER_HTTPS_PORT,
                    Configuration.WS_CONTAINER_HTTPS_PORT_VALUE)

                val serverCertName = getConfigProperty(
                    Configuration.WS_SERVER_CERT, "")
                if(serverCertName.isNotBlank()) {
                    task.envVars.put("ICM_SERVERCERT", serverCertName)
                }

                val privateKeyName = getConfigProperty(
                    Configuration.WS_SERVER_PRIVAT, "")
                if(privateKeyName.isNotBlank()) {
                    task.envVars.put("ICM_SERVERPRIVATEKEY", privateKeyName)
                }

                val usehttp2 = getConfigProperty(
                    Configuration.WS_SERVER_HTTP2, "false")
                if(usehttp2 == "true") {
                    task.envVars.put("USEHTTP2", "true")
                }

                task.hostConfig.portBindings.set(
                    listOf("${httpPort}:${httpContainerPort}", "${httpsPort}:${httpsContainerPort}")
                )

                task.hostConfig.network.set(networkId)

                val asHTTPPort = if (appserverAsContainer) {
                    getConfigProperty(
                        Configuration.AS_CONNECTOR_CONTAINER_PORT,
                        Configuration.AS_CONNECTOR_CONTAINER_PORT_VALUE
                    )
                } else {
                    getConfigProperty(
                        Configuration.AS_CONNECTOR_PORT,
                        Configuration.AS_CONNECTOR_PORT_VALUE
                    )
                }

                val asHostname = if (appserverAsContainer) {
                    "${extension.containerPrefix}-${ServerTaskPreparer.extName}"
                } else {
                    getConfigProperty(
                        Configuration.LOCAL_CONNECTOR_HOST,
                        Configuration.LOCAL_CONNECTOR_HOST_VALUE
                    )
                }

                task.envVars.put(
                    "ICM_ICMSERVLETURLS",
                    "cs.url.0=http://${asHostname}:${asHTTPPort}/servlet/ConfigurationServlet")
            }
            task.hostConfig.binds.set( volumes )

            task.dependsOn(pullTask, networkTask)
        }
    }
}
