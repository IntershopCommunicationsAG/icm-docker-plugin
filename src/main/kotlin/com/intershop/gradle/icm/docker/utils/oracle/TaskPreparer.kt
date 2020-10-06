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

package com.intershop.gradle.icm.docker.utils.oracle

import com.intershop.gradle.icm.docker.tasks.PrepareNetwork
import com.intershop.gradle.icm.docker.tasks.StartExtraContainer
import com.intershop.gradle.icm.docker.utils.AbstractTaskPreparer
import com.intershop.gradle.icm.docker.utils.Configuration
import org.gradle.api.Project
import org.gradle.api.provider.Provider

class TaskPreparer(project: Project,
                   networkTask: Provider<PrepareNetwork>) : AbstractTaskPreparer(project, networkTask) {

    companion object {
        const val extName: String = "Oracle"
    }

    override val image: Provider<String> = extension.images.mssqldb
    override val extensionName: String = extName
    override val containerExt: String = extensionName.toLowerCase()

    init {
        initBaseTasks()

        project.tasks.register("start${extensionName}", StartExtraContainer::class.java) { task ->
            configureContainerTask(task)
            task.description = "Starts an Oracle XE server"

            task.targetImageId(project.provider { pullTask.get().image.get() })
            task.image.set(pullTask.get().image)

            with(extension.developmentConfig) {
                val port = getConfigProperty(
                    Configuration.DB_ORACLE_PORT,
                    Configuration.DB_ORACLE_PORT_VALUE
                )
                val containerPort = getConfigProperty(
                    Configuration.DB_ORACLE_CONTAINER_PORT,
                    Configuration.DB_ORACLE_CONTAINER_PORT_VALUE
                )

                val listenerPort = getConfigProperty(
                    Configuration.DB_ORACLE_LISTENERPORT,
                    Configuration.DB_ORACLE_LISTENERPORT_VALUE
                )
                val containerListenerPort = getConfigProperty(
                    Configuration.DB_ORACLE_CONTAINER_LISTENERPORT,
                    Configuration.DB_ORACLE_CONTAINER_LISTENERPORT_VALUE
                )

                task.hostConfig.portBindings.set(
                    listOf("${port}:${containerPort}",
                           "${listenerPort}:${containerListenerPort}")
                )
            }

            task.dependsOn(pullTask, networkTask)
        }
    }
}
