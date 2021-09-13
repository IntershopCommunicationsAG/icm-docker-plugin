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
import javax.inject.Inject

open class RestoreMSSQLDB @Inject constructor(objectFactory: ObjectFactory) : AbstractMSSQLDBTask(objectFactory) {

    /**
     * The output file contains the MSSQL DB dump of the ICM.
     *
     * @property inputPath path for backup file
     */
    @get:Input
    val inputPath: Property<String> = objectFactory.property(String::class.java)

    /**
     * Set provider for mssql admin password property.
     *
     * @property adminPassword provider for mssql password.
     */
    @get:Input
    val adminPassword: Property<String> = objectFactory.property(String::class.java)

    init {
        group = "icm container mssql"
        description = "Restore a database from export file."

        inputPath.convention(project.layout.buildDirectory.file(BACKUP_FOLDER_PATH_VALUE).get().asFile.absolutePath)

        // the task is not incremental!
        outputs.upToDateWhen { false }
    }

    /**
     * Restores the project database dump.
     */
    @TaskAction
    fun restoreMSSQLDump() {
        val inputFile = File("${inputPath.get()}/${filename.get()}")
        if(! inputFile.exists()) {
            throw GradleException("The database backup is not available for import. Check ${inputFile.absolutePath}")
        }
        checkInputParams()

        val dbnameStr = getDBNameFrom(jdbcUrl.get())
        val urlParts = jdbcUrl.get().split(";")

        jdbcRequest("""
                    RESTORE DATABASE $dbnameStr
                    FROM DISK = '/var/opt/mssql/backup/${filename.get()}' 
                    WITH REPLACE
                    """.trimIndent(), urlParts[0],"sa", adminPassword.get())
        jdbcRequest("""
                    ALTER AUTHORIZATION ON DATABASE::[$dbnameStr ] TO [sa]
                    """.trimIndent(), urlParts[0],"sa", adminPassword.get())
    }
}
