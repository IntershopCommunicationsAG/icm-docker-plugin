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

import com.avast.gradle.dockercompose.DockerComposePlugin
import com.bmuschko.gradle.docker.DockerRemoteApiPlugin
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.intershop.gradle.icm.docker.extension.ImageBuild
import com.intershop.gradle.icm.docker.extension.ImageConfiguration
import com.intershop.gradle.icm.docker.extension.Images
import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.tasks.BuildImage
import com.intershop.gradle.icm.docker.tasks.ImageProperties
import com.intershop.gradle.icm.docker.tasks.PushImages
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Main plugin class of the project plugin.
 */
open class ICMDockerPlugin: Plugin<Project> {

    companion object {
        const val BUILD_MAIN_IMAGE = "buildMainImage"
        const val BUILD_INIT_IMAGE = "buildInitImage"
        const val BUILD_TEST_IMAGE = "buildTestImage"
        const val BUILD_INIT_TEST_IMAGE = "buildInitTestImage"

        const val BUILD_IMAGES = "buildImages"
        const val PUSH_IMAGES = "pushImages"
        const val WRITE_IMAGE_PROPERTIES = "writeImageProperties"
    }

    /**
     * Main method of a plugin.
     *
     * @param project target project
     */
    override fun apply(project: Project) {
        with(project) {
            logger.info("ICM Docker build plugin will be initialized")
            val extension = extensions.findByType(
                    IntershopDockerExtension::class.java
            ) ?: extensions.create("intershop_docker", IntershopDockerExtension::class.java)

            plugins.apply(DockerComposePlugin::class.java)
            plugins.apply(DockerRemoteApiPlugin::class.java)

            createImageTasks(this, extension)
        }
    }

    private fun createImageTasks(project: Project, extension: IntershopDockerExtension) {
        val mainTask = project.tasks.maybeCreate(BUILD_IMAGES).apply {
            group = "build"
        }

        val pushImage = project.tasks.maybeCreate(PUSH_IMAGES, PushImages::class.java)
        val writeProperties = project.tasks.maybeCreate(WRITE_IMAGE_PROPERTIES, ImageProperties::class.java)

        val imgTask = createImageTask(project,
                extension.images,
                extension.imageBuild,
                extension.imageBuild.images.mainImage,
                BUILD_MAIN_IMAGE)

        val initImgTask = createImageTask(project,
                extension.images,
                extension.imageBuild,
                extension.imageBuild.images.initImage,
                BUILD_INIT_IMAGE)

        if(imgTask != null) {
            pushImage.images.addAll(imgTask.images.get())
            writeProperties.images.addAll(imgTask.images.get())
            val imgTestTask = createTestImageTask(project,
                    extension.images,
                    extension.imageBuild,
                    extension.imageBuild.images.testImage,
                    imgTask,
                    BUILD_TEST_IMAGE)

            if(imgTestTask != null) {
                pushImage.images.addAll(imgTestTask.images.get())
                writeProperties.images.addAll(imgTestTask.images.get())
                mainTask.dependsOn(imgTestTask)
            } else {
                mainTask.dependsOn(imgTask)
            }
        }

        if(initImgTask != null) {
            pushImage.images.addAll(initImgTask.images.get())
            writeProperties.images.addAll(initImgTask.images.get())
            val imgInitTestTask = createTestImageTask(project,
                    extension.images,
                    extension.imageBuild,
                    extension.imageBuild.images.testInitImage,
                    initImgTask,
                    BUILD_INIT_TEST_IMAGE)

            if(imgInitTestTask != null) {
                pushImage.images.addAll(imgInitTestTask.images.get())
                writeProperties.images.addAll(imgInitTestTask.images.get())
                mainTask.dependsOn(imgInitTestTask)
            } else {
                mainTask.dependsOn(initImgTask)
            }
        }

    }

    private fun createImageTask(project: Project,
                                imgs: Images,
                                imgBuild: ImageBuild,
                                imgConf: ImageConfiguration,
                                taskName: String): DockerBuildImage? {
        return if(imgConf.createImage.get()) {
            project.tasks.maybeCreate(taskName, BuildImage::class.java).apply {
                with(this.labels) {
                    put("license", imgBuild.license.get())
                    put("version", project.version.toString())
                    put("maintainer", imgBuild.maintainer.get())
                    put("description", "${imgBuild.baseDescription.get()} - ${imgConf.description.get()}")
                    put("created", imgBuild.created.get())
                }

                buildArgs.put( "SETUP_IMAGE", imgs.icmsetup.get() )

                val nameExt = imgConf.imageExtension.get()
                val nameComplete = if(nameExt.isNotEmpty()) { "- ${nameExt}" } else { "" }
                images.set(mutableListOf("${imgBuild.baseImageName.get()}${nameComplete}:${project.version}"))
            }
        } else {
            null
        }
    }

    private fun createTestImageTask(project: Project,
                                    imgs: Images,
                                    imgBuild: ImageBuild,
                                    imgConf: ImageConfiguration,
                                    imgTask: DockerBuildImage,
                                    taskName: String): DockerBuildImage? {
        return if(imgConf.createImage.get()) {
            project.tasks.maybeCreate(taskName, BuildImage::class.java).apply {
                with(this.labels) {
                    put("description", "${imgBuild.baseDescription.get()} - ${imgConf.description.get()}")
                }

                buildArgs.put( "SETUP_IMAGE", imgs.icmsetup.get() )
                buildArgs.put( "BASE_IMAGE", imgTask.images.get().first())

                val nameExt = imgConf.imageExtension.get()
                val nameComplete = if(nameExt.isNotEmpty()) { "- ${nameExt}" } else { "" }
                images.set(mutableListOf("${imgBuild.baseImageName.get()}${nameComplete}:${project.version}"))
                dependsOn(imgTask)
            }
        } else {
            null
        }
    }
}
