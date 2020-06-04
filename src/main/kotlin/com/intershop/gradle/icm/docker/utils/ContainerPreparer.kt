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
package com.intershop.gradle.icm.docker.utils

import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer
import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.tasks.PullImage
import com.intershop.gradle.icm.docker.tasks.RemoveContainerByName
import org.gradle.api.Project
import com.intershop.gradle.icm.extension.IntershopExtension
import com.intershop.gradle.icm.project.TaskConfCopyLib
import com.intershop.gradle.icm.project.TaskName
import com.intershop.gradle.icm.tasks.CreateClusterID
import org.gradle.api.GradleException
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import java.io.File

class ContainerPreparer(val project: Project, val dockerExtension: IntershopDockerExtension) {

    companion object {
        const val TASK_PULLBASEIMAGE = "pullImage"
        const val TASK_CREATECONTAINER = "createContainer"
        const val TASK_STARTCONTAINER = "startContainer"
        const val TASK_REMOVECONTAINER = "removeContainer"
        const val TASK_FINALIZECONTAINER = "finalizeContainer"

        const val SERVERLOGS = "serverlogs"
        const val SERVERLOGS_PATH = "server/logs"

        const val ISHUNITOUT = "ishunitout"
        const val ISHUNITOUT_PATH = "ishunitrunner/output"


    }

    val addDirectories: Map<String, Provider<Directory>> by lazy {
        mapOf(
            SERVERLOGS to project.layout.buildDirectory.dir(SERVERLOGS_PATH),
            ISHUNITOUT to project.layout.buildDirectory.dir(ISHUNITOUT_PATH)
        )
    }

    fun getPullImage(): PullImage {
        return with(project) {
            tasks.maybeCreate(
                    TASK_PULLBASEIMAGE,
                    PullImage::class.java).apply {
                this.image.set(dockerExtension.images.icmbase)
            }
        }
    }

    fun getBaseContainer(pullImage: PullImage): DockerCreateContainer {
        return with(project) {

            val dirprep = tasks.maybeCreate( "dirPreparer").apply {
                doLast {
                    addDirectories.forEach { _, dir ->
                        val file = dir.get().asFile
                        if(file.exists()) {
                            file.deleteRecursively()
                        }
                        dir.get().asFile.mkdirs()
                    }
                }
                dependsOn(pullImage)
            }

            val ishExtension = extensions.findByType(IntershopExtension::class.java)
                ?: throw GradleException("This plugin requires the plugin 'com.intershop.gradle.icm.project'!")

            val prepareServer = tasks.findByName(TaskName.DEVELOPMENT.prepare())
                ?: throw GradleException("This plugin requires the plugin 'com.intershop.gradle.icm.project'!")

            tasks.maybeCreate(TASK_CREATECONTAINER, DockerCreateContainer::class.java).apply {
                attachStderr.set(true)
                attachStdout.set(true)

                targetImageId(pullImage.image)

                containerName.set("${project.name.toLowerCase()}-container")

                entrypoint.set(listOf("/intershop/bin/startAndWait.sh"))

                hostConfig.binds.set(transformVolumes( mutableMapOf(
                        getOutputPathFor(TaskName.DEVELOPMENT.sites(), "sites")
                                to "/intershop/sites" ,
                        ishExtension.developmentConfig.licenseDirectory
                                to "/intershop/license",
                        addDirectories.get(SERVERLOGS)!!.get().asFile.absolutePath
                                to "/intershop/logs",
                        addDirectories.get(ISHUNITOUT)!!.get().asFile.absolutePath
                                to "/intershop/ishunitrunner/output",
                        project.projectDir.absolutePath
                                to "/intershop/project/cartridges",
                        getOutputPathFor(TaskName.DEVELOPMENT.cartridges(), "")
                                to "/intershop/project/extraCartridges",
                        getOutputPathFor(TaskConfCopyLib.DEVELOPMENT.taskname(), "")
                                to "/intershop/project/libs",
                        getOutputDirFor(CreateClusterID.DEFAULT_NAME).parent
                                to "/intershop/clusterid",
                        ishExtension.developmentConfig.configDirectory
                                to "/intershop/conf",
                        getOutputPathFor(TaskName.DEVELOPMENT.config(), "system-conf")
                                to "/intershop/system-conf"
                )))

                dependsOn(dirprep, prepareServer)

            }
        }
    }

    fun getStartContainer(container: DockerCreateContainer): DockerStartContainer {
        return with(project) {
            tasks.maybeCreate(TASK_STARTCONTAINER, DockerStartContainer::class.java).apply {
                targetContainerId(container.containerId)
                dependsOn(container)
            }
        }
    }

    fun getRemoveContainerByName(): RemoveContainerByName {
        return with(project) {
            tasks.maybeCreate(TASK_REMOVECONTAINER, RemoveContainerByName::class.java).apply {
                containerName.set("${project.name.toLowerCase()}-container")
            }
        }
    }

    fun getFinalizeContainer(startContainer: DockerStartContainer): DockerRemoveContainer {
        return with(project) {
            tasks.maybeCreate(TASK_FINALIZECONTAINER, DockerRemoveContainer::class.java)
                    .apply {
                        removeVolumes.set(true)
                        force.set(true)
                        targetContainerId(startContainer.containerId)
                    }
        }
    }

    private fun getOutputDirFor(taskName: String): File {
        val task = project.tasks.findByName(taskName)
            ?: throw GradleException("Task name '${taskName}' not found in project. Please check version of plugin 'com.intershop.gradle.icm.project'.")

        return task.outputs.files.first()
    }

    private fun getOutputPathFor(taskName: String, path: String): String {
        return if(path.isNotEmpty()) {
            File(getOutputDirFor(taskName), path).absolutePath
        } else {
            getOutputDirFor(taskName).absolutePath
        }
    }

    private fun transformVolumes(volumes: Map<String,String>) : Map<String, String> {
        val tv = mutableMapOf<String, String>()

        volumes.forEach { k, v ->
            if(k.contains('\\')) {
                tv.put("//${k.replace('\\','/')}".replace(":", ""), v)
            } else {
                tv.put(k, v)
            }
        }

        return tv
    }
}
