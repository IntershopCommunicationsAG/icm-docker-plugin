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

import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.tasks.ISHUnitTask
import com.intershop.gradle.icm.docker.utils.ContainerPreparer
import com.intershop.gradle.icm.docker.utils.DatabaseTaskPreparer
import com.intershop.gradle.icm.docker.utils.ISHUnitTestRegistry
import com.intershop.gradle.icm.docker.utils.RunTaskPreparer
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

                extensions.findByName(INTERSHOP_EXTENSION_NAME)
                    ?: throw GradleException("This plugin requires the plugin 'com.intershop.gradle.icm.project'!")

                addTestReportConfiguration(this)

                val containerPreparer = ContainerPreparer(project, extension)

                val removeContainerByName = containerPreparer.getRemoveContainerByName()
                val pullImage = containerPreparer.getPullImage()
                val baseContainer = containerPreparer.getBaseContainer(pullImage)
                val startContainer = containerPreparer.getStartContainer(baseContainer)
                val removeContainer = containerPreparer.getFinalizeContainer(startContainer)

                val runTaskPreparer = RunTaskPreparer(project)

                val dbprepare = runTaskPreparer.getDBPrepareTask(baseContainer)
                dbprepare.dependsOn(startContainer)
                dbprepare.finalizedBy(removeContainer)

                baseContainer.dependsOn(removeContainerByName)
                startContainer.finalizedBy(removeContainer)

                val dbTaskPreparer = DatabaseTaskPreparer(this, extension)
                val pullMSSQL = dbTaskPreparer.getMSSQLPullTask()

                val startMSSQL = dbTaskPreparer.getMSSQLStartTask(pullMSSQL)
                startMSSQL.dependsOn(pullMSSQL)

                dbprepare.mustRunAfter(startMSSQL)
                dbTaskPreparer.getMSSQLStopTask()
                dbTaskPreparer.getMSSQLRemoveTask()

                gradle.sharedServices.registerIfAbsent(ISHUNIT_REGISTRY, ISHUnitTestRegistry::class.java, {
                    it.maxParallelUsages.set(1)
                })

                val ishunitreport = runTaskPreparer.getISHUnitHTMLTestReportTask()

                extension.ishUnitTests.all {
                    tasks.maybeCreate(it.name + ISHUNIT_TEST, ISHUnitTask::class.java).apply {
                        this.containerId.set(startContainer.containerId)
                        this.testCartridge.set(it.cartridge)
                        this.testSuite.set(it.testSuite)

                        this.mustRunAfter(dbprepare)
                        this.finalizedBy(removeContainer)
                        this.dependsOn(startContainer)
                        ishunitreport.dependsOn(this)
                    }
                }
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
