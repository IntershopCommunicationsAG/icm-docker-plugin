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
package com.intershop.gradle.icm.docker.utils.webserver

import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.tasks.CreateVolumes
import com.intershop.gradle.icm.docker.tasks.RemoveVolumes
import com.intershop.gradle.icm.docker.utils.Configuration
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByType
import java.io.File
import com.intershop.gradle.icm.docker.utils.network.TaskPreparer as NetworkPreparer

class TaskPreparer(val project: Project, private val networkTasks: NetworkPreparer) {

    companion object {
        const val TASK_EXT_VOLUMES = "WebVolumes"
        const val TASK_EXT_SERVER = "WebServer"
    }

    private val extension = project.extensions.getByType<IntershopDockerExtension>()

    val waTasks : WATaskPreparer

    init {
        val volumes = mapOf(
            "${extension.containerPrefix}-pagecache" to "/intershop/pagecache",
            "${extension.containerPrefix}-walogs" to "/intershop/logs")

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

        val certsPath =  extension.developmentConfig.getConfigProperty(Configuration.WS_CERT_PATH)
        val certsDir = File(certsPath)
        val certVol = if(certsDir.exists()) {
            mutableMapOf(
                certsDir.absolutePath to "/intershop/webserver-certs")
        } else {
            emptyMap()
        }

        val waaTasks = WAATaskPreparer(project, networkTasks.createNetworkTask, volumes)
        waTasks = WATaskPreparer(project, networkTasks.createNetworkTask, volumes + certVol)

        waTasks.startTask.configure {
            it.dependsOn(createVolumes)
        }

        waaTasks.startTask.configure {
            it.dependsOn(createVolumes)
        }

        project.tasks.register("start${TASK_EXT_SERVER}") { task ->
            configureWebServerTasks(task, "Start all components for ICM WebServer")
            task.dependsOn(waTasks.startTask, waaTasks.startTask, networkTasks.createNetworkTask)
        }

        project.tasks.register("stop${TASK_EXT_SERVER}") { task ->
            configureWebServerTasks(task, "Stop all components for ICM WebServer")
            task.dependsOn(waTasks.stopTask, waaTasks.stopTask)
            task.finalizedBy(removeVolumes)
        }

        networkTasks.removeNetworkTask.configure {
            it.mustRunAfter(waTasks.removeTask, waaTasks.removeTask, removeVolumes)
        }

        project.tasks.register("remove${TASK_EXT_SERVER}") { task ->
            configureWebServerTasks(task, "Removes all components for ICM WebServer")
            task.dependsOn(waTasks.removeTask, waaTasks.removeTask)
            task.finalizedBy(removeVolumes)
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

    private fun configureWebServerTasks(task: Task, description: String) {
        task.group = "icm container webserver"
        task.description = description
    }
}
