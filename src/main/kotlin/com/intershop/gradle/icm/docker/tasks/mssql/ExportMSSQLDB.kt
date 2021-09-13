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
package com.intershop.gradle.icm.docker.tasks.mssql

import com.intershop.gradle.icm.docker.utils.Configuration.BACKUP_FOLDER_PATH_VALUE
import org.gradle.api.GradleException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.sql.SQLException
import javax.inject.Inject

/**
 * CreateMSSQLDump Gradle task 'createMSSQLDump'
 *
 * This task creates a database dump in the MSSQL docker container and makes it available in a docker volume.
 */
open class ExportMSSQLDB @Inject constructor(objectFactory: ObjectFactory) : AbstractMSSQLDBTask(objectFactory) {

    /**
     * The output file contains the MSSQL DB dump of the ICM.
     *
     * @property outputPath path for output
     */
    @get:Input
    val outputPath: Property<String> = objectFactory.property(String::class.java)

    init {
        group = "icm container mssql"
        description = "Creates a database export file."

        outputPath.convention(project.layout.buildDirectory.file(BACKUP_FOLDER_PATH_VALUE).get().asFile.absolutePath)

        // the task is not incremental!
        outputs.upToDateWhen { false }
    }

    /**
     * Creates the dump file.
     */
    @TaskAction
    fun exportMSSQLDBTask() {
        val outputFile = File("${outputPath.get()}/${filename.get()}")
        if(outputFile.exists()) {
            logger.quiet("Remove existing backup file ${outputFile}.")
            if(! outputFile.delete()) {
                throw GradleException("It was not possible to delete  ${outputFile}.")
            }
        }
        checkInputParams()

        try {
            project.logger.info("Querying Dump Creation")
            val request = """
                BACKUP DATABASE ${getDBNameFrom(jdbcUrl.get())}
                TO DISK = '/var/opt/mssql/backup/${filename.get()}'
                   WITH FORMAT,
                      MEDIANAME = 'ICMDBBackup',
                      NAME = 'Full Backup of ICM DB'
            """.trimIndent()
            jdbcRequest(request, jdbcUrl.get(), user.get(), password.get())
        } catch (ex: SQLException) {
            ex.printStackTrace()
            throw GradleException("It was not possible to create an export file for the MSSQL database.")
        }
    }
}
