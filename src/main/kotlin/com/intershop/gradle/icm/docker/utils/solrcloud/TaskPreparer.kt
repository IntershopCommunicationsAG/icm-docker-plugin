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

import com.intershop.gradle.icm.docker.tasks.PrepareNetwork
import com.intershop.gradle.icm.docker.tasks.RemoveNetwork
import com.intershop.gradle.icm.docker.utils.AbstractTaskPreparer
import com.intershop.gradle.icm.docker.utils.network.TaskPreparer
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

class TaskPreparer(val project: Project, private val networkTasks: TaskPreparer) {

    companion object {
        const val TASK_EXT_SERVER = "SolrCloud"
    }

    init {
        val zkTasks = ZKPreparer(project, networkTasks.createNetworkTask)
        val solrTasks = SolrPreparer(project, networkTasks.createNetworkTask)

        solrTasks.startTask.configure {
            it.dependsOn(zkTasks.startTask)
        }

        solrTasks.removeTask.configure {
            it.dependsOn(zkTasks.removeTask)
        }

        project.tasks.register("start${TASK_EXT_SERVER}") { task ->
            configureSolrCloudTasks(task, "Start all components of a one note SolrCloud cluster")
            task.dependsOn(zkTasks.startTask, solrTasks.startTask, networkTasks.createNetworkTask)
        }

        project.tasks.register("stop${TASK_EXT_SERVER}") { task ->
            configureSolrCloudTasks(task, "Stop all components of a one note SolrCloud cluster")
            task.dependsOn(zkTasks.stopTask, solrTasks.stopTask)
        }

        project.tasks.register("remove${TASK_EXT_SERVER}") { task ->
            configureSolrCloudTasks(task, "Removes all components of a one note SolrCloud cluster")
            task.dependsOn(zkTasks.removeTask, solrTasks.removeTask)
        }

        networkTasks.removeNetworkTask.configure {
            it.mustRunAfter(zkTasks.removeTask, solrTasks.removeTask)
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
