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

import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.extension.readme.push.ImageConfiguration
import com.intershop.gradle.icm.docker.tasks.PullExtraImage
import com.intershop.gradle.icm.docker.tasks.readmepush.CreateToolContainer
import com.intershop.gradle.icm.docker.tasks.readmepush.LogToolContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

/**
 * Main plugin class of the project plugin.
 */
open class ICMDockerReadmePushPlugin : Plugin<Project> {

    /**
     * Main method of a plugin.
     *
     * @param project target project
     */
    override fun apply(project: Project) {
        with(project) project@{
            if (project.rootProject == this) {
                logger.info("ICM Docker README Push plugin for projects will be initialized")

                val extension = extensions.findByType(
                    IntershopDockerExtension::class.java
                ) ?: extensions.create("intershop_docker", IntershopDockerExtension::class.java, project)

                val pullImg = project.tasks.register("pullReadmePush", PullExtraImage::class.java) { task ->
                    task.group = "icm container readme push"
                    task.description = "Pull image from registry for pushing readme"
                    task.image.set(extension.readmePush.toolImage)
                }

                with(extension.readmePush) {
                    val createContainerAS = createContainerTask(this@project, "AS",
                        this.readmeBaseDir, this.baseImageName, this.images.mainImage, pullImg)
                    val runContainerAS = createRunTask(this@project, "AS",
                        this.images.mainImage, createContainerAS)
                    val logContainerAS = createLogTask(this@project, "AS",
                        this.images.mainImage, runContainerAS)

                    val createContainerTest = createContainerTask(this@project, "Test",
                        this.readmeBaseDir, this.baseImageName, this.images.testImage, pullImg)
                    val runContainerTest = createRunTask(this@project, "Test",
                        this.images.testImage, createContainerTest)
                    val logContainerTest = createLogTask(this@project, "Test",
                        this.images.testImage, runContainerTest)

                    val createContainerInit = createContainerTask(this@project, "Init",
                        this.readmeBaseDir, this.baseImageName, this.images.initImage, pullImg)
                    val runContainerInit = createRunTask(this@project, "Init",
                        this.images.initImage, createContainerInit)
                    val logContainerInit = createLogTask(this@project, "Test",
                        this.images.initImage, runContainerInit)

                    val createContainerTestInit = createContainerTask(this@project, "TestInit",
                        this.readmeBaseDir, this.baseImageName, this.images.initTestImage, pullImg)
                    val runContainerTestInit = createRunTask(this@project, "Init",
                        this.images.initTestImage, createContainerTestInit)
                    val logContainerInitTest = createLogTask(this@project, "Test",
                        this.images.mainImage, runContainerTestInit)

                    tasks.register("pushReadme") { task ->
                        task.group =  "icm container readme push"
                        task.description = "Push readme for all containers"

                        task.dependsOn(logContainerAS, logContainerTest, logContainerInit, logContainerInitTest)
                    }
                }


            }
        }
    }

    private fun createContainerTask(project: Project,
                              ext: String,
                              baseDir: Property<String>,
                              baseImg: Property<String>,
                              conf: ImageConfiguration,
                              pullTask: Provider<PullExtraImage>): Provider<CreateToolContainer> =
        project.tasks.register("createReadmePush${ext}",
            CreateToolContainer::class.java) { task ->
            task.description = "Create image for pushing readme for ${ext} container"

            task.dependsOn(pullTask)
            task.targetImageId(project.provider { pullTask.get().image.get() })
            task.image.set(pullTask.get().image)

            task.envVars.put("DOCKER_USER", project.property("regUserName").toString())
            task.envVars.put("DOCKER_PASS", project.property("regUserPassword").toString())

            task.hostConfig.binds.set(
                mapOf(
                    project.layout.projectDirectory.dir("${baseDir}/${conf.nameExtension}").asFile.absolutePath
                            to "/myvol"
                )
            )
            task.cmd.addAll(
                listOf("--file", "/myvol/${conf.filename}", "--debug", "${baseImg}-${conf.nameExtension}")
            )

            task.containerName.set("pushReadme-${conf.nameExtension}")

            task.onlyIf {
                val returnValue = conf.enabled.getOrElse(false)
                if (!returnValue) {
                    project.logger.quiet("Task {} skipped, because it is not enabled.")
                }
                val runOnCICheck = project.hasProperty("runOnCI") &&
                        project.property("runOnCI") == "true"
                if (!runOnCICheck) {
                    project.logger.quiet("Task {} skipped, because runOnCI is false or not configured.")
                }
                runOnCICheck && returnValue
            }
        }


    private fun createRunTask(project: Project,
                                ext: String,
                                conf: ImageConfiguration,
                                containerTask: Provider<CreateToolContainer>) : Provider<DockerStartContainer> =
        project.tasks.register("runReadmePush${ext}", DockerStartContainer::class.java) { task ->
            task.group =  "icm container readme push"
            task.description = "Run container for pushing readme for ${ext} container"

            task.dependsOn(containerTask)
            task.containerId.set( containerTask.get().containerId )

            task.onlyIf {
                val returnValue = conf.enabled.getOrElse(false)
                if(! returnValue) {
                    project.logger.quiet("Task {} skipped, because it is not enabled.")
                }
                val runOnCICheck = project.hasProperty("runOnCI") &&
                        project.property("runOnCI") == "true"
                if (!runOnCICheck) {
                    project.logger.quiet("Task {} skipped, because runOnCI is false or not configured.")
                }
                runOnCICheck && returnValue
            }
        }
        
        private fun createLogTask(project: Project,
                                  ext: String,
                                  conf: ImageConfiguration,
                                  runTask: Provider<DockerStartContainer>) =
            project.tasks.register("logReadmePush${ext}", LogToolContainer::class.java) { task ->
                task.group = "icm container readme push"
                task.description = "Log container output for pushing readme for ${ext} container"

                task.targetContainerId(runTask.get().containerId)

                task.onlyIf {
                    val returnValue = conf.enabled.getOrElse(false)
                    if (!returnValue) {
                        project.logger.quiet("Task {} skipped, because it is not enabled.")
                    }
                    val runOnCICheck = project.hasProperty("runOnCI") &&
                            project.property("runOnCI") == "true"
                    if (!runOnCICheck) {
                        project.logger.quiet("Task {} skipped, because runOnCI is false or not configured.")
                    }
                    runOnCICheck && returnValue
                }
            }
}
