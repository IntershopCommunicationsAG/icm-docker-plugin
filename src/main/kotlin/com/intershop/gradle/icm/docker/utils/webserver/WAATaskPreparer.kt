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
import com.intershop.gradle.icm.docker.utils.appsrv.ServerTaskPreparer
import org.gradle.api.Project
import org.gradle.api.provider.Provider

class WAATaskPreparer(project: Project,
                      networkTask: Provider<PrepareNetwork>,
                      volumes: Map<String,String>) : AbstractTaskPreparer(project, networkTask) {

    companion object {
        const val extName: String = "WAA"
        const val GROUP_NAME: String = "icm container webserver"
    }

    override fun getExtensionName(): String = extName
    override fun getImage(): Provider<String> = dockerExtension.images.webadapteragent
    override fun getUseHostUserConfigProperty(): String = Configuration.WAA_USE_HOST_USER

    init{
        initBaseTasks()

        pullTask.configure {
            it.group = GROUP_NAME
        }
        stopTask.configure {
            it.group = GROUP_NAME
        }
        removeTask.configure {
            it.group = GROUP_NAME
        }

        project.tasks.register("start${getExtensionName()}", StartExtraContainer::class.java) { task ->
            configureContainerTask(task)
            task.group = "icm container webserver"
            task.description = "Start ICM WebAdapterAgent"

            task.targetImageId(project.provider { pullTask.get().image.get() })
            task.image.set(pullTask.get().image)

            with(dockerExtension.developmentConfig) {
                val portMapping = asPortConfiguration.managementConnector.get()

                val host: String
                val port: Int
                if (appserverAsContainer) {
                    // started as container
                    host = "${dockerExtension.containerPrefix}-${ServerTaskPreparer.extName.lowercase()}"
                    port = portMapping.containerPort
                } else {

                    // started externally
                    host = getConfigProperty(
                            Configuration.LOCAL_CONNECTOR_HOST,
                            Configuration.LOCAL_CONNECTOR_HOST_VALUE
                    )
                    port = portMapping.hostPort
                }

                task.envVars.put(
                    "ICM_ICMSERVLETURLS",
                    "cs.url.0=http://${host}:${port}/servlet/ConfigurationServlet"
                )
            }
            task.hostConfig.binds.set( volumes )
            task.hostConfig.network.set(networkId)

            task.dependsOn(pullTask, networkTask)
        }
    }
}
