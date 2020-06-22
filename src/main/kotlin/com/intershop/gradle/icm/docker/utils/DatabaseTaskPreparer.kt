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
package com.intershop.gradle.icm.docker.utils

import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.tasks.PullExtraImage
import com.intershop.gradle.icm.docker.tasks.RemoveContainerByName
import com.intershop.gradle.icm.docker.tasks.StartExtraContainerTask
import com.intershop.gradle.icm.docker.tasks.StopExtraContainerTask
import org.gradle.api.Project

class DatabaseTaskPreparer(val project: Project,
                           private val dockerExtension: IntershopDockerExtension) {

    companion object {
        const val TASK_PULL_MSSQLDB = "pullMSSQL"
        const val TASK_START_MSSQLDB = "startMSSQL"
        const val TASK_STOP_MSSQLDB = "stopMSSQL"
        const val TASK_REMOVE_MSSQLDB = "removeMSSQL"
    }

    fun getMSSQLPullTask(): PullExtraImage {
        return with(project) {
            tasks.maybeCreate(
                TASK_PULL_MSSQLDB,
                PullExtraImage::class.java).apply {
                this.image.set(dockerExtension.images.mssqldb)

                this.onlyIf { dockerExtension.images.mssqldb.isPresent }
            }
        }
    }

    fun getMSSQLStartTask(image: PullExtraImage): StartExtraContainerTask {
        return with(project) {
            tasks.maybeCreate(
                TASK_START_MSSQLDB,
                StartExtraContainerTask::class.java).apply {
                attachStderr.set(true)
                attachStdout.set(true)

                targetImageId(image.image)

                containerName.set("${project.name.toLowerCase()}-mssql")

                with(dockerExtension.developmentConfig) {
                    hostConfig.portBindings.set(
                        listOf("${getConfigProperty( "intershop.db.msql.hostport", "1433")}:1433"))
                    envVars.set( mutableMapOf(
                        "ACCEPT_EULA" to
                                "Y",
                        "SA_PASSWORD" to
                                getConfigProperty( "intershop.db.msql.sa.password", "1nstershop5A"),
                        "MSSQL_PID" to
                                "Developer",
                        "RECREATEDB" to
                                getConfigProperty("intershop.db.msql.recreatedb", "false"),
                        "RECREATEUSER" to
                                getConfigProperty("intershop.db.msql.recreateuser", "false"),
                        "ICM_DB_NAME" to
                                getConfigProperty("intershop.db.msql.dbname", "icmtestdb"),
                        "ICM_DB_USER" to
                                getConfigProperty("intershop.jdbc.user", "intershop"),
                        "ICM_DB_PASSWORD" to
                                getConfigProperty("intershop.jdbc.password", "intershop")
                    ))
                }

                this.onlyIf { dockerExtension.images.mssqldb.isPresent }
            }
        }
    }

    fun getMSSQLStopTask(): StopExtraContainerTask {
        return with(project) {
            tasks.maybeCreate(
                TASK_STOP_MSSQLDB,
                StopExtraContainerTask::class.java).apply {
                containerName.set("${project.name.toLowerCase()}-mssql")

                this.onlyIf { dockerExtension.images.mssqldb.isPresent }
            }
        }
    }

    fun getMSSQLRemoveTask(): RemoveContainerByName {
        return with(project) {
            tasks.maybeCreate(
                TASK_REMOVE_MSSQLDB,
                RemoveContainerByName::class.java).apply {
                containerName.set("${project.name.toLowerCase()}-mssql")

                this.onlyIf { dockerExtension.images.mssqldb.isPresent }
            }
        }
    }
}
