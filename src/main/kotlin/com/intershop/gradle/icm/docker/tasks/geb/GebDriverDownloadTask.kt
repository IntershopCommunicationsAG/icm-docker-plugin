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

package com.intershop.gradle.icm.docker.tasks.geb

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import javax.inject.Inject

open class GebDriverDownloadTask @Inject constructor(objectFactory: ObjectFactory,
                                                     projectLayout: ProjectLayout,
                                                     private val fsOps: FileSystemOperations,
                                                     private val archiveOps: ArchiveOperations
) : DefaultTask() {

    @get:Input
    val url: Property<String> = objectFactory.property(String::class.java)

    @get:Input
    val extension: Property<String> = objectFactory.property(String::class.java)

    @get:OutputDirectory
    val driverDir: DirectoryProperty = objectFactory.directoryProperty()

    init {
        driverDir.convention(projectLayout.buildDirectory.dir("gebdriver"))
    }

    @TaskAction
    fun downloaddriver() {
        val targetDir = temporaryDir
        targetDir.createNewFile()
        val targetFile = File(targetDir, "driver.${extension.get()}")

        try {
            URL(url.get()).openStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch(ex: IOException) {
            throw GradleException("It was not possible to download the driver '" + url + "'(" + ex.message + ")")
        }

        when {
            extension.get().toLowerCase() == "zip" -> {
                fsOps.copy {
                    it.from(archiveOps.zipTree(targetFile))
                    it.into(driverDir)
                }
            }
            extension.get().toLowerCase() == "tar"
                    || extension.get().toLowerCase() == "tgz"
                    || extension.get().toLowerCase() == "tar.gz" -> {
                fsOps.copy {
                    it.from(archiveOps.tarTree(targetFile))
                    it.into(driverDir)
                }
            }
            else -> {
                throw GradleException("It is not possible to handle the file extension '" + extension.get() + "'")
            }
        }
    }
}
