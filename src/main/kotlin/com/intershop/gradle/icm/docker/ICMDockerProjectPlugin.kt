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
import com.intershop.gradle.icm.docker.tasks.StartExtraContainerTask
import com.intershop.gradle.icm.docker.utils.ServerTaskPreparer
import com.intershop.gradle.icm.docker.utils.DatabaseTaskPreparer
import com.intershop.gradle.icm.docker.utils.ISHUnitTestRegistry
import com.intershop.gradle.icm.docker.utils.SolrCloudPreparer
import com.intershop.gradle.icm.docker.utils.StandardTaskPreparer
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

                gradle.sharedServices.registerIfAbsent(ISHUNIT_REGISTRY, ISHUnitTestRegistry::class.java, {
                    it.maxParallelUsages.set(1)
                })

                val standardTaksPreparer = StandardTaskPreparer(project)
                val startMSSQL = prepareDatabaseContainer(project, standardTaksPreparer, extension)

                prepareSolrCloudContainer(project, standardTaksPreparer, extension)
                prepareBaseContainer(this, standardTaksPreparer, extension, startMSSQL)

            }
        }
    }

    private fun prepareBaseContainer(project: Project,
                                     taskPreparer: StandardTaskPreparer,
                                     extension: IntershopDockerExtension,
                                     startDatabase: StartExtraContainerTask) {

        val serverPreparer = ServerTaskPreparer(project, extension)

        val removeContainerByName = taskPreparer.getRemoveTask(
                ServerTaskPreparer.TASK_REMOVE,
                ServerTaskPreparer.CONTAINER_EXTENSION)
        val pullImage = taskPreparer.getBasePullTask(
                ServerTaskPreparer.TASK_PULL,
                extension.images.icmbase)

        val baseContainer = serverPreparer.getBaseContainer(pullImage)
        val startContainer = serverPreparer.getStartContainer(baseContainer)
        val removeContainer = serverPreparer.getFinalizeContainer(startContainer)


        val dbprepare = serverPreparer.getDBPrepareTask(baseContainer)
        dbprepare.dependsOn(startContainer)
        dbprepare.finalizedBy(removeContainer)
        dbprepare.mustRunAfter(startDatabase)

        baseContainer.dependsOn(removeContainerByName)
        startContainer.finalizedBy(removeContainer)

        val ishunitreport = serverPreparer.getISHUnitHTMLTestReportTask()

        extension.ishUnitTests.all {
            project.tasks.maybeCreate(it.name + ISHUNIT_TEST, ISHUnitTask::class.java).apply {
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

    private fun prepareDatabaseContainer(project: Project,
                                         taskPreparer: StandardTaskPreparer,
                                         extension: IntershopDockerExtension): StartExtraContainerTask {

        val dbTaskPreparer = DatabaseTaskPreparer(project, extension)
        val pullMSSQL = taskPreparer.getPullTask(
                DatabaseTaskPreparer.TASK_PULL,
                extension.images.mssqldb)
        taskPreparer.getStopTask(
                DatabaseTaskPreparer.TASK_STOP,
                DatabaseTaskPreparer.CONTAINER_EXTENSION,
                extension.images.mssqldb)
        taskPreparer.getRemoveTask(
                DatabaseTaskPreparer.TASK_REMOVE,
                DatabaseTaskPreparer.CONTAINER_EXTENSION)

        return dbTaskPreparer.getMSSQLStartTask(pullMSSQL)
    }

    private fun prepareSolrCloudContainer(project: Project,
                                          taskPreparer: StandardTaskPreparer,
                                          extension: IntershopDockerExtension) {
        val pullZK = taskPreparer.getPullTask(
                SolrCloudPreparer.TASK_PULL_ZK,
                extension.images.zookeeper)
        val pullSolr = taskPreparer.getPullTask(
                SolrCloudPreparer.TASK_PULL_SOLR,
                extension.images.solr)

        val solrCloudTaskPreparer = SolrCloudPreparer(project, extension)
        val zkStartTask = solrCloudTaskPreparer.getZKStartTask(pullZK)
        val solrStartTask = solrCloudTaskPreparer.getSolrStartTask(pullSolr)

        solrStartTask.dependsOn(zkStartTask)

        val stopZK = taskPreparer.getStopTask(
                SolrCloudPreparer.TASK_STOP_ZK,
                SolrCloudPreparer.CONTAINER_EXTENSION_ZK,
                extension.images.zookeeper)
        val stopSolr = taskPreparer.getStopTask(
                SolrCloudPreparer.TASK_STOP_SOLR,
                SolrCloudPreparer.CONTAINER_EXTENSION_SOLR,
                extension.images.solr)
        stopZK.dependsOn(stopSolr)

        taskPreparer.getRemoveTask(
                SolrCloudPreparer.TASK_REMOVE_ZK,
                SolrCloudPreparer.CONTAINER_EXTENSION_ZK)
        taskPreparer.getRemoveTask(
                SolrCloudPreparer.TASK_REMOVE_SOLR,
                SolrCloudPreparer.CONTAINER_EXTENSION_SOLR)
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
