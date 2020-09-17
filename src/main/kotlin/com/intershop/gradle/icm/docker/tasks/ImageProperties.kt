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

package com.intershop.gradle.icm.docker.tasks

import com.intershop.gradle.icm.docker.ICMDockerPlugin
import com.intershop.gradle.icm.docker.utils.BuildImageRegistry
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.services.BuildServiceRegistry
import org.gradle.api.services.internal.BuildServiceRegistryInternal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.util.PropertiesUtils
import java.nio.charset.Charset
import java.util.*
import javax.inject.Inject

open class ImageProperties @Inject constructor(objectFactory: ObjectFactory,
                                          projectLayout: ProjectLayout): DefaultTask() {

    companion object {
        const val IMAGE_PROPERTIES_FILE = "containerimages/image.properties"
    }

    @get:OutputFile
    val outputFile: RegularFileProperty = objectFactory.fileProperty()

    init {
        outputFile.convention(projectLayout.buildDirectory.file(IMAGE_PROPERTIES_FILE))
    }

    init {
        group = "icm image build"
        description = "Writes a property file with all pushed containers for further usage."

        outputs.upToDateWhen {
            false
        }

        onlyIf {
            project.hasProperty("runOnCI") &&
                    project.property("runOnCI") == "true"
        }
    }

    @TaskAction
    fun createProperties() {
        if(! outputFile.get().asFile.parentFile.exists()) {
            outputFile.get().asFile.parentFile.mkdirs()
        }

        val props = linkedMapOf<String,String>()
        var counter = 1

        val serviceRegistry = services.get(BuildServiceRegistryInternal::class.java)
        val buildService = getBuildService(serviceRegistry, ICMDockerPlugin.BUILD_IMG_REGISTRY)

        if(buildService != null) {
            buildService.images.forEach {
                props["image.${counter}"] = it

                val imageName = it.split(":")
                if(imageName.size > 1) {
                    props["image.${counter}.name"] = imageName[0]
                    props["image.tag"] = imageName[1]
                }
                counter++
            }
        } else {
            throw GradleException("Buildservice registry is not correct configured.")
        }

        val comment = "Built images"
        val propsObject = Properties()
        propsObject.putAll(props)
        try {
            PropertiesUtils.store(
                    propsObject,
                    outputFile.get().asFile,
                    comment,
                    Charset.forName("ISO_8859_1"),
                    "\n"
            )
        } finally {
            project.logger.debug("Write properties finished not correct.")
        }
    }

    private fun getBuildService(registry: BuildServiceRegistry, name: String): BuildImageRegistry? {
        val registration = registry.registrations.findByName(name)
            ?: throw GradleException ("Unable to find build service with name '$name'.")

        val buildservice = registration.getService().get()
        return if(buildservice is BuildImageRegistry) {
            buildservice
        } else {
            null
        }
    }
}
