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

import com.bmuschko.gradle.docker.DockerRemoteApiPlugin
import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.extension.image.build.ImageConfiguration
import com.intershop.gradle.icm.docker.extension.image.build.ProjectConfiguration
import com.intershop.gradle.icm.docker.tasks.BuildImage
import com.intershop.gradle.icm.docker.tasks.ImageProperties
import com.intershop.gradle.icm.docker.tasks.PushImages
import com.intershop.gradle.icm.docker.utils.BuildImageRegistry
import com.intershop.gradle.icm.docker.utils.webserver.TaskPreparer as WebServerPreparer
import com.intershop.gradle.icm.docker.utils.mail.TaskPreparer as MailSrvPreparer
import com.intershop.gradle.icm.docker.utils.mssql.TaskPreparer as MSSQLPreparer
import com.intershop.gradle.icm.docker.utils.network.TaskPreparer as NetworkPreparer
import com.intershop.gradle.icm.docker.utils.oracle.TaskPreparer as OraclePreparer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.UnknownTaskException
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

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

        const val BUILD_IMG_REGISTRY = "ishBuildImageRegistry"

        const val GROUP_CONTAINER = "icm container"
        const val GROUP_SERVERBUILD = "icm server build"
    }

    /**
     * Main method of a plugin.
     *
     * @param project target project
     */
    override fun apply(project: Project) {
        with(project.rootProject) {

            logger.info("ICM Docker build plugin will be initialized")
            val extension = extensions.findByType(
                    IntershopDockerExtension::class.java
            ) ?: extensions.create("intershop_docker", IntershopDockerExtension::class.java, project)

            plugins.apply(DockerRemoteApiPlugin::class.java)

            val networkTasks = NetworkPreparer(project, extension)

            val mssqlTasks = MSSQLPreparer(project, networkTasks.createNetworkTask)
            val oracleTasks = OraclePreparer(project, networkTasks.createNetworkTask)
            val mailSrvTask = MailSrvPreparer(project, networkTasks.createNetworkTask)

            val webServerTasks = WebServerPreparer(project, networkTasks)

            try {
                tasks.named("dbPrepare").configure {
                    it.mustRunAfter(mssqlTasks.startTask)
                    it.mustRunAfter(oracleTasks.startTask)
                }
            } catch(ex: UnknownTaskException) {
                project.logger.info("No dbPrepare task available!")
            }

            val ccTask = tasks.register("containerClean") {task ->
                task.group = GROUP_CONTAINER
                task.description = "Removes all available container from Docker"

                task.dependsOn( networkTasks.removeNetworkTask,
                                mssqlTasks.removeTask,
                                mailSrvTask.removeTask,
                                webServerTasks.removeTask,
                                oracleTasks.removeTask)
            }

            try {
                project.tasks.named("clean").configure {
                    it.dependsOn(   networkTasks.removeNetworkTask,
                                    mssqlTasks.removeTask,
                                    mailSrvTask.removeTask,
                                    webServerTasks.removeTask,
                                    oracleTasks.removeTask)
                }

                networkTasks.removeNetworkTask.configure {
                    it.mustRunAfter(mssqlTasks.removeTask,
                        mailSrvTask.removeTask,
                        webServerTasks.removeTask,
                        oracleTasks.removeTask)
                }
            } catch(ex: UnknownTaskException) {
                project.logger.quiet("Task clean is not available.")
            }

            gradle.sharedServices.registerIfAbsent(BUILD_IMG_REGISTRY, BuildImageRegistry::class.java) { }

            createImageTasks(project, extension)

            try {
                tasks.named("publish").configure {
                    it.finalizedBy(ccTask)
                }
            } catch(ex: UnknownTaskException) {
                logger.quiet("Publish task is not available.")
            }
        }
    }


    private fun createImageTasks(project: Project, extension: IntershopDockerExtension) {
        with(extension) {
            val imgTask = createImageTask(
                    project, images.icmsetup, imageBuild, imageBuild.images.mainImage, BUILD_MAIN_IMAGE)

            imgTask.configure { task ->
                task.description = "Creates the main image with an appserver."
                configureLables(task.labels, project, imageBuild)
            }

            val initImgTask = createImageTask(
                project, images.icmsetup, imageBuild, imageBuild.images.initImage, BUILD_INIT_IMAGE)

            initImgTask.configure { task ->
                task.description = "Creates the main init image for initialization of an appserver."
                configureLables(task.labels, project, imageBuild)
            }

            val testImgTask = createImageTask(
                    project, images.icmsetup, imageBuild, imageBuild.images.testImage, BUILD_TEST_IMAGE)

            testImgTask.configure { task ->
                task.description = "Creates the test image of an appserver."
                task.buildArgs.put( "BASE_IMAGE", project.provider { imgTask.get().images.get().first() })
                task.dependsOn(imgTask)
            }

            val initTestImgTask = createImageTask(
                    project, images.icmsetup, imageBuild, imageBuild.images.initTestImage, BUILD_INIT_TEST_IMAGE)

            initTestImgTask.configure { task ->
                task.description = "Creates the init test image for initialization of an test appserver."
                task.buildArgs.put( "BASE_IMAGE", project.provider { initImgTask.get().images.get().first() })
                task.dependsOn(initImgTask)
            }

            project.tasks.register(BUILD_IMAGES) { task ->
                task.group = "build"
                task.description = "Build all configured images"
                task.dependsOn(imgTask, initImgTask, testImgTask, initTestImgTask)
            }

            val push = project.tasks.register(PUSH_IMAGES, PushImages::class.java) { task ->
                task.dependsOn(imgTask, initImgTask, testImgTask, initTestImgTask)
            }

            try {
                val checkTask = project.tasks.named("check")
                push.configure { task ->
                    task.mustRunAfter(checkTask)
                }
            } catch (ex: UnknownTaskException) {
                project.logger.info("Task check is not available!")
            }

            project.tasks.register(WRITE_IMAGE_PROPERTIES, ImageProperties::class.java) { task ->
                task.dependsOn(push)
            }
        }
    }

    private fun createImageTask(project: Project,
                                setupImg: Property<String>,
                                imgBuild: ProjectConfiguration,
                                imgConf: ImageConfiguration,
                                taskName: String): TaskProvider<BuildImage> =
            project.tasks.register(taskName, BuildImage::class.java ) { buildImage ->
                buildImage.group = "icm image build"
                buildImage.enabled.set(imgConf.enabledProvider)
                buildImage.dockerfile.set(imgConf.dockerfileProvider)

                buildImage.srcFiles.from(imgConf.srcFiles)
                buildImage.dirname.set(imgConf.dockerBuildDirProvider)

                with(buildImage.labels) {
                    put("description", project.provider {
                        "${imgBuild.baseDescription.get()} - ${imgConf.description.get()}"
                    })
                }
                buildImage.buildArgs.put( "SETUP_IMAGE", setupImg )
                buildImage.images.set( calculateImageTag(project, imgBuild, imgConf) )
            }

    private fun configureLables(property: MapProperty<String,String>,
                                project: Project,
                                imageBuild: ProjectConfiguration) {
        property.put("license", imageBuild.licenseProvider)
        property.put("maintainer", imageBuild.maintainerProvider)
        property.put("created", imageBuild.createdProvider)
        property.put("version", project.provider { project.version.toString() })
    }

    private fun calculateImageTag(project: Project, prjconf: ProjectConfiguration,
                                  imgConf: ImageConfiguration): Provider<List<String>> =
            project.provider {
                val nameExt = imgConf.nameExtension.get()
                val nameComplete = if(nameExt.isNotEmpty()) { "-$nameExt" } else { "" }
                mutableListOf("${prjconf.baseImageName.get()}${nameComplete}:${project.version}")
            }

}
