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

import com.intershop.gradle.icm.ICMProjectPlugin
import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.tasks.DBPrepareTask
import com.intershop.gradle.icm.docker.tasks.ISHUnitHTMLTestReport
import com.intershop.gradle.icm.docker.tasks.ISHUnitTest
import com.intershop.gradle.icm.docker.tasks.PrepareNetwork
import com.intershop.gradle.icm.docker.tasks.StartExtraContainer
import com.intershop.gradle.icm.docker.utils.CustomizationImageBuildPreparer
import com.intershop.gradle.icm.docker.utils.ISHUnitTestRegistry
import com.intershop.gradle.icm.docker.utils.appsrv.AbstractASTaskPreparer
import com.intershop.gradle.icm.docker.utils.network.TaskPreparer
import com.intershop.gradle.icm.docker.utils.appsrv.TaskPreparer as AppSrvPreparer
import com.intershop.gradle.icm.docker.utils.webserver.WATaskPreparer
import com.intershop.gradle.icm.extension.IntershopExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByType

/**
 * Main plugin class of the customization plugin.
 *
 * Configures the build to:
 * - create customization images
 * - execute a dbPrepare using the ICM-AS (test) images plus customization cartridges
 * - execute ishUnit test using the ICM-AS (test) images plus customization cartridges
 * - start and stop an AS using the ICM-AS (test) images plus customization cartridges
 *
 * @see CustomizationImageBuildPreparer
 * @see com.intershop.gradle.icm.docker.tasks.StartServerContainer
 * @see DBPrepareTask
 * @see ISHUnitTest
 * @see ISHUnitHTMLTestReport
 */
open class ICMDockerCustomizationPlugin : Plugin<Project> {

