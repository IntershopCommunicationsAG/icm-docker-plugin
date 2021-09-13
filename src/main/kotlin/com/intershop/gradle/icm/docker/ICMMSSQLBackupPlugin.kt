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
package com.intershop.gradle.icm.docker

import com.intershop.gradle.icm.docker.extension.DevelopmentConfiguration
import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.tasks.GenICMProperties.Companion.databaseJDBCUrlProp
import com.intershop.gradle.icm.docker.tasks.StartExtraContainer
import com.intershop.gradle.icm.docker.tasks.mssql.AbstractMSSQLDBTask
import com.intershop.gradle.icm.docker.tasks.mssql.DownloadBackup
import com.intershop.gradle.icm.docker.tasks.mssql.ExportMSSQLDB
import com.intershop.gradle.icm.docker.tasks.mssql.RestoreMSSQLDB
import com.intershop.gradle.icm.docker.utils.Configuration.DB_MSSQL_SA_PASSWORD
import com.intershop.gradle.icm.docker.utils.Configuration.DB_MSSQL_SA_PASSWORD_VALUE
import com.intershop.gradle.icm.docker.utils.Configuration.DB_USER_NAME
import com.intershop.gradle.icm.docker.utils.Configuration.DB_USER_PASSWORD
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

/**
 * The plugin for Intershop MSSQLDB backup functionality.
 */
class ICMMSSQLBackupPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        with(project) {
            val extension = extensions.findByType(
                IntershopDockerExtension::class.java
            ) ?: extensions.create("intershop_docker", IntershopDockerExtension::class.java, project)

            val config = extension.developmentConfig

            val exportTask = project.tasks.register("exportMSSQLDB", ExportMSSQLDB::class.java)
            configureDBTask(project, exportTask, config)
            exportTask.configure {
                it.mustRunAfter(tasks.named("dbPrepare"))
            }

            val restoreTask = project.tasks.register("restoreLocalMSSQLDB", RestoreMSSQLDB::class.java)
            configureDBTask(this, restoreTask, config)
            restoreTask.configure {
                it.adminPassword.set(config.getConfigProperty(DB_MSSQL_SA_PASSWORD, DB_MSSQL_SA_PASSWORD_VALUE))
            }

            val downloadTask = project.tasks.register("downloadDB", DownloadBackup::class.java)

            val importTask = project.tasks.register("restoreMSSQLDB", RestoreMSSQLDB::class.java)
            configureDBTask(this, importTask, config)
            importTask.configure {
                it.adminPassword.set(config.getConfigProperty(DB_MSSQL_SA_PASSWORD, DB_MSSQL_SA_PASSWORD_VALUE))
                it.dependsOn(downloadTask)
                it.inputPath.set(project.provider { downloadTask.get().outputFile.get().asFile.parent })
                it.filename.set(project.provider { downloadTask.get().outputFile.get().asFile.name })
            }
        }
    }

    private fun configureDBTask(project: Project,
                                task: TaskProvider<out AbstractMSSQLDBTask>,
                                config: DevelopmentConfiguration) {
        task.configure {

            it.jdbcUrl.set(config.getConfigProperty(databaseJDBCUrlProp))
            it.user.set(config.getConfigProperty(DB_USER_NAME))
            it.password.set(config.getConfigProperty(DB_USER_PASSWORD))

            it.mustRunAfter(project.tasks.named("startMSSQL", StartExtraContainer::class.java))
        }
    }
}
