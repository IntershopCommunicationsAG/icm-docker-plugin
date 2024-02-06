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
import com.intershop.gradle.icm.docker.utils.appsrv.ASTaskPreparer
import org.gradle.api.Project
import org.gradle.api.provider.Provider

class WAATaskPreparer(project: Project,
                      networkTask: Provider<PrepareNetwork>,
                      volumes: Map<String,String>) : AbstractTaskPreparer(project, networkTask) {

    companion object {
        const val extName: String = "WAA"
    }

    override fun getExtensionName(): String = extName
    override fun getImage(): Provider<String> = dockerExtension.images.webadapteragent
    override fun getUseHostUserConfigProperty(): String = Configuration.WAA_USE_HOST_USER
    override fun getTaskGroupExt(): String = "webserver"

    init{
        initBaseTasks()

        val env = with(dockerExtension.developmentConfig) {
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

            ContainerEnvironment().add(
                    "ICM_ICMSERVLETURLS",
                    "cs.url.0=http://${host}:${port}/servlet/ConfigurationServlet"
            )
        }
        val createTask = registerCreateContainerTask(findTask, volumes, env)
        registerStartContainerTask(createTask)

    }
}
