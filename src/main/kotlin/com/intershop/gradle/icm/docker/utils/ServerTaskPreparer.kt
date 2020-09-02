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
import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.tasks.APullImage
import com.intershop.gradle.icm.docker.tasks.CreateVolumes
import com.intershop.gradle.icm.docker.tasks.RemoveVolumes
import com.intershop.gradle.icm.docker.tasks.StartExtraContainerTask
import com.intershop.gradle.icm.docker.tasks.StartServerContainerTask
import com.intershop.gradle.icm.docker.utils.ContainerUtils.transformVolumes
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.extra
import java.io.File

class ServerTaskPreparer(private val project: Project,
                         private val dockerExtension: IntershopDockerExtension) {

    companion object {
        const val TASK_EXT_MSSQL = "MSSQL"
        const val TASK_EXT_MAIL = "MailSrv"
        const val TASK_EXT_WAA = "WAA"
        const val TASK_EXT_WA = "WA"
        const val TASK_EXT_SOLR = "Solr"
        const val TASK_EXT_ZK = "ZK"

        const val TASK_EXT_VOLUMES = "ICMWebVolumes"

        const val START_WEBSERVER = "startWebServer"
        const val STOP_WEBSERVER = "stopWebServer"

        const val START_SOLRCLOUD = "startSolrCloud"
        const val STOP_SOLRCLOUD = "stopSolrCloud"

        const val TASK_EXT_CONTAINER = "Container"
        const val TASK_EXT_AS = "AS"

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
    }

    private val taskPreparer : StandardTaskPreparer by lazy {
        StandardTaskPreparer(project)
    }

    private val addDirectories: Map<String, Provider<Directory>> by lazy {
        mapOf(
                SERVERLOGS to project.layout.buildDirectory.dir(SERVERLOGS_PATH),
                ISHUNITOUT to project.layout.buildDirectory.dir(ISHUNITOUT_PATH)
        )
    }

    fun createMSSQLServerTasks() {
        val containerExt = TASK_EXT_MSSQL.toLowerCase()
        taskPreparer.createBaseTasks(TASK_EXT_MSSQL, containerExt, dockerExtension.images.mssqldb)
        val imageTask = project.tasks.named("pull${TASK_EXT_MSSQL}", APullImage::class.java)

        project.tasks.register ("start${TASK_EXT_MSSQL}", StartExtraContainerTask::class.java) { task ->
            configureContainerTask(task, containerExt)
            task.description = "Starts an MSSQL server"
            task.targetImageId( project.provider { imageTask.get().image.get() } )

            with(dockerExtension.developmentConfig) {
                val port = getConfigProperty(
                    Configuration.DB_MSSQL_PORT,
                    Configuration.DB_MSSQL_PORT_VALUE)
                val containerPort = getConfigProperty(
                    Configuration.DB_MSSQL_CONTAINER_PORT,
                    Configuration.DB_MSSQL_CONTAINER_PORT_VALUE)

                task.hostConfig.portBindings.set(
                        listOf("${port}:${containerPort}"))
                task.envVars.set( mutableMapOf(
                        "ACCEPT_EULA" to
                                "Y",
                        "SA_PASSWORD" to
                                getConfigProperty(
                                    Configuration.DB_MSSQL_SA_PASSWORD,
                                    Configuration.DB_MSSQL_SA_PASSWORD_VALUE),
                        "MSSQL_PID" to
                                "Developer",
                        "RECREATEDB" to
                                getConfigProperty(
                                    Configuration.DB_MSSQL_RECREATE_DB,
                                    Configuration.DB_MSSQL_RECREATE_DB_VALUE),
                        "RECREATEUSER" to
                                getConfigProperty(
                                    Configuration.DB_MSSQL_RECREATE_USER,
                                    Configuration.DB_MSSQL_RECREATE_USER_VALUE),
                        "ICM_DB_NAME" to
                                getConfigProperty(
                                    Configuration.DB_MSSQL_DBNAME,
                                    Configuration.DB_MSSQL_DBNAME_VALUE),
                        "ICM_DB_USER" to
                                getConfigProperty(
                                    Configuration.DB_USER_NAME,
                                    Configuration.DB_USER_NAME_VALUE),
                        "ICM_DB_PASSWORD" to
                                getConfigProperty(
                                    Configuration.DB_USER_PASSWORD,
                                    Configuration.DB_USER_PASSWORD_VALUE)
                ))
            }

            task.dependsOn(imageTask)
        }
    }

    fun createMailServerTasks() {
        val containerExt = TASK_EXT_MAIL.toLowerCase()
        taskPreparer.createBaseTasks(TASK_EXT_MAIL, containerExt, dockerExtension.images.mailsrv)
        val imageTask = project.tasks.named("pull${TASK_EXT_MAIL}", APullImage::class.java)

        project.tasks.register ("start${TASK_EXT_MAIL}", StartExtraContainerTask::class.java) { task ->
            configureContainerTask(task, containerExt)
            task.description = "Starts an local mail server for testing"
            task.targetImageId( project.provider { imageTask.get().image.get() } )

            task.envVars.set(mutableMapOf(
                "MH_STORAGE" to "maildir",
                "MH_MAILDIR_PATH" to "/maildir"))

            task.hostConfig.portBindings.set(listOf("25:1025", "8025:8025"))
            task.hostConfig.binds.set( project.provider {
                transformVolumes(
                    mutableMapOf( project.layout.buildDirectory.dir("mailoutput").get().asFile.absolutePath
                            to "/maildir" )) })

            task.dependsOn(imageTask)
        }
    }

    fun createWebServerTasks() {
        val containerWAAExt = TASK_EXT_WAA.toLowerCase()
        val containerWAExt = TASK_EXT_WA.toLowerCase()

        taskPreparer.createBaseTasks(TASK_EXT_WAA, containerWAAExt, dockerExtension.images.webadapteragent)
        taskPreparer.createBaseTasks(TASK_EXT_WA, containerWAExt, dockerExtension.images.webadapter)

        val imageWAATask = project.tasks.named("pull${TASK_EXT_WAA}", APullImage::class.java)
        val imageWATask = project.tasks.named("pull${TASK_EXT_WA}", APullImage::class.java)

        val volumes = mapOf(
            "${project.name.toLowerCase()}-waproperties" to "/intershop/webadapter-conf",
            "${project.name.toLowerCase()}-pagecache" to "/intershop/pagecache",
            "${project.name.toLowerCase()}-walogs" to "/intershop/logs")

        val createVolumes =
            project.tasks.register("create${TASK_EXT_VOLUMES}", CreateVolumes::class.java) { task ->
                configureWebServerTasks(task, "Creates volumes in Docker")
                task.volumeNames.set( volumes.keys )
            }

        val removeVolumes =
            project.tasks.register("remove${TASK_EXT_VOLUMES}", RemoveVolumes::class.java) { task ->
                configureWebServerTasks(task, "Removes volumes from Docker")
                task.volumeNames.set( volumes.keys )
            }

        val startWA = project.tasks.register("start${TASK_EXT_WA}", StartExtraContainerTask::class.java) { task ->
            configureContainerTask(task, containerWAExt)
            configureWebServerTasks(task, "Start ICM WebServer with WebAdapter")
            task.targetImageId(project.provider { imageWATask.get().image.get() })

            with(dockerExtension.developmentConfig) {
                val httpPort = getConfigProperty(
                    Configuration.WS_HTTP_PORT,
                    Configuration.WS_HTTP_PORT_VALUE)
                val httpContainerPort = getConfigProperty(
                    Configuration.WS_CONTAINER_HTTP_PORT,
                    Configuration.WS_CONTAINER_HTTP_PORT_VALUE)
                val httpsPort = getConfigProperty(
                    Configuration.WS_HTTPS_PORT,
                    Configuration.WS_HTTPS_PORT_VALUE)
                val httpsContainerPort = getConfigProperty(
                    Configuration.WS_CONTAINER_HTTPS_PORT,
                    Configuration.WS_CONTAINER_HTTPS_PORT_VALUE)

                task.hostConfig.portBindings.set(
                    listOf("${httpPort}:${httpContainerPort}", "${httpsPort}:${httpsContainerPort}"))

                val asHttpPort = if(dockerExtension.developmentConfig.appserverAsContainer) {
                    getConfigProperty(
                        Configuration.AS_CONNECTOR_CONTAINER_PORT,
                        Configuration.AS_CONNECTOR_CONTAINER_PORT_VALUE)
                } else {
                    getConfigProperty(
                        Configuration.AS_CONNECTOR_PORT,
                        Configuration.AS_CONNECTOR_PORT_VALUE)
                }

                val asHostname = if(dockerExtension.developmentConfig.appserverAsContainer) {
                        taskPreparer.getContainerName(TASK_EXT_AS.toLowerCase())
                    } else {
                        getConfigProperty(
                            Configuration.AS_CONNECTOR_HOST,
                            Configuration.AS_CONNECTOR_HOST_VALUE)
                    }

                task.envVars.set(mutableMapOf(
                    "ICM_ICMSERVLETURLS" to "cs.url.0=http://${asHostname}:${asHttpPort}/servlet/ConfigurationServlet"))

                if(dockerExtension.developmentConfig.appserverAsContainer) {
                    task.hostConfig.links.add("${asHostname}:${asHostname}")
                }
            }

            task.hostConfig.binds.set( volumes )

            task.dependsOn(imageWATask, createVolumes)
        }

        val startWAA = project.tasks.register("start${TASK_EXT_WAA}", StartExtraContainerTask::class.java) { task ->
            configureContainerTask(task, containerWAAExt)
            configureWebServerTasks(task, "Start ICM WebAdapterAgent")
            task.targetImageId(project.provider { imageWAATask.get().image.get() })

            task.hostConfig.binds.set( volumes )

            task.dependsOn(imageWAATask, startWA, createVolumes)
        }

        val stopWAA = project.tasks.named("stop${TASK_EXT_WAA}")
        val stopWA = project.tasks.named("stop${TASK_EXT_WA}")

        project.tasks.register(START_WEBSERVER) {task ->
            configureWebServerTasks(task, "Start all components for ICM WebServer")
            task.dependsOn(startWA, startWAA)
        }

        project.tasks.register(STOP_WEBSERVER) {task ->
            configureWebServerTasks(task, "Stop all components for ICM WebServer")
            task.dependsOn(stopWA, stopWAA)
        }

        try {
            project.tasks.named("clean").configure { task ->
                task.dependsOn(removeVolumes)
            }
        } catch( ex: UnknownTaskException) {
            project.logger.info("Task clean is not available.")
        }
    }

    private fun configureWebServerTasks(task: Task, description: String) {
        task.group = "icm container webserver"
        task.description = description
    }

    fun createSolrServerTasks() {
        val containerSolrExt = TASK_EXT_SOLR.toLowerCase()
        val containerZKExt = TASK_EXT_ZK.toLowerCase()

        taskPreparer.createBaseTasks(TASK_EXT_SOLR, containerSolrExt, dockerExtension.images.solr)
        taskPreparer.createBaseTasks(TASK_EXT_ZK, containerZKExt, dockerExtension.images.zookeeper)

        val imageSolrTask = project.tasks.named("pull${TASK_EXT_SOLR}", APullImage::class.java)
        val imageZKTask = project.tasks.named("pull${TASK_EXT_ZK}", APullImage::class.java)

        val startZK = project.tasks.register("start${TASK_EXT_ZK}", StartExtraContainerTask::class.java) { task ->
            configureContainerTask(task, containerZKExt)
            task.group = "icm container SolrCloud"
            task.description = "Start Zookeeper component of SolrCloud"
            task.targetImageId(project.provider { imageZKTask.get().image.get() })

            task.hostConfig.portBindings.set(
                listOf("2181:2188"))

            task.envVars.set(mutableMapOf(
                "ZOO_MY_ID" to "1",
                "ZOO_PORT" to "2181" ,
                "ZOO_SERVERS" to "server.1=zoo-1:2888:3888"))

            task.dependsOn(imageZKTask)
        }

        val startSolr = project.tasks.register("start${TASK_EXT_SOLR}", StartExtraContainerTask::class.java) { task ->
            configureContainerTask(task, containerSolrExt)
            task.group = "icm container SolrCloud"
            task.description = "Start Solr component of SolrCloud"
            task.targetImageId(project.provider { imageSolrTask.get().image.get() })

            task.hostConfig.portBindings.set(
                listOf("8983:8983"))

            task.envVars.set(mutableMapOf(
                "SOLR_PORT" to "8983",
                "ZK_HOST" to "${taskPreparer.getContainerName(containerZKExt)}:2181" ,
                "SOLR_HOST" to taskPreparer.getContainerName(containerSolrExt)
            ))

            task.dependsOn(imageSolrTask, startZK)
        }

        val stopZK = project.tasks.named("stop${TASK_EXT_ZK}")
        val stopSolr = project.tasks.named("stop${TASK_EXT_SOLR}")

        project.tasks.register(START_SOLRCLOUD) { task ->
            task.group = "icm container SolrCloud"
            task.description = "Start all components of SolrCloud"
            task.dependsOn(startZK, startSolr)
        }

        project.tasks.register(STOP_SOLRCLOUD) { task ->
            task.group = "icm container SolrCloud"
            task.description = "Stop all components of SolrCloud"
            task.dependsOn(stopZK, stopSolr)
        }

    }

    fun createAppServerTasks() {
        try {
            project.tasks.named(TASK_PREPARESERVER)
        } catch (ex: UnknownTaskException) {
            throw GradleException("This plugin requires a task ${TASK_PREPARESERVER}' !")
        }

        val dirprep = getDirPreparerTask()
        val prepareServer = project.tasks.named(TASK_PREPARESERVER)

        val containerExt = TASK_EXT_CONTAINER.toLowerCase()
        val asContainerExt = TASK_EXT_AS.toLowerCase()

        taskPreparer.createBaseTasks(TASK_EXT_CONTAINER, containerExt, dockerExtension.images.icmbase, true)
        val imageTask = project.tasks.named("pull${TASK_EXT_CONTAINER}", APullImage::class.java)

        project.tasks.register("start${TASK_EXT_CONTAINER}", StartExtraContainerTask::class.java) { task ->
            configureContainerTask(task, containerExt)
            task.description = "Start container without any special command (sleep)"
            task.targetImageId(project.provider { imageTask.get().image.get() })
            task.entrypoint.set(listOf("/intershop/bin/startAndWait.sh"))

            task.hostConfig.portBindings.set(listOf("5005:7746"))
            task.hostConfig.binds.set(getServerVolumes())

            task.dependsOn(dirprep, prepareServer, imageTask)
        }

        taskPreparer.createBaseTasks(TASK_EXT_AS, asContainerExt, dockerExtension.images.icmbase, true)
        val imageASTask = project.tasks.named("pull${TASK_EXT_AS}", APullImage::class.java)

        project.tasks.register("start${TASK_EXT_AS}", StartServerContainerTask::class.java) { task ->
            configureContainerTask(task, asContainerExt)
            task.description = "Start container Application server of ICM"
            task.targetImageId(project.provider { imageTask.get().image.get() })

            with(dockerExtension.developmentConfig) {
                val httpASContainerPort = getConfigProperty(
                    Configuration.AS_CONNECTOR_CONTAINER_PORT,
                    Configuration.AS_CONNECTOR_CONTAINER_PORT_VALUE
                )
                val httpASPort = getConfigProperty(
                    Configuration.AS_CONNECTOR_PORT,
                    Configuration.AS_CONNECTOR_PORT_VALUE
                )
                task.hostConfig.portBindings.set(listOf("5005:7746", "${httpASPort}:${httpASContainerPort}"))
            }

            task.hostConfig.binds.set(getServerVolumes())

            task.dependsOn(dirprep, prepareServer, imageASTask)
        }
    }

    private fun configureContainerTask(task: DockerCreateContainer, containerExt: String) {
        task.group = "icm container ${containerExt}"
        task.attachStderr.set(true)
        task.attachStdout.set(true)
        task.hostConfig.autoRemove.set(true)
        task.containerName.set(taskPreparer.getContainerName(containerExt))
    }

    private fun getDirPreparerTask(): TaskProvider<Task> {
        return project.tasks.register( "dirPreparer") { task ->
            task.doLast {
                addDirectories.forEach { (_, dir) ->
                    val file = dir.get().asFile
                    if(file.exists()) {
                        file.deleteRecursively()
                    }
                    dir.get().asFile.mkdirs()
                }
            }
        }
    }

    private fun getServerVolumes(): Provider<Map<String,String>> = project.provider {
        transformVolumes( mapOf(
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
        ))
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
