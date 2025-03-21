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
import com.intershop.gradle.icm.docker.tasks.utils.ContainerEnvironment
import com.intershop.gradle.icm.docker.utils.AbstractTaskPreparer
import com.intershop.gradle.icm.docker.utils.Configuration
import org.gradle.api.Project
import org.gradle.api.provider.Provider

class TaskPreparer(project: Project,
                   networkTask: Provider<PrepareNetwork>) : AbstractTaskPreparer(project, networkTask){

    companion object {
        const val EXT_NAME: String = "MSSQL"
    }

    override fun getExtensionName(): String = EXT_NAME
    override fun getImage(): Provider<String> = dockerExtension.images.mssqldb
    override fun getUseHostUserConfigProperty(): String = Configuration.DB_MSSQL_USE_HOST_USER
    override fun getAutoRemoveContainerConfigProperty() : String = Configuration.DB_MSSQL_AUTOREMOVE_CONTAINER

    init {
        initBaseTasks()

        val portMapping = devConfig.getPortMapping(
                "JDBC",
                Configuration.DB_MSSQL_PORT,
                Configuration.DB_MSSQL_PORT_VALUE,
                Configuration.DB_MSSQL_CONTAINER_PORT,
                Configuration.DB_MSSQL_CONTAINER_PORT_VALUE,
                true
        )
        val dbName = devConfig.getConfigProperty(
                Configuration.DB_MSSQL_DBNAME,
                Configuration.DB_MSSQL_DBNAME_VALUE
        )
        val env = ContainerEnvironment().addAll(
                "ACCEPT_EULA" to "Y",
                "SA_PASSWORD" to devConfig.getConfigProperty(Configuration.DB_MSSQL_SA_PASSWORD,
                        Configuration.DB_MSSQL_SA_PASSWORD_VALUE), "MSSQL_PID" to "Developer",
                "RECREATEDB" to devConfig.getConfigProperty(
                        Configuration.DB_MSSQL_RECREATE_DB,
                        Configuration.DB_MSSQL_RECREATE_DB_VALUE
                ),
                "RECREATEUSER" to devConfig.getConfigProperty(
                        Configuration.DB_MSSQL_RECREATE_USER,
                        Configuration.DB_MSSQL_RECREATE_USER_VALUE
                ),
                "ICM_DB_NAME" to dbName,
                "ICM_DB_USER" to devConfig.getConfigProperty(
                        Configuration.DB_USER_NAME,
                        Configuration.DB_USER_NAME_VALUE
                ),
                "ICM_DB_PASSWORD" to devConfig.getConfigProperty(
                        Configuration.DB_USER_PASSWORD,
                        Configuration.DB_USER_PASSWORD_VALUE
                )
        )
        val dataPath = devConfig.getFileProperty(Configuration.DATA_FOLDER_PATH,
                project.layout.buildDirectory.dir("data_folder").get().asFile).absolutePath
        val backupPath = devConfig.getFileProperty(Configuration.BACKUP_FOLDER_PATH,
                project.layout.buildDirectory.dir("data_backup_folder").get().asFile).absolutePath
        val volumes = mutableMapOf<String, String>(
                dataPath to devConfig.getConfigProperty(Configuration.DATA_FOLDER_VOLUME,
                        Configuration.DATA_FOLDER_VOLUME_VALUE),
                backupPath to devConfig.getConfigProperty(Configuration.BACKUP_FOLDER_VOLUME,
                        Configuration.BACKUP_FOLDER_VOLUME_VALUE)
        )

        val createTask = registerCreateContainerTask(findTask, volumes, env)
        createTask.configure { task ->
            task.withPortMappings(portMapping)
        }

        registerStartContainerTask(createTask).configure { task ->
            // TODO #72420 replace by probe (SocketProbe will not help: socket gets available too early)
            task.finishedCheck.set("Parallel redo is shutdown for database")

            task.doLast {
                project.logger.quiet(
                        "The database can be connected with jdbc:sqlserver://{}:{};databaseName={}",
                        getContainerName(),
                        portMapping.containerPort,
                        dbName
                )
            }
        }

    }
}
