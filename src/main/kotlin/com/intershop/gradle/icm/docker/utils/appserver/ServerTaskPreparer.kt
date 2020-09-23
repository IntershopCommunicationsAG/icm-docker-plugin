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

package com.intershop.gradle.icm.docker.utils.appserver

import com.intershop.gradle.icm.docker.tasks.PrepareNetwork
import com.intershop.gradle.icm.docker.tasks.StartServerContainerTask
import com.intershop.gradle.icm.docker.utils.Configuration
import org.gradle.api.Project
import org.gradle.api.provider.Provider

class ServerTaskPreparer(project: Project,
                          networkTask: Provider<PrepareNetwork>) : AbstractTaskPreparer(project, networkTask) {

    companion object {
        const val extName: String = "AS"
    }

    override val extensionName: String = extName
    override val containerExt: String = extensionName.toLowerCase()

    init {
        initAppTasks()

        project.tasks.register("start${extensionName}", StartServerContainerTask::class.java) { task ->
            configureContainerTask(task)
            task.description = "Start container Application server of ICM"
            task.targetImageId(project.provider { pullTask.get().image.get() })

            with(extension.developmentConfig) {

                val httpASContainerPort = getConfigProperty(
                    Configuration.AS_CONNECTOR_CONTAINER_PORT,
                    Configuration.AS_CONNECTOR_CONTAINER_PORT_VALUE
                )
                val httpASPort = getConfigProperty(
                    Configuration.AS_EXT_CONNECTOR_PORT,
                    Configuration.AS_EXT_CONNECTOR_PORT_VALUE
                )

                val httpJMXContainerPort = getConfigProperty(
                    Configuration.AS_JMX_CONNECTOR_CONTAINER_PORT,
                    Configuration.AS_JMX_CONNECTOR_CONTAINER_PORT_VALUE
                )
                val httpJMXPort = getConfigProperty(
                    Configuration.AS_JMX_CONNECTOR_PORT,
                    Configuration.AS_JMX_CONNECTOR_PORT_VALUE
                )

                task.envVars.put("INTERSHOP_SERVLETENGINE_CONNECTOR_PORT", httpASContainerPort)
                task.hostConfig.portBindings.set(
                    listOf(
                        "5005:7746",
                        "${httpASPort}:${httpASContainerPort}",
                        "${httpJMXPort}:${httpJMXContainerPort}"
                    )
                )

                task.hostConfig.network.set(networkId)
            }

            task.hostConfig.binds.set(getServerVolumes())
            task.finishedCheck = SERVER_READY_STRING

            task.dependsOn(dirPreparer, prepareServer, prepareServer, networkTask)
        }
    }
}