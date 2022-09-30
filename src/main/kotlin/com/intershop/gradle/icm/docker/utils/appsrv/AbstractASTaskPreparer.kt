/*
 * Copyright 2022 Intershop Communications AG.
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
package com.intershop.gradle.icm.docker.utils.appsrv

import com.intershop.gradle.icm.docker.tasks.PrepareNetwork
import com.intershop.gradle.icm.docker.tasks.PullImage
import com.intershop.gradle.icm.docker.tasks.RemoveContainerByName
import com.intershop.gradle.icm.docker.tasks.StartExtraContainer
import com.intershop.gradle.icm.docker.tasks.StopExtraContainer
import com.intershop.gradle.icm.docker.utils.AbstractTaskPreparer
import com.intershop.gradle.icm.docker.utils.Configuration
import com.intershop.gradle.icm.docker.utils.ContainerUtils
import com.intershop.gradle.icm.docker.utils.OS
import com.intershop.gradle.icm.docker.utils.PortMapping
import com.intershop.gradle.icm.tasks.CopyLibraries
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.util.concurrent.TimeUnit

abstract class AbstractASTaskPreparer(
    project: Project,
    networkTask: Provider<PrepareNetwork>) : AbstractTaskPreparer(project, networkTask) {

    init {
        initAppTasks()
    }

    val prepareServer: TaskProvider<Task> by lazy {
        project.tasks.named(TaskPreparer.TASK_PREPARESERVER)
    }
    private fun initAppTasks() {
        project.tasks.register("pull${getExtensionName()}", PullImage::class.java) { task ->
            task.group = "icm container ${getContainerExt()}"
            task.description = "Pull image from registry"
            task.image.set(getImage())
        }

        project.tasks.register("stop${getExtensionName()}", StopExtraContainer::class.java) { task ->
            task.group = "icm container ${getContainerExt()}"
            task.description = "Stop running container"
            task.containerName.set(getContainerName())
        }

        project.tasks.register("remove${getExtensionName()}", RemoveContainerByName::class.java) { task ->
            task.group = "icm container ${getContainerExt()}"
            task.description = "Remove container from Docker"

            task.containerName.set(getContainerName())
        }
    }

    /**
     * Determines the port mappings using
     * [com.intershop.gradle.icm.docker.extension.DevelopmentConfiguration.asPortConfiguration]
     */
    protected fun getPortMappings(): Set<PortMapping> =
        with(dockerExtension.developmentConfig.asPortConfiguration){
            setOf( servletEngine.get(), debug.get(), jmx.get() )
        }

    protected fun getServerVolumes(task: Task, customization: Boolean): Map<String,String> {
        addDirectories.forEach { (_, path) ->
            path.get().asFile.mkdirs()
        }

        prepareSitesFolder()

        val volumes = mutableMapOf(
            dockerExtension.developmentConfig.getConfigProperty(
                Configuration.SITES_FOLDER_PATH,
                project.layout.buildDirectory.dir("sites_folder").get().asFile.absolutePath)
                    to "/intershop/sites",
            File(dockerExtension.developmentConfig.licenseDirectory).absolutePath
                    to "/intershop/license",
            addDirectories.getValue(TaskPreparer.SERVERLOGS).get().asFile.absolutePath
                    to "/intershop/logs",
            addDirectories.getValue(TaskPreparer.ISHUNITOUT).get().asFile.absolutePath
                    to "/intershop/ishunitrunner/output",
            project.projectDir.absolutePath
                    to "/intershop/customizations/${dockerExtension.containerPrefix}/cartridges",
            "${dockerExtension.containerPrefix}-customizations"
                    to "/intershop/customizations"
        )

        if(customization) {
            volumes[getOutputPathFor(TaskPreparer.TASK_CREATECONFIG, "system-conf")] = "/intershop/system-conf"

            project.tasks.withType(CopyLibraries::class.java) {
                task.dependsOn(it)

                val dir = it.librariesDirectory.get().asFile
                volumes[dir.absolutePath] =
                    "/intershop/customizations/${dockerExtension.containerPrefix}-${dir.name}-libs/lib"
            }
        }
        return ContainerUtils.transformVolumes(volumes)
    }

    private fun getOutputPathFor(taskName: String, path: String): String {
        return if(path.isNotEmpty()) {
            File(getOutputDirFor(taskName), path).absolutePath
        } else {
            getOutputDirFor(taskName).absolutePath
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

    private val addDirectories: Map<String, Provider<Directory>> by lazy {
        mapOf(
            TaskPreparer.SERVERLOGS to project.layout.buildDirectory.dir(TaskPreparer.SERVERLOGS_PATH),
            TaskPreparer.ISHUNITOUT to project.layout.buildDirectory.dir(TaskPreparer.ISHUNITOUT_PATH)
        )
    }

    private fun prepareSitesFolder() {
        with(project) {
            val sitesFolderPath = dockerExtension.developmentConfig.getConfigProperty(
                Configuration.SITES_FOLDER_PATH, ""
            )

            val defaultSitesFolder =
                project.layout.buildDirectory.dir("sites_folder").get().asFile

            val sitesFolder : File
            if (sitesFolderPath.isEmpty()) {
                logger.warn(
                    "There is no configuration for the sites folder. Check '{}' in your icm.properties! \n" +
                            "The default '{}' value will be used!",
                    Configuration.SITES_FOLDER_PATH,
                    defaultSitesFolder.path
                )
                sitesFolder = defaultSitesFolder
            }
            else {
                sitesFolder = File(sitesFolderPath)
                logger.quiet("Using configured sites folder '{}'", sitesFolder.path)
            }

            if (sitesFolder.exists() && sitesFolder.canWrite()) {
                logger.quiet("The sites folder '{}' exists and can be used", sitesFolder.path)
            } else {
                if (sitesFolder.exists()){
                    if (!sitesFolder.canWrite()) {
                        throw GradleException("The sites folder '${sitesFolder.path}' exists, but is not writable!")
                    }
                } else {
                    logger.warn("The sites folder '{}' does not exist -> trying to create", sitesFolder.path)
                    if (!sitesFolder.mkdirs()) {
                        throw GradleException(
                            "The sites folder '${sitesFolder.path}' does not exist, but can not be " +
                                    "created!"
                        )
                    }
                    logger.quiet("Created sites folder '{}'", sitesFolder.path)
                }
            }

            // try to make sites folder accessible to other users especially 'intershop' (inside the container)
            val os = OS.bySystem()
            os?.run {
                if (this == OS.LINUX || this == OS.MAC){
                    // execute '/bin/sh -c chmod a+rwx <sitesFolder>', stdout/stderr are just redirected to gradle's
                    // stdout/stderr
                    val process = ProcessBuilder().command("/bin/sh", "-c", "chmod a+rwx '${sitesFolder.path}'").
                    inheritIO().start()
                    val isTimeout = !process.waitFor(5, TimeUnit.SECONDS)
                    if (isTimeout){
                        throw GradleException(
                            "Timed out while making the sites folder '${sitesFolder.path}' " +
                                    "accessible to everyone!"
                        )
                    }
                    val exitCode = process.exitValue()
                    if (exitCode != 0){
                        throw GradleException(
                            "Failed to make the sites folder '${sitesFolder.path}' accessible to " +
                                    "everyone (exitCode=$exitCode)!"
                        )
                    }
                }
            }
        }
    }

    val mailServerTaskProvider: Provider<StartExtraContainer>? by lazy {
        try {
            project.tasks.named(
                "start${com.intershop.gradle.icm.docker.utils.mail.TaskPreparer.extName}",
                StartExtraContainer::class.java
            )
        } catch (ex: UnknownTaskException) {
            project.logger.info("MailSrv tasks not found")
            null
        }
    }
}
