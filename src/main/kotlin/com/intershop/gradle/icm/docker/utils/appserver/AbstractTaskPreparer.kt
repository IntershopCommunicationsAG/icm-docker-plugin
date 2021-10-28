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
package com.intershop.gradle.icm.docker.utils.appserver

import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.tasks.PrepareNetwork
import com.intershop.gradle.icm.docker.tasks.PullImage
import com.intershop.gradle.icm.docker.tasks.RemoveContainerByName
import com.intershop.gradle.icm.docker.tasks.StopExtraContainer
import com.intershop.gradle.icm.docker.utils.Configuration
import com.intershop.gradle.icm.docker.utils.Configuration.SITES_FOLDER_PATH
import com.intershop.gradle.icm.docker.utils.ContainerUtils
import com.intershop.gradle.icm.docker.utils.PortMapping
import com.intershop.gradle.icm.tasks.CollectLibraries
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import java.io.File

abstract class AbstractTaskPreparer(
        project: Project,
        networkTask: Provider<PrepareNetwork>,
) : com.intershop.gradle.icm.docker.utils.AbstractTaskPreparer(project, networkTask) {

    companion object {
        const val SERVER_READY_STRING = "Servlet engine successfully started"

        const val SERVERLOGS = "serverlogs"
        const val SERVERLOGS_PATH = "server/logs"

        const val ISHUNITOUT = "ishunitout"
        const val ISHUNITOUT_PATH = "ishunitrunner/output"

        const val TASK_PREPARESERVER = "prepareServer"
        const val TASK_EXTRACARTRIDGES = "setupCartridges"
        const val TASK_CREATECONFIG = "createConfig"
        const val TASK_CREATECLUSTERID = "createClusterID"
    }

    override fun getImage(): Provider<String> = extension.images.icmbase

    fun initAppTasks() {
        project.tasks.register("pull${getExtensionName()}", PullImage::class.java) { task ->
            task.group = "icm container ${getContainerExt()}"
            task.description = "Pull image from registry"
            task.image.set(getImage())
        }

        project.tasks.register("stop${getExtensionName()}", StopExtraContainer::class.java) { task ->
            task.group = "icm container ${getContainerExt()}"
            task.description = "Stop running container"
            task.containerName.set("${extension.containerPrefix}-${getContainerExt()}")
        }

        project.tasks.register("remove${getExtensionName()}", RemoveContainerByName::class.java) { task ->
            task.group = "icm container ${getContainerExt()}"
            task.description = "Remove container from Docker"

            task.containerName.set("${extension.containerPrefix}-${getContainerExt()}")
        }
    }

    val prepareServer: TaskProvider<Task> by lazy {
        project.tasks.named(TASK_PREPARESERVER)
    }

    private val addDirectories: Map<String, Provider<Directory>> by lazy {
        mapOf(
            SERVERLOGS to project.layout.buildDirectory.dir(SERVERLOGS_PATH),
            ISHUNITOUT to project.layout.buildDirectory.dir(ISHUNITOUT_PATH)
        )
    }

    protected fun getServerVolumes(): Map<String,String> {
        addDirectories.forEach { _, path ->
            path.get().asFile.mkdirs()
        }

        prepareSitesFolder(project, extension)

        val volumes = mutableMapOf(
                extension.developmentConfig.getConfigProperty(SITES_FOLDER_PATH,
                        project.layout.buildDirectory.dir("sites_folder").get().asFile.absolutePath)
                        to "/intershop/sites",
                File(extension.developmentConfig.licenseDirectory).absolutePath
                        to "/intershop/license",
                addDirectories.getValue(SERVERLOGS).get().asFile.absolutePath
                        to "/intershop/logs",
                addDirectories.getValue(ISHUNITOUT).get().asFile.absolutePath
                        to "/intershop/ishunitrunner/output",
                project.projectDir.absolutePath
                        to "/intershop/customizations/${extension.containerPrefix}/cartridges",
                getOutputPathFor(TASK_EXTRACARTRIDGES, "")
                        to "/intershop/customizations/additional-dependencies/cartridges",
                getOutputPathFor(TASK_CREATECLUSTERID, "")
                        to "/intershop/clusterid",
                File(extension.developmentConfig.configDirectory).absolutePath
                        to "/intershop/conf",
                getOutputPathFor(TASK_CREATECONFIG, "system-conf")
                        to "/intershop/system-conf"
        )
        getOutputDirFor(CollectLibraries.DEFAULT_NAME).listFiles { file -> file.isDirectory }?.forEach { dir ->
            volumes[dir.absolutePath] = "/intershop/customizations/${extension.containerPrefix}-${dir.name}-libs/lib"
        }

        return ContainerUtils.transformVolumes(volumes)
    }

    /**
     * TODO SKR make configurable
     */
    protected open fun getPortMappings(): Set<PortMapping> =
        setOf(PortMapping(7743, 7743), PortMapping(7746, 7746),
                PortMapping(7747, 7747))


    private fun prepareSitesFolder(project: Project, extension: IntershopDockerExtension) {
        with(project) {
            val sitesFolderPath = extension.developmentConfig.getConfigProperty(
                Configuration.SITES_FOLDER_PATH, ""
            )

            val defaultSitesFolder =
                project.layout.buildDirectory.dir("sites_folder").forUseAtConfigurationTime().get().asFile

            if (sitesFolderPath.isEmpty()) {
                logger.warn(
                    "There is no configuration for the sites folder. Check '{}' in your icm.properties! \n" +
                            "The default '{}' value will be used!",
                    Configuration.SITES_FOLDER_PATH,
                    defaultSitesFolder.path
                )

                if (! defaultSitesFolder.exists()) {
                    if (! defaultSitesFolder.mkdirs()) {
                        logger.error(
                            "It was not possible to create the sites folder '{}'!",
                            defaultSitesFolder.path
                        )
                        throw GradleException(
                            "It was not possible to create the sites folder '{" +
                                    defaultSitesFolder.path + "'!"
                        )
                    }
                } else {
                    logger.warn(
                        "The sites folder exists and will be used '{}'!",
                        defaultSitesFolder.path
                    )
                }
            } else {
                val sitesFolder = File(sitesFolderPath)

                if (sitesFolder.exists() && sitesFolder.canWrite()) {
                    logger.warn("The sites folder exists and can be used '{}'!", sitesFolder.path)
                } else {
                    if (!sitesFolder.canWrite()) {
                        logger.warn(
                            "The sites folder exists, but it is not possible to write '{}'!",
                            sitesFolder.path
                        )
                    }
                    if (sitesFolder.mkdirs()) {
                        logger.warn(
                            "The sites folder does not exist, but it is was possible to create '{}'!",
                            sitesFolder.path
                        )
                    } else {
                        logger.error(
                            "The sites folder does not exist and it is was possible to create '{}'!",
                            sitesFolder.path
                        )
                        throw GradleException(
                            "It was not possible to create the sites folder '{" +
                                    sitesFolder.path + "'!"
                        )
                    }
                }
            }
        }
    }

    private fun getOutputDirFor(taskName: String): File {
        try {
            val task = project.tasks.named(taskName)
            return task.get().outputs.files.first()
        } catch (ex: UnknownTaskException) {
            throw GradleException("Task name '${taskName}' not found in project.")
        }
    }

    private fun getOutputPathFor(taskName: String, path: String): String {
        return if(path.isNotEmpty()) {
            File(getOutputDirFor(taskName), path).absolutePath
        } else {
            getOutputDirFor(taskName).absolutePath
        }
    }
}