    companion object {
        const val INTERSHOP_EXTENSION_NAME = "intershop"
        const val ISHUNIT_REGISTRY = "ishUnitTestTegistry"
        const val HTML_ANT_TESTREPORT_CONFIG = "junitXmlToHtml"
        const val ISHUNIT_TEST = "ISHUnitTest"

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
                logger.info("ICM Docker build plugin for customizations will be initialized")
                // project tasks required
                plugins.apply(ICMProjectPlugin::class.java)
                // docker tasks required
                plugins.apply(ICMDockerPlugin::class.java)

                val dockerExtension = extensions.findByType(
                        IntershopDockerExtension::class.java
                ) ?: extensions.create("intershop_docker", IntershopDockerExtension::class.java, project)

                extensions.findByName(INTERSHOP_EXTENSION_NAME)
                ?: throw GradleException("This plugin requires the plugin 'com.intershop.gradle.icm.project'!")

                val icmExtension = project.extensions.getByType<IntershopExtension>()

                val mssqlDatabase = tasks.named(
                        "start${com.intershop.gradle.icm.docker.utils.mssql.TaskPreparer.extName}")
                val oracleDatabase = tasks.named(
                        "start${com.intershop.gradle.icm.docker.utils.oracle.TaskPreparer.extName}")
                val mailSrvTask = tasks.named(
                        "start${com.intershop.gradle.icm.docker.utils.mail.TaskPreparer.extName}",
                        StartExtraContainer::class.java)
                val startSolrCloud = tasks.named(
                        "start${com.intershop.gradle.icm.docker.utils.solrcloud.TaskPreparer.TASK_EXT_SERVER}")

                val prepareNetwork = project.tasks.named(TaskPreparer.PREPARE_NETWORK, PrepareNetwork::class.java)

                val appSrvPreparer = AppSrvPreparer(project, prepareNetwork)
                val containerPreparer = appSrvPreparer.getContainerTaskPreparer()

                try {
                    tasks.named("containerClean").configure {
                        it.dependsOn( containerPreparer.removeTask,
                            appSrvPreparer.getServerTaskPreparer().removeTask)
                    }
                } catch (ex: UnknownTaskException) {
                    logger.info("Task containerClean is not available.")
                }

                val asStartTask = appSrvPreparer.getServerTaskPreparer().startTask
                asStartTask.configure {
                    it.mustRunAfter(startSolrCloud)
                    it.mustRunAfter(mailSrvTask)
                }

                val startWA = tasks.named("start${WATaskPreparer.extName}")
                val startWS = tasks.named(
                        "start${com.intershop.gradle.icm.docker.utils.webserver.TaskPreparer.TASK_EXT_SERVER}")

                tasks.register(TASK_START_SERVER) { task ->
                    task.group = ICMDockerPlugin.GROUP_SERVERBUILD
                    task.description = "Start app server container with webserver containers"
                    task.dependsOn(startWS)
                    task.dependsOn(asStartTask)
                }

                startWS.configure {
                    it.mustRunAfter(asStartTask)
                }

                startWA.configure {
                    it.mustRunAfter(asStartTask)
                }

                val stopWS = tasks.named(
                        "stop${com.intershop.gradle.icm.docker.utils.webserver.TaskPreparer.TASK_EXT_SERVER}")

                tasks.register(TASK_STOP_SERVER) { task ->
                    task.group = ICMDockerPlugin.GROUP_SERVERBUILD
                    task.description = "Stop app server container and webserver containers"
                    task.dependsOn(containerPreparer.stopTask, stopWS)
                }

                val removeWS = tasks.named(
                        "remove${com.intershop.gradle.icm.docker.utils.webserver.TaskPreparer.TASK_EXT_SERVER}")

                tasks.register(TASK_REMOVE_SERVER) { task ->
                    task.group = ICMDockerPlugin.GROUP_SERVERBUILD
                    task.description = "Removes app server container and webserver containers"
                    task.dependsOn(containerPreparer.removeTask, removeWS)
                }

                val dbPrepare: TaskProvider<DBPrepareTask> = getDBPrepare(this,
                    containerPreparer, mssqlDatabase, oracleDatabase)

                configureISHUnitTest(this,
                    dockerExtension, containerPreparer, dbPrepare, mssqlDatabase, oracleDatabase)

                addTestReportConfiguration(this)

                val customizationName = project.getCustomizationName()

                with(dockerExtension.imageBuild.images) {
                    mainImage.nameExtension.set("")
                    mainImage.description.set("customization $customizationName")
                    testImage.nameExtension.set("test")
                    testImage.description.set("test for customization $customizationName")
                }

                CustomizationImageBuildPreparer(this, dockerExtension.images,
                        dockerExtension.imageBuild.images, icmExtension.projectConfig).prepareImageBuilds()
            }
        }
    }

    /**
     * Determines the name of the customization that is associated the project this plugin is applied to.
     *
     * __Attention__: extends the class [Project] by this function
     */
    open fun Project.getCustomizationName(): String = name

    private fun getDBPrepare(
        project: Project,
        containerPreparer: AbstractASTaskPreparer,
        mssqlDatabase: TaskProvider<Task>,
        oracleDatabase: TaskProvider<Task>,
    ): TaskProvider<DBPrepareTask> {
        return project.tasks.register(DBPrepareTask.TASK_NAME, DBPrepareTask::class.java) { task ->
            task.group = ICMDockerPlugin.GROUP_SERVERBUILD
            task.description = "Starts dbPrepare in an existing ICM base container."
            task.executeUsing(containerPreparer.startTask)

            task.finalizedBy(containerPreparer.removeTask)
            task.mustRunAfter(mssqlDatabase, oracleDatabase)
        }
    }

    private fun configureISHUnitTest(
            project: Project,
            extension: IntershopDockerExtension,
            containerPreparer: AbstractASTaskPreparer,
            dbPrepare: TaskProvider<DBPrepareTask>,
            mssqlDatabase: TaskProvider<Task>,
            oracleDatabase: TaskProvider<Task>) {
        project.gradle.sharedServices.registerIfAbsent(ISHUNIT_REGISTRY,
                ISHUnitTestRegistry::class.java) {
            it.maxParallelUsages.set(1)
        }

        val ishUnitTest = project.tasks.register(ISHUnitHTMLTestReport.TASK_NAME,
                ISHUnitHTMLTestReport::class.java) { task ->
            task.group = ICMDockerPlugin.GROUP_SERVERBUILD
            task.description = "Generates report for ISHUnitTest execution"
        }

        extension.ishUnitTests.all { suite ->
            val ishunitTest = project.tasks.register(suite.name + ISHUNIT_TEST,
                    ISHUnitTest::class.java) { task ->
                task.group = ICMDockerPlugin.GROUP_SERVERBUILD
                task.description = "Starts ISHUnitTest suite '" + suite.name + "' in an existing ICM base container."

                task.executeUsing(containerPreparer.startTask)
                task.testCartridge.set(suite.cartridge)
                task.testSuite.set(suite.testSuite)

                task.finalizedBy(containerPreparer.removeTask)
                task.mustRunAfter(dbPrepare, mssqlDatabase, oracleDatabase)
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
