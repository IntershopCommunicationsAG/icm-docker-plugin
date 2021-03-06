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
import com.intershop.gradle.icm.docker.tasks.GenICMProperties
import com.intershop.gradle.icm.docker.tasks.ImageProperties
import com.intershop.gradle.icm.docker.tasks.PushImages
import com.intershop.gradle.icm.docker.tasks.ShowICMASConfig
import com.intershop.gradle.icm.docker.utils.BuildImageRegistry
import com.intershop.gradle.icm.docker.utils.Configuration
import com.intershop.gradle.icm.docker.utils.solrcloud.TaskPreparer as SolrCloudPreparer
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
import com.intershop.gradle.icm.docker.utils.mail.TaskPreparer as MailTaskPreparer
import com.intershop.gradle.icm.docker.utils.webserver.TaskPreparer as WebTaskPreparer
import com.intershop.gradle.icm.docker.utils.solrcloud.TaskPreparer as SolrTaskPreparer
import com.intershop.gradle.icm.docker.utils.mssql.TaskPreparer as MSSQLTaskPreparer
import com.intershop.gradle.icm.docker.utils.oracle.TaskPreparer as OracleTaskPreparer

/**
 * Main plugin class of the project plugin.
 */
open class ICMDockerPlugin: Plugin<Project> {

    companion object {
        const val BUILD_MAIN_IMAGE = "buildMainImage"
        const val BUILD_TEST_IMAGE = "buildTestImage"

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
            val solrcloudPreparer = SolrCloudPreparer(project, networkTasks)

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
                                oracleTasks.removeTask,
                                solrcloudPreparer.removeTask)
            }

            networkTasks.removeNetworkTask.configure {
                it.mustRunAfter(mssqlTasks.removeTask,
                    mailSrvTask.removeTask,
                    webServerTasks.removeTask,
                    oracleTasks.removeTask,
                    solrcloudPreparer.removeTask)
            }

            gradle.sharedServices.registerIfAbsent(BUILD_IMG_REGISTRY, BuildImageRegistry::class.java) { }

            createICMPropertiesGenTask(project)

            createImageTasks(project, extension)

            createEnvironmentTask(project, extension)

            try {
                tasks.named("publish").configure {
                    it.finalizedBy(ccTask)
                }
            } catch(ex: UnknownTaskException) {
                logger.info("Publish task is not available.")
            }
        }
    }

    private fun createICMPropertiesGenTask(project: Project) {
        project.tasks.register("generateICMProps", GenICMProperties::class.java).configure {
            it.group = "icm project setup"
            it.description = "Generates an icm properties file."
        }
        project.tasks.register("showICMASConfig", ShowICMASConfig::class.java).configure {
            it.group = "icm project setup"
            it.description = "Shows a special part of the configuration for local application server development"
        }
    }

    private fun createEnvironmentTask(project: Project, extension: IntershopDockerExtension) {
        val listStr = extension.developmentConfig.getConfigProperty(Configuration.COTAINER_ENV_PROP)
        val list = listStr.split(",")

        project.tasks.register("startEnv") { task ->
            task.group = GROUP_CONTAINER
            task.description = "Start all container from Docker for the selected environment"
            if(list.contains("mail")) {
                task.dependsOn("start${MailTaskPreparer.extName}")
            }
            if(list.contains("webserver")) {
                task.dependsOn("start${WebTaskPreparer.TASK_EXT_SERVER}")
            }
            if(list.contains("solr")) {
                task.dependsOn("start${SolrTaskPreparer.TASK_EXT_SERVER}")
            }
            if(list.contains("mssql")) {
                task.dependsOn("start${MSSQLTaskPreparer.extName}")
            }
            if(list.contains("oracle")) {
                task.dependsOn("start${OracleTaskPreparer.extName}")
            }
        }

        project.tasks.register("stopEnv") { task ->
            task.group = GROUP_CONTAINER
            task.description = "Stops all container from Docker for the selected environment"
            if(list.contains("mail")) {
                task.dependsOn("stop${MailTaskPreparer.extName}")
            }
            if(list.contains("webserver")) {
                task.dependsOn("stop${WebTaskPreparer.TASK_EXT_SERVER}")
            }
            if(list.contains("solr")) {
                task.dependsOn("stop${SolrTaskPreparer.TASK_EXT_SERVER}")
            }
            if(list.contains("mssql")) {
                task.dependsOn("stop${MSSQLTaskPreparer.extName}")
            }
            if(list.contains("oracle")) {
                task.dependsOn("stop${OracleTaskPreparer.extName}")
            }
        }
    }

    private fun createImageTasks(project: Project, extension: IntershopDockerExtension) {
        with(extension) {
            val imgTask = createImageTask(
                    project, images.icmsetup, imageBuild, imageBuild.images.mainImage, BUILD_MAIN_IMAGE)

            imgTask.configure { task ->
                task.description = "Creates the main image with an appserver."
            }

            val testImgTask = createImageTask(
                    project, images.icmsetup, imageBuild, imageBuild.images.testImage, BUILD_TEST_IMAGE)

            testImgTask.configure { task ->
                task.description = "Creates the test image of an appserver."
                task.buildArgs.put( "BASE_IMAGE", project.provider { imgTask.get().images.get().first() })
                task.dependsOn(imgTask)
            }

            project.tasks.register(BUILD_IMAGES) { task ->
                task.group = "build"
                task.description = "Build all configured images"
                task.dependsOn(imgTask, testImgTask)
            }

            val push = project.tasks.register(PUSH_IMAGES, PushImages::class.java) { task ->
                task.dependsOn(imgTask, testImgTask)
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

                configureLables(buildImage.labels, project, imgBuild)

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
