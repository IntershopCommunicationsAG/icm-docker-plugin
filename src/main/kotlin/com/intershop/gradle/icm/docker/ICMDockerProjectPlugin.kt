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

import com.intershop.gradle.icm.docker.ICMDockerPlugin.Companion.GROUP_SERVERBUILD
import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.tasks.DBPrepareTask
import com.intershop.gradle.icm.docker.tasks.ISHUnitHTMLTestReportTask
import com.intershop.gradle.icm.docker.tasks.ISHUnitTask
import com.intershop.gradle.icm.docker.tasks.PrepareNetwork
import com.intershop.gradle.icm.docker.tasks.RemoveNetwork
import com.intershop.gradle.icm.docker.utils.ISHUnitTestRegistry
import com.intershop.gradle.icm.docker.utils.ProjectImageBuildPreparer
import com.intershop.gradle.icm.docker.utils.appserver.ServerTaskPreparer
import com.intershop.gradle.icm.docker.utils.appserver.ContainerTaskPreparer
import com.intershop.gradle.icm.docker.utils.webserver.TaskPreparer as WebServerPreparer
import com.intershop.gradle.icm.docker.utils.webserver.WATaskPreparer
import com.intershop.gradle.icm.docker.utils.solrcloud.TaskPreparer as SolrCloudPreparer
import com.intershop.gradle.icm.docker.utils.network.TaskPreparer as NetworkPreparer
import com.intershop.gradle.icm.docker.utils.mssql.TaskPreparer as MSSQLPreparer
import com.intershop.gradle.icm.docker.utils.oracle.TaskPreparer as OraclePreparer
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.tasks.TaskProvider

/**
 * Main plugin class of the project plugin.
 */
open class ICMDockerProjectPlugin : Plugin<Project> {

    companion object {
        const val INTERSHOP_EXTENSION_NAME = "intershop"
        const val ISHUNIT_REGISTRY = "ishUnitTestTegistry"
        const val HTML_ANT_TESTREPORT_CONFIG = "junitXmlToHtml"
        const val ISHUNIT_TEST = "ISHUnitTest"

        const val TASK_DBPREPARE = "dbPrepare"
        const val TASK_ISHUNIT_REPORT = "ishUnitTestReport"

        const val TASK_START_SERVER = "startServer"
        const val TASK_STOP_SERVER = "stopServer"
        const val TASK_REMOVE_SERVER = "removeServer"
    }

    /**
     * Main method of a plugin.
     *
     * @param project target project
     */
    override fun apply(project: Project) {
        with(project) {
            if (project.rootProject == this) {
                logger.info("ICM Docker build plugin for projects will be initialized")
                plugins.apply(ICMDockerPlugin::class.java)

                val extension = extensions.findByType(
                    IntershopDockerExtension::class.java
                ) ?: extensions.create("intershop_docker", IntershopDockerExtension::class.java, project)

                extension.developmentConfig.appserverAsContainer = true

                extensions.findByName(INTERSHOP_EXTENSION_NAME)
                    ?: throw GradleException("This plugin requires the plugin 'com.intershop.gradle.icm.project'!")

                val prepareNetwork = project.tasks.named(NetworkPreparer.PREPARE_NETWORK, PrepareNetwork::class.java)
                val removeNetwork = project.tasks.named(NetworkPreparer.REMOVE_NETWORK, RemoveNetwork::class.java)

                val solrcloudPreparer = SolrCloudPreparer(project, prepareNetwork, removeNetwork)
                val containerPreparer = ContainerTaskPreparer(project, prepareNetwork)
                val appServerPreparer = ServerTaskPreparer(project, prepareNetwork)

                try {
                    tasks.named("clean").configure {
                        it.dependsOn(
                            solrcloudPreparer.removeTask,
                            containerPreparer.removeTask,
                            appServerPreparer.removeTask
                        )
                    }
                    tasks.named("clean").configure {
                        it.dependsOn(
                            solrcloudPreparer.removeTask,
                            containerPreparer.removeTask,
                            appServerPreparer.removeTask
                        )
                    }
                } catch (ex: UnknownTaskException) {
                    logger.info("Task clean is not available.")
                }

                val startWA = tasks.named("start${WATaskPreparer.extName}")
                val startWS = tasks.named("start${WebServerPreparer.TASK_EXT_SERVER}")

                tasks.register(TASK_START_SERVER) { task ->
                    task.group = GROUP_SERVERBUILD
                    task.description = "Start app server container with webserver containers"
                    task.dependsOn(startWS)
                }

                startWS.configure {
                    it.dependsOn(appServerPreparer.startTask)
                }

                startWA.configure {
                    it.mustRunAfter(appServerPreparer.startTask)
                    it.dependsOn(appServerPreparer.startTask)
                }

                val stopWS = tasks.named("stop${WebServerPreparer.TASK_EXT_SERVER}")

                tasks.register(TASK_STOP_SERVER) { task ->
                    task.group = GROUP_SERVERBUILD
                    task.description = "Stop app server container and webserver containers"
                    task.dependsOn(appServerPreparer.stopTask, stopWS)
                }

                val removeWS = tasks.named("remove${WebServerPreparer.TASK_EXT_SERVER}")

                tasks.register(TASK_REMOVE_SERVER) { task ->
                    task.group = GROUP_SERVERBUILD
                    task.description = "Removes app server container and webserver containers"
                    task.dependsOn(appServerPreparer.removeTask, removeWS)
                }

                val mssqlDatabase = tasks.named("start${MSSQLPreparer.extName}")
                val oracleDatabase = tasks.named("start${OraclePreparer.extName}")

                val dbprepare: TaskProvider<DBPrepareTask> =
                    getDBPrepare(this, containerPreparer, mssqlDatabase, oracleDatabase)

                configureISHUnitTest(this, extension, containerPreparer, dbprepare, mssqlDatabase, oracleDatabase)
                addTestReportConfiguration(this)
                ProjectImageBuildPreparer(this, extension.images, extension.imageBuild.images).prepareImageBuilds()
            }
        }
    }

