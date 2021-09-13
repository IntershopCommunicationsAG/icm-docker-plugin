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

import com.intershop.gradle.icm.docker.tasks.mssql.AbstractMSSQLDBTask.Companion.FILENAME
import com.intershop.gradle.icm.docker.utils.Configuration.BACKUP_FOLDER_PATH_VALUE
import com.intershop.gradle.icm.docker.utils.PackageUtil
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

open class DownloadBackup : DefaultTask() {

    private val dbmoduleProperty: Property<String> = project.objects.property(String::class.java)
    private val dbversionProperty: Property<String> = project.objects.property(String::class.java)

    /**
     * Set export group and module without version (e.g. "com.intershop.icm:icm-as").
     *
     * @property dbmodule set provider for dbmodule property.
     */
    @set:Option(option = "dbmodule",
        description = "Set datatbase export group and module (e.g. 'com.intershop.icm:icm-as').")
    @get:Optional
    @get:Input
    var dbmodule:String
        get() = dbmoduleProperty.getOrElse("${project.group}:${project.name}")
        set(value) = dbmoduleProperty.set(value)

    /**
     * Set database export version (e.g. "7.11.0.0").
     *
     * @property dbversion set provider for deb export version property.
     */
    @set:Option(option = "dbversion",
        description = "Set database export version (e.g. '7.11.0.0').")
    @get:Input
    var dbversion:String?
        get() = dbversionProperty.get()
        set(value) = dbversionProperty.set(value)

    @get:Input
    val dbclassifier: Property<String> = project.objects.property(String::class.java)

    /**
     * The output file contains the MSSQL DB dump of the ICM.
     *
     * @property outputFile real file on file system with descriptor
     */
    @get:OutputFile
    val outputFile: RegularFileProperty = project.objects.fileProperty()

    init {
        group = "icm container mssql"
        description = "Downloads a database export file."

        dbclassifier.convention("dbexport")
        outputFile.convention(project.layout.buildDirectory.file("${BACKUP_FOLDER_PATH_VALUE}/${FILENAME}"))
    }

    /**
     * Creates the dump file.
     */
    @TaskAction
    fun downloadMSSQLExportFile() {
        if(! dbversion.isNullOrBlank()) {
            val file = PackageUtil.downloadPackage(project,
                            "$dbmodule:$dbversion",
                            dbclassifier.getOrElse("dbexport"))

            if(file != null) {
                project.copy {
                    it.from(project.zipTree(file))
                    it.into(outputFile.get().asFile.parentFile)
                    it.rename { outputFile.get().asFile.name }
                }
            } else {
                throw GradleException("The backup file for '$dbmodule:$dbversion' is not available!")
            }
        } else {
            throw GradleException("It is necessary to specify a version for the database backup!")
        }
    }
}
