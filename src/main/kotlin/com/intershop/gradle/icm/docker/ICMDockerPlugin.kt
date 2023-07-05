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
import com.intershop.gradle.icm.docker.tasks.PushImages
import com.intershop.gradle.icm.docker.tasks.RemoveContainerByName
import com.intershop.gradle.icm.docker.tasks.ShowICMASConfig
import com.intershop.gradle.icm.docker.utils.Configuration
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.UnknownTaskException
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import com.intershop.gradle.icm.docker.utils.mail.TaskPreparer as MailSrvPreparer
import com.intershop.gradle.icm.docker.utils.mail.TaskPreparer as MailTaskPreparer
import com.intershop.gradle.icm.docker.utils.mssql.TaskPreparer as MSSQLPreparer
import com.intershop.gradle.icm.docker.utils.mssql.TaskPreparer as MSSQLTaskPreparer
import com.intershop.gradle.icm.docker.utils.network.TaskPreparer as NetworkPreparer
import com.intershop.gradle.icm.docker.utils.nginx.TaskPreparer as NginxTaskPreparer
import com.intershop.gradle.icm.docker.utils.oracle.TaskPreparer as OraclePreparer
import com.intershop.gradle.icm.docker.utils.oracle.TaskPreparer as OracleTaskPreparer
import com.intershop.gradle.icm.docker.utils.solrcloud.TaskPreparer as SolrCloudPreparer
import com.intershop.gradle.icm.docker.utils.solrcloud.TaskPreparer as SolrTaskPreparer
import com.intershop.gradle.icm.docker.utils.webserver.TaskPreparer as WebServerPreparer
import com.intershop.gradle.icm.docker.utils.webserver.TaskPreparer as WebTaskPreparer

/**
 * Main plugin class of the project plugin.
 */
open class ICMDockerPlugin : Plugin<Project> {

    companion object {
        const val BUILD_MAIN_IMAGE = "buildMainImage"
        const val BUILD_TEST_IMAGE = "buildTestImage"

        const val BUILD_IMAGES = "buildImages"
        const val PUSH_IMAGES = "pushImages"

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
            val nginxTasks = NginxTaskPreparer(project, networkTasks.createNetworkTask)

            try {
                tasks.named("dbPrepare").configure {
                    it.mustRunAfter(mssqlTasks.startTask)
                    it.mustRunAfter(oracleTasks.startTask)
                }
            } catch (ex: UnknownTaskException) {
                project.logger.info("No dbPrepare task available!")
            }

            tasks.register("containerClean") { task ->
                task.group = GROUP_CONTAINER
                task.description = "Removes all available container from Docker"

                task.dependsOn(networkTasks.removeNetworkTask,
                        mssqlTasks.removeTask,
                        mailSrvTask.removeTask,
                        webServerTasks.removeTask,
                        nginxTasks.removeTask,
                        oracleTasks.removeTask,
                        solrcloudPreparer.removeTask)
            }

            networkTasks.removeNetworkTask.configure {
                // ensure network is not removed before containers are removed
                it.mustRunAfter(tasks.withType(RemoveContainerByName::class.java))
            }

            createICMPropertiesGenTask(project)

            createImageTasks(project, extension)

            createEnvironmentTask(project, extension)
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
        val listStr = extension.developmentConfig.getConfigProperty(Configuration.CONTAINER_ENV_PROP)
        val list = listStr.split(",")

        project.tasks.register("startEnv") { task ->
            task.group = GROUP_CONTAINER
            task.description = "Start all container from Docker for the selected environment"
            if (list.contains("mail")) {
                task.dependsOn("start${MailTaskPreparer.extName}")
            }
            if (list.contains("webserver")) {
                task.dependsOn("start${WebTaskPreparer.TASK_EXT_SERVER}")
            }
            if (list.contains("solr")) {
                task.dependsOn("start${SolrTaskPreparer.TASK_EXT_SERVER}")
            }
            if (list.contains("mssql")) {
                task.dependsOn("start${MSSQLTaskPreparer.extName}")
            }
            if (list.contains("oracle")) {
                task.dependsOn("start${OracleTaskPreparer.extName}")
            }
            if (list.contains("nginx")) {
                task.dependsOn("start${NginxTaskPreparer.extName}")
            }
        }

        project.tasks.register("stopEnv") { task ->
            task.group = GROUP_CONTAINER
            task.description = "Stops all container from Docker for the selected environment"
            if (list.contains("mail")) {
                task.dependsOn("stop${MailTaskPreparer.extName}")
            }
            if (list.contains("webserver")) {
                task.dependsOn("stop${WebTaskPreparer.TASK_EXT_SERVER}")
            }
            if (list.contains("solr")) {
                task.dependsOn("stop${SolrTaskPreparer.TASK_EXT_SERVER}")
            }
            if (list.contains("mssql")) {
                task.dependsOn("stop${MSSQLTaskPreparer.extName}")
            }
            if (list.contains("oracle")) {
                task.dependsOn("stop${OracleTaskPreparer.extName}")
            }
            if (list.contains("nginx")) {
                task.dependsOn("stop${NginxTaskPreparer.extName}")
            }
        }
    }

