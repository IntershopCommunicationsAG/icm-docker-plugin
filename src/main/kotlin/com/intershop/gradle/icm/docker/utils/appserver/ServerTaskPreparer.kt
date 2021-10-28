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
import com.intershop.gradle.icm.docker.tasks.StartServerContainer
import com.intershop.gradle.icm.docker.utils.Configuration.AS_CONNECTOR_CONTAINER_PORT
import com.intershop.gradle.icm.docker.utils.Configuration.AS_CONNECTOR_CONTAINER_PORT_VALUE
import com.intershop.gradle.icm.docker.utils.Configuration.AS_EXT_CONNECTOR_PORT
import com.intershop.gradle.icm.docker.utils.Configuration.AS_EXT_CONNECTOR_PORT_VALUE
import com.intershop.gradle.icm.docker.utils.PortMapping
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Provider

class ServerTaskPreparer(
        project: Project,
        networkTask: Provider<PrepareNetwork>,
) : AbstractTaskPreparer(project, networkTask) {

    companion object {
        const val extName: String = "AS"
    }

    override fun getExtensionName(): String = extName
    override fun getImage(): Provider<String> = extension.images.webadapteragent

    init {
        initAppTasks()

        project.tasks.register("start${getExtensionName()}", StartServerContainer::class.java) { task ->
            configureContainerTask(task)
            task.description = "Start container Application server of ICM"

            task.targetImageId(project.provider { pullTask.get().image.get() })
            task.image.set(pullTask.get().image)

            val httpASContainerPort = extension.developmentConfig.getConfigProperty(
                    AS_CONNECTOR_CONTAINER_PORT,
                    AS_CONNECTOR_CONTAINER_PORT_VALUE
            )
            task.envVars.put("INTERSHOP_SERVLETENGINE_CONNECTOR_PORT", httpASContainerPort)
            task.hostConfig.portBindings.addAll(project.provider {
                getPortMappings().map { pm -> pm.render() }.apply {
                    project.logger.info("Using the following port bindings for container startup in task {}: {}",
                            task.name, this)
                }
            })

            task.hostConfig.network.set(networkId)

            task.hostConfig.binds.set(getServerVolumes().apply {
                project.logger.info("Using the following volume binds for container startup in task {}: {}",
                        task.name,this)
            })
            task.finishedCheck = SERVER_READY_STRING

            task.dependsOn(pullTask, prepareServer, prepareServer, networkTask)
        }
    }

    override fun getPortMappings(): Set<PortMapping> {
        with(extension.developmentConfig) {

            val httpASContainerPort = try {
                getConfigProperty(
                        AS_CONNECTOR_CONTAINER_PORT,
                        AS_CONNECTOR_CONTAINER_PORT_VALUE
                ).toInt()
            } catch (e: NumberFormatException) {
                throw GradleException(
                        "Configuration property $AS_CONNECTOR_CONTAINER_PORT is not a valid int value", e)
            }
            val httpASPort = try {
                getConfigProperty(
                        AS_EXT_CONNECTOR_PORT,
                        AS_EXT_CONNECTOR_PORT_VALUE
                ).toInt()
            } catch (e: NumberFormatException) {
                throw GradleException(
                        "Configuration property $AS_EXT_CONNECTOR_PORT is not a valid int value", e)
            }
            return super.getPortMappings().plus(PortMapping(httpASPort, httpASContainerPort))
        }
    }

}
