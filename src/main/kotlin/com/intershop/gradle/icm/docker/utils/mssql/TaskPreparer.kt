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
import com.intershop.gradle.icm.docker.tasks.StartExtraContainer
import com.intershop.gradle.icm.docker.utils.AbstractTaskPreparer
import com.intershop.gradle.icm.docker.utils.Configuration
import com.intershop.gradle.icm.docker.utils.PortMapping
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import java.io.File

class TaskPreparer(project: Project,
                   networkTask: Provider<PrepareNetwork>) : AbstractTaskPreparer(project, networkTask){

    companion object {
        const val extName: String = "MSSQL"
    }

    override fun getExtensionName(): String = extName
    override fun getImage(): Provider<String> = dockerExtension.images.mssqldb

    init {
        initBaseTasks()

        project.tasks.register("start${getExtensionName()}", StartExtraContainer::class.java) { task ->
            configureContainerTask(task)
            task.description = "Starts an MSSQL server"

            task.targetImageId(project.provider { pullTask.get().image.get() })
            task.image.set(pullTask.get().image)

            val portMapping : PortMapping
            val dbName : String
            with(dockerExtension.developmentConfig) {
                portMapping = getPortMapping("JDBC",
                    Configuration.DB_MSSQL_PORT,
                    Configuration.DB_MSSQL_PORT_VALUE,
                    Configuration.DB_MSSQL_CONTAINER_PORT,
                    Configuration.DB_MSSQL_CONTAINER_PORT_VALUE,
                    true
                )
                task.withPortMappings(portMapping)
                task.hostConfig.network.set(networkId)

                dbName = getConfigProperty(
                    Configuration.DB_MSSQL_DBNAME,
                    Configuration.DB_MSSQL_DBNAME_VALUE
                )

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
                        "ICM_DB_NAME" to dbName,
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

                val volumeMap = mutableMapOf<String, String>()

                // add data path if configured
                val dataPath = getConfigProperty(Configuration.DATA_FOLDER_PATH,"")
                val dataPahtFP = if(dataPath.isBlank()) {
                    project.layout.buildDirectory.dir("data_folder").get().asFile
                } else {
                    File(dataPath)
                }

                volumeMap[dataPahtFP.absolutePath] = dockerExtension.developmentConfig.getConfigProperty(
                    Configuration.DATA_FOLDER_VOLUME,
                    Configuration.DATA_FOLDER_VOLUME_VALUE)

                // add backup folder - default is build directory
                val backupPath = getConfigProperty(Configuration.BACKUP_FOLDER_PATH)
                val backupPathFP = if(backupPath.isBlank()) {
                    project.layout.buildDirectory.dir("data_backup_folder").get().asFile
                } else {
                    File(backupPath)
                }
                volumeMap[backupPathFP.absolutePath] = dockerExtension.developmentConfig.getConfigProperty(
                                            Configuration.BACKUP_FOLDER_VOLUME,
                                            Configuration.BACKUP_FOLDER_VOLUME_VALUE)

                volumeMap.forEach { path, _ -> File(path).mkdirs() }

                task.hostConfig.binds.set(volumeMap)
            }

            // TODO #72420 replace by probe (SocketProbe will not help: socket gets available too early)
            task.finishedCheck = "Parallel redo is shutdown for database"

            project.logger.quiet(
                "The database can be connected with jdbc:sqlserver://{}:{};databaseName={}",
                task.containerName.get(),
                portMapping.containerPort,
                dbName
            )
            task.dependsOn(pullTask, networkTask)
        }
    }
}