    private fun createImageTasks(project: Project, extension: IntershopDockerExtension) {
        with(extension) {
            val mainImages = calculateImageTag(project, imageBuild, imageBuild.images.mainImage)
            val imgTask = createImageTask(
                    project, imageBuild, imageBuild.images.mainImage, mainImages, BUILD_MAIN_IMAGE)

            imgTask.configure { task ->
                task.description = "Creates the main image with an appserver."
            }
            val testImages = calculateImageTag(project, imageBuild, imageBuild.images.testImage)
            val testImgTask = createImageTask(
                    project, imageBuild, imageBuild.images.testImage, testImages, BUILD_TEST_IMAGE)

            testImgTask.configure { task ->
                task.description = "Creates the test image of an appserver."
                task.buildArgs.put("BASE_IMAGE", project.provider { imgTask.get().images.get().first() })
                task.dependsOn(imgTask)
            }

            project.tasks.register(BUILD_IMAGES) { task ->
                task.group = "build"
                task.description = "Build all configured images"
                task.dependsOn(imgTask, testImgTask)
            }

            val push = project.tasks.register(PUSH_IMAGES, PushImages::class.java) { task ->
                task.mustRunAfter(imgTask, testImgTask)
                task.images.addAll(mainImages)
                task.images.addAll(testImages)
            }

            try {
                val checkTask = project.tasks.named("check")
                push.configure { task ->
                    task.mustRunAfter(checkTask)
                }
            } catch (ex: UnknownTaskException) {
                project.logger.info("Task check is not available!")
            }
        }
    }

    private fun createImageTask(
            project: Project,
            imgBuild: ProjectConfiguration,
            imgConf: ImageConfiguration,
            images: Provider<List<String>>,
            taskName: String
    ): TaskProvider<BuildImage> =
            project.tasks.register(taskName, BuildImage::class.java) { buildImage ->
                buildImage.group = "icm image build"
                buildImage.enabled.set(imgConf.enabledProvider)
                buildImage.dockerfile.set(imgConf.dockerfileProvider)

                buildImage.srcFiles.from(imgConf.srcFiles)
                buildImage.dirname.set(imgConf.dockerBuildDirProvider)

                configureLabels(buildImage.labels, project, imgBuild)

                with(buildImage.labels) {
                    put("description", project.provider {
                        "${imgBuild.baseDescription.get()} - ${imgConf.description.get()}"
                    })
                }
                buildImage.images.set(images)
            }

    private fun configureLabels(
            property: MapProperty<String, String>,
            project: Project,
            imageBuild: ProjectConfiguration,
    ) {
        property.put("license", imageBuild.licenseProvider)
        property.put("maintainer", imageBuild.maintainerProvider)
        property.put("created", imageBuild.createdProvider)
        property.put("version", project.provider { project.version.toString() })
    }

    private fun calculateImageTag(
            project: Project, prjconf: ProjectConfiguration,
            imgConf: ImageConfiguration,
    ): Provider<List<String>> =
            project.provider {
                val nameExt = imgConf.nameExtension.get()
                val nameComplete = if (nameExt.isNotEmpty()) {
                    "-$nameExt"
                } else {
                    ""
                }
                val tagList = imgConf.tags.get()
                val imageName = "${prjconf.baseImageName.get()}${nameComplete}"

                val rvList = if( tagList.isEmpty() ) {
                    mutableListOf("${imageName}:${project.version}")
                } else {
                    val tmpList = mutableListOf<String>()
                    tagList.distinct().forEach {
                        tmpList.add("${imageName}:${it}")
                    }
                    tmpList
                }

                rvList
            }
}