    private fun getDBPrepare(project: Project,
                             containerPreparer: ContainerTaskPreparer,
                             mssqlDatabase: TaskProvider<Task>,
                             oracleDatabase: TaskProvider<Task>) : TaskProvider<DBPrepareTask> {
        return project.tasks.register(TASK_DBPREPARE, DBPrepareTask::class.java) { task ->
            task.group = GROUP_SERVERBUILD
            task.description = "Starts dbPrepare in an existing ICM base container."
            task.containerId.set(project.provider { containerPreparer.startTask.get().containerId.get() })
            task.dependsOn(containerPreparer.startTask)
            task.finalizedBy(containerPreparer.removeTask)
            task.mustRunAfter(mssqlDatabase, oracleDatabase)
        }
    }

    private fun configureISHUnitTest(project: Project,
                                     extension: IntershopDockerExtension,
                                     containerPreparer: ContainerTaskPreparer,
                                     dbprepare: TaskProvider<DBPrepareTask>,
                                     mssqlDatabase: TaskProvider<Task>,
                                     oracleDatabase: TaskProvider<Task>) {
        project.gradle.sharedServices.registerIfAbsent(ISHUNIT_REGISTRY, ISHUnitTestRegistry::class.java) {
            it.maxParallelUsages.set(1)
        }

        val ishUnitTest = project.tasks.register(TASK_ISHUNIT_REPORT, ISHUnitHTMLTestReportTask::class.java)

        extension.ishUnitTests.all {
            val ishunitTest = project.tasks.register(it.name + ISHUNIT_TEST, ISHUnitTask::class.java) { task ->
                task.group = GROUP_SERVERBUILD
                task.description = "Starts ISHUnitTest suite '" + it.name + "' in an existing ICM base container."

                task.containerId.set(project.provider {  containerPreparer.startTask.get().containerId.get() })
                task.testCartridge.set(it.cartridge)
                task.testSuite.set(it.testSuite)

                task.dependsOn(containerPreparer.startTask)
                task.finalizedBy(containerPreparer.removeTask)
                task.mustRunAfter(dbprepare, mssqlDatabase, oracleDatabase)
            }

            ishUnitTest.configure { task ->
                task.dependsOn(ishunitTest)
            }
        }
    }

    private fun addTestReportConfiguration(project: Project) {
        val configuration = project.configurations.maybeCreate(HTML_ANT_TESTREPORT_CONFIG)
        configuration
            .setVisible(false)
            .setTransitive(false)
            .setDescription("HTML Ant Test Report libraries")
            .defaultDependencies { dependencies: DependencySet ->
                // this will be executed if configuration is empty
                val dependencyHandler = project.dependencies
                dependencies.add(dependencyHandler.create("org.apache.ant:ant-junit:1.9.7"))
            }

        project.configurations.maybeCreate(HTML_ANT_TESTREPORT_CONFIG)
    }
}
