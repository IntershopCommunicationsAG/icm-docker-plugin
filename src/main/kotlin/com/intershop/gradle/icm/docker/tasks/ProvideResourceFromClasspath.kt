/*
 * Copyright 2019 Intershop Communications AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.intershop.gradle.icm.docker.tasks

import com.intershop.gradle.icm.docker.utils.CustomizationImageBuildPreparer
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import javax.inject.Inject

/**
 * Provides the content of a classpath resource as a file at a target location
 */
open class ProvideResourceFromClasspath
    @Inject
    constructor(@Internal val fileSystemOperations: FileSystemOperations) : DefaultTask() {

    @get:Input
    val resourceName: Property<String> = project.objects.property(String::class.java)

    @get:OutputFile
    val targetLocation: RegularFileProperty = project.objects.fileProperty()

    /**
     * Task action starts the java process in the background.
     */
    @TaskAction
    fun execute() {
        val resource = javaClass.classLoader.getResource(resourceName.get())
                       ?: throw GradleException(
                               "Required '${resourceName.get()}' is missing inside the classpath")
        val content = resource.readBytes()
        Files.write(targetLocation.get().asFile.toPath(), content, StandardOpenOption.CREATE)
    }
}

