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

package com.intershop.gradle.icm.docker.utils.solrcloud

import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.utils.Configuration
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByType

class TaskPreparer(
        val project: Project,
        private val networkTasks: com.intershop.gradle.icm.docker.utils.network.TaskPreparer
        ) {

    companion object {
        const val TASK_EXT_SERVER = "SolrCloud"
    }

    init {
        val zkTasks = ZKPreparer(project, networkTasks.createNetworkTask)
        val devConfig = project.extensions.getByType<IntershopDockerExtension>().developmentConfig
        val nodeCount = devConfig.getIntProperty(Configuration.SOLR_NODES_COUNT, Configuration.SOLR_NODES_COUNT_VALUE)
        val solrTasks = (1..nodeCount).map { nodeNr ->
            SolrPreparer(project, networkTasks.createNetworkTask, zkTasks, nodeNr)
        }
        // only needed to distribute requests across multiple Solr nodes
        val solrLBTask = if (nodeCount > 1) {
            SolrLBPreparer(project, networkTasks.createNetworkTask, solrTasks)
        } else {
            null
        }

        project.tasks.register(
                "start${TASK_EXT_SERVER}").configure { task ->
            configureSolrCloudTasks(task, "Start all components of a SolrCloud cluster with ${nodeCount} nodes")
            task.dependsOn(zkTasks.startTask, networkTasks.createNetworkTask)
            task.dependsOn(solrTasks.map { it.startTask })
            solrLBTask?.let { task.dependsOn(it.startTask) }
        }

        project.tasks.register("stop${TASK_EXT_SERVER}") { task ->
            configureSolrCloudTasks(task, "Stop all components of a SolrCloud cluster")
            task.dependsOn(zkTasks.stopTask)
            task.dependsOn(solrTasks.map { it.stopTask })
            solrLBTask?.let { task.dependsOn(it.stopTask) }
        }

        project.tasks.register("remove${TASK_EXT_SERVER}") { task ->
            configureSolrCloudTasks(task, "Removes all components of a $nodeCount node SolrCloud cluster")
            task.dependsOn(zkTasks.removeTask)
            task.dependsOn(solrTasks.map { it.removeTask })
            solrLBTask?.let { task.dependsOn(it.removeTask) }
        }

        networkTasks.removeNetworkTask.configure {
            it.mustRunAfter(zkTasks.removeTask)
            it.mustRunAfter(solrTasks.map { solrTask -> solrTask.removeTask })
            solrLBTask?.let { lb -> it.mustRunAfter(lb.removeTask) }
        }
    }

    val startTask: TaskProvider<Task> by lazy {
        project.tasks.named("start${TASK_EXT_SERVER}")
    }

    val stopTask: TaskProvider<Task> by lazy {
        project.tasks.named("stop${TASK_EXT_SERVER}")
    }

    val removeTask: TaskProvider<Task> by lazy {
        project.tasks.named("remove${TASK_EXT_SERVER}")
    }

    private fun configureSolrCloudTasks(task: Task, description: String) {
        task.group = "icm container solrcloud"
        task.description = description
    }
}
