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
import com.intershop.gradle.icm.docker.tasks.APullImage
import com.intershop.gradle.icm.docker.tasks.DBPrepareTask
import com.intershop.gradle.icm.docker.tasks.ISHUnitHTMLTestReportTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import java.io.File

/**
 * Provides methods to configure container related tasks.
 */
class ServerTaskPreparer(val project: Project, private val dockerExtension: IntershopDockerExtension) {

    companion object {
        const val TASK_PULL = "pullImage"
        const val TASK_CREATECONTAINER = "createContainer"
        const val TASK_STARTCONTAINER = "startContainer"
        const val TASK_REMOVE = "removeContainer"
        const val TASK_FINALIZECONTAINER = "finalizeContainer"

        const val SERVERLOGS = "serverlogs"
        const val SERVERLOGS_PATH = "server/logs"

        const val ISHUNITOUT = "ishunitout"
        const val ISHUNITOUT_PATH = "ishunitrunner/output"

        const val TASK_PREPARESERVER = "prepareServer"
        const val TASK_CREATESITES = "createSites"
        const val TASK_EXTRACARTRIDGES = "setupCartridges"
        const val TASK_CREATECONFIG = "createConfig"
        const val TASK_CREATECLUSTERID = "createClusterID"
        const val TASK_COPYLIBS = "copyLibs"

        const val CONTAINER_EXTENSION = "container"
    }

    private val addDirectories: Map<String, Provider<Directory>> by lazy {
        mapOf(
            SERVERLOGS to project.layout.buildDirectory.dir(SERVERLOGS_PATH),
            ISHUNITOUT to project.layout.buildDirectory.dir(ISHUNITOUT_PATH)
        )
    }

    /**
     * Creates base container.
     *
     * @param pullImage pull image task.
     */
    fun getBaseContainer(pullImage: APullImage): DockerCreateContainer {
        return with(project) {

            val dirprep = tasks.maybeCreate( "dirPreparer").apply {
                doLast {
                    addDirectories.forEach { (_, dir) ->
                        val file = dir.get().asFile
                        if(file.exists()) {
                            file.deleteRecursively()
                        }
                        dir.get().asFile.mkdirs()
                    }
                }
                dependsOn(pullImage)
            }

            val prepareServer = tasks.findByName(TASK_PREPARESERVER)
                ?: throw GradleException("This plugin requires a task ${TASK_PREPARESERVER}' !")

            tasks.maybeCreate(TASK_CREATECONTAINER, DockerCreateContainer::class.java).apply {
                attachStderr.set(true)
                attachStdout.set(true)

                targetImageId(pullImage.image)

                containerName.set("${project.name.toLowerCase()}-container")

                entrypoint.set(listOf("/intershop/bin/startAndWait.sh"))

                hostConfig.portBindings.set(listOf("5005:7746"))

                hostConfig.binds.set(transformVolumes( mutableMapOf(
                        getOutputPathFor(TASK_CREATESITES, "sites")
                                to "/intershop/sites" ,
                        dockerExtension.developmentConfig.licenseDirectory
                                to "/intershop/license",
                        addDirectories.getValue(SERVERLOGS).get().asFile.absolutePath
                                to "/intershop/logs",
                        addDirectories.getValue(ISHUNITOUT).get().asFile.absolutePath
                                to "/intershop/ishunitrunner/output",
                        project.projectDir.absolutePath
                                to "/intershop/project/cartridges",
                        getOutputPathFor(TASK_EXTRACARTRIDGES, "")
                                to "/intershop/project/extraCartridges",
                        getOutputPathFor(TASK_COPYLIBS, "")
                                to "/intershop/project/libs",
                        getOutputDirFor(TASK_CREATECLUSTERID).parent
                                to "/intershop/clusterid",
                        dockerExtension.developmentConfig.configDirectory
                                to "/intershop/conf",
                        getOutputPathFor(TASK_CREATECONFIG, "system-conf")
                                to "/intershop/system-conf"
                )))

                dependsOn(dirprep, prepareServer)

            }
        }
    }

    /**
     * Starts base container.
     *
     * @param container create container task.
     */
    fun getStartContainer(container: DockerCreateContainer): DockerStartContainer {
        return with(project) {
            tasks.maybeCreate(TASK_STARTCONTAINER, DockerStartContainer::class.java).apply {
                targetContainerId(container.containerId)
                dependsOn(container)
            }
        }
    }

    /**
     * Configures remove container.
     *
     * @param startContainer Task, that starts the base container.
     */
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

    /**
     * Return a configured dbinit task.
     *
     * @param containertask task that creates the container.
     */
    fun getDBPrepareTask(containertask: DockerCreateContainer): DBPrepareTask {
        return with(project) {
            tasks.maybeCreate(RunTaskPreparer.TASK_DBPREPARE, DBPrepareTask::class.java).apply {
                containerId.set(containertask.containerId)
            }
        }
    }

    /**
     * Returns a task to create a HTML report from tes results.
     */
    fun getISHUnitHTMLTestReportTask(): ISHUnitHTMLTestReportTask {
        return with(project) {
            tasks.maybeCreate(RunTaskPreparer.TASK_ISHUNIT_REPORT, ISHUnitHTMLTestReportTask::class.java)
        }
    }

    private fun getOutputDirFor(taskName: String): File {
        val task = project.tasks.findByName(taskName)
            ?: throw GradleException("Task name '${taskName}' not found in project.")

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

        volumes.forEach { (k, v) ->
            if(k.contains('\\')) {
                tv["//${k.replace('\\','/')}".replace(":", "")] = v
            } else {
                tv[k] = v
            }
        }

        return tv
    }
}
