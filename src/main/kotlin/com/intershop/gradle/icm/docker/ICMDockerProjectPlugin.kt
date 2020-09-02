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
import com.intershop.gradle.icm.docker.tasks.StartExtraContainerTask
import com.intershop.gradle.icm.docker.utils.ISHUnitTestRegistry
import com.intershop.gradle.icm.docker.utils.ProjectImageBuildPreparer
import com.intershop.gradle.icm.docker.utils.ServerTaskPreparer
import com.intershop.gradle.icm.docker.utils.ServerTaskPreparer.Companion.START_WEBSERVER
import com.intershop.gradle.icm.docker.utils.ServerTaskPreparer.Companion.STOP_WEBSERVER
import com.intershop.gradle.icm.docker.utils.ServerTaskPreparer.Companion.TASK_EXT_CONTAINER
import com.intershop.gradle.icm.docker.utils.ServerTaskPreparer.Companion.TASK_EXT_MSSQL
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencySet

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
    }

    /**
     * Main method of a plugin.
     *
     * @param project target project
     */
    override fun apply(project: Project) {
        with(project) {
            if(project.rootProject == this) {
                logger.info("ICM Docker build plugin for projects will be initialized")
                plugins.apply(ICMDockerPlugin::class.java)

                val extension = extensions.findByType(
                        IntershopDockerExtension::class.java
                ) ?: extensions.create("intershop_docker", IntershopDockerExtension::class.java)

                project.setProperty("runASasContainer", true)

                extensions.findByName(INTERSHOP_EXTENSION_NAME)
                    ?: throw GradleException("This plugin requires the plugin 'com.intershop.gradle.icm.project'!")

                addTestReportConfiguration(this)

                gradle.sharedServices.registerIfAbsent(ISHUNIT_REGISTRY, ISHUnitTestRegistry::class.java) {
                    it.maxParallelUsages.set(1)
                }

                val serverTaskPreparer = ServerTaskPreparer(project, extension)
                serverTaskPreparer.createAppServerTasks()
                serverTaskPreparer.createSolrServerTasks()

                val startWS = project.tasks.named(START_WEBSERVER)
                val stopWS = project.tasks.named(STOP_WEBSERVER)
                val startAS = project.tasks.named("start${ServerTaskPreparer.TASK_EXT_AS}")
                val stopAS = project.tasks.named("stop${ServerTaskPreparer.TASK_EXT_AS}")

                project.tasks.register(TASK_START_SERVER) { task ->
                    task.group = GROUP_SERVERBUILD
                    task.description = "Start app server container with webserver containers"
                    task.dependsOn(startAS, startWS)
                }

                project.tasks.register(TASK_STOP_SERVER) { task ->
                    task.group = GROUP_SERVERBUILD
                    task.description = "Stop app server container and webserver containers"
                    task.dependsOn( stopAS, stopWS)
                }

                prepareBaseContainer(project, extension)

                ProjectImageBuildPreparer(project, extension.images, extension.imageBuild.images).prepareImageBuilds()
            }
        }
    }

    private fun prepareBaseContainer(project: Project,
                                     extension: IntershopDockerExtension) {

        val startContainer = project.tasks.named("start${TASK_EXT_CONTAINER}", StartExtraContainerTask::class.java)
        val removeContainer = project.tasks.named("remove${TASK_EXT_CONTAINER}")
        val startDatabase = project.tasks.named("start${TASK_EXT_MSSQL}")

        val dbprepare = project.tasks.register(TASK_DBPREPARE, DBPrepareTask::class.java) { task ->
            task.group = GROUP_SERVERBUILD
            task.description = "Starts dbPrepare in an existing ICM base container."
            task.containerId.set(project.provider {  startContainer.get().containerId.get() })
            task.dependsOn(startContainer)
            task.finalizedBy(removeContainer)
            task.mustRunAfter(startDatabase)
        }

        val ishUnitTest = project.tasks.register(TASK_ISHUNIT_REPORT, ISHUnitHTMLTestReportTask::class.java)

        extension.ishUnitTests.all {
            val ishunitTest = project.tasks.register(it.name + ISHUNIT_TEST, ISHUnitTask::class.java) { task ->
                task.group = GROUP_SERVERBUILD
                task.description = "Starts ISHUnitTest suite '" + it.name + "' in an existing ICM base container."

                task.containerId.set(project.provider {  startContainer.get().containerId.get() })
                task.testCartridge.set(it.cartridge)
                task.testSuite.set(it.testSuite)

                task.dependsOn(startContainer)
                task.finalizedBy(removeContainer)
                task.mustRunAfter(dbprepare, startDatabase)
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
