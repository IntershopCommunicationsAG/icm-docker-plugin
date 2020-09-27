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
package com.intershop.gradle.icm.docker.utils.mssql

import com.intershop.gradle.icm.docker.tasks.PrepareNetwork
import com.intershop.gradle.icm.docker.tasks.StartExtraContainerTask
import com.intershop.gradle.icm.docker.utils.AbstractTaskPreparer
import com.intershop.gradle.icm.docker.utils.Configuration
import org.gradle.api.Project
import org.gradle.api.provider.Provider

class TaskPreparer(project: Project,
                   networkTask: Provider<PrepareNetwork>) : AbstractTaskPreparer(project, networkTask){

    companion object {
        const val extName: String = "MSSQL"
    }

    override val image: Provider<String> = extension.images.mssqldb
    override val extensionName: String = extName
    override val containerExt: String = extensionName.toLowerCase()

    init {
        initBaseTasks()

        project.tasks.register("start${extensionName}", StartExtraContainerTask::class.java) { task ->
            configureContainerTask(task)
            task.description = "Starts an MSSQL server"

            task.targetImageId(project.provider { pullTask.get().image.get() })
            task.image.set(pullTask.get().image)

            with(extension.developmentConfig) {
                val port = getConfigProperty(
                    Configuration.DB_MSSQL_PORT,
                    Configuration.DB_MSSQL_PORT_VALUE
                )
                val containerPort = getConfigProperty(
                    Configuration.DB_MSSQL_CONTAINER_PORT,
                    Configuration.DB_MSSQL_CONTAINER_PORT_VALUE
                )

                task.hostConfig.portBindings.set(
                    listOf("${port}:${containerPort}")
                )

                task.hostConfig.network.set(networkId)

                task.envVars.set(
                    mutableMapOf(
                        "ACCEPT_EULA" to
                                "Y",
                        "SA_PASSWORD" to
                                getConfigProperty(
                                    Configuration.DB_MSSQL_SA_PASSWORD,
                                    Configuration.DB_MSSQL_SA_PASSWORD_VALUE
                                ),
                        "MSSQL_PID" to
                                "Developer",
                        "RECREATEDB" to
                                getConfigProperty(
                                    Configuration.DB_MSSQL_RECREATE_DB,
                                    Configuration.DB_MSSQL_RECREATE_DB_VALUE
                                ),
                        "RECREATEUSER" to
                                getConfigProperty(
                                    Configuration.DB_MSSQL_RECREATE_USER,
                                    Configuration.DB_MSSQL_RECREATE_USER_VALUE
                                ),
                        "ICM_DB_NAME" to
                                getConfigProperty(
                                    Configuration.DB_MSSQL_DBNAME,
                                    Configuration.DB_MSSQL_DBNAME_VALUE
                                ),
                        "ICM_DB_USER" to
                                getConfigProperty(
                                    Configuration.DB_USER_NAME,
                                    Configuration.DB_USER_NAME_VALUE
                                ),
                        "ICM_DB_PASSWORD" to
                                getConfigProperty(
                                    Configuration.DB_USER_PASSWORD,
                                    Configuration.DB_USER_PASSWORD_VALUE
                                )
                    )
                )
            }

            task.dependsOn(pullTask, networkTask)
        }
    }
}
