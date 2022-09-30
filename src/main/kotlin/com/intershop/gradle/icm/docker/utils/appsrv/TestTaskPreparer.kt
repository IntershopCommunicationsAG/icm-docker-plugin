/*
 * Copyright 2022 Intershop Communications AG.
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
package com.intershop.gradle.icm.docker.utils.appsrv

import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.tasks.CreateVolumes
import com.intershop.gradle.icm.docker.tasks.PrepareNetwork
import com.intershop.gradle.icm.docker.tasks.RemoveContainerByName
import com.intershop.gradle.icm.docker.tasks.RemoveVolumes
import com.intershop.gradle.icm.docker.tasks.StartExtraContainer
import com.intershop.gradle.icm.docker.utils.Configuration
import com.intershop.gradle.icm.extension.IntershopExtension
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByType

class TestTaskPreparer (val project: Project, private val networkTask: Provider<PrepareNetwork>) {

    companion object {
        const val TASK_EXT_VOLUMES = "ASVolumes"
    }

    private val dockerExtension = project.extensions.getByType<IntershopDockerExtension>()
    private val icmExtension = project.extensions.getByType<IntershopExtension>()

    private var icmServerTaskPreparer: AbstractASTaskPreparer

    init {
        val containerVolumes = mapOf(
            "${dockerExtension.containerPrefix}-customizations" to "/customizations")

        val createVolumes =
            project.tasks.register("create${TASK_EXT_VOLUMES}", CreateVolumes::class.java) { task ->
                configureASTasks(task, "Creates volumes in Docker")
                task.volumeNames.set( containerVolumes.keys )
            }

        val removeVolumes =
            project.tasks.register(
                "remove${TASK_EXT_VOLUMES}",
                RemoveVolumes::class.java) { task ->
                configureASTasks(task, "Removes volumes from Docker")
                task.volumeNames.set( containerVolumes.keys )
            }

        val customizationStartTasks = mutableSetOf<TaskProvider<StartExtraContainer>>()
        val customizationRemoveTasks = mutableSetOf<TaskProvider<RemoveContainerByName>>()
        icmExtension.projectConfig.modules.all {
            val useTest = dockerExtension.developmentConfig.
                                    getConfigProperty(Configuration.AS_USE_TESTIMAGE).toBoolean()
            val imagePath = if (useTest || ! it.testImage.isPresent) { it.image } else { it.testImage }
            val cp = CustomizationPreparer(project, networkTask, it.name, imagePath)

            cp.startTask.configure {
                it.dependsOn(createVolumes)
            }
            customizationStartTasks.add(cp.startTask)
            customizationRemoveTasks.add(cp.removeTask)
        }

        icmServerTaskPreparer = ICMServerTaskPreparer(project, networkTask)
        icmServerTaskPreparer.startTask.configure {
            it.dependsOn(customizationStartTasks, icmServerTaskPreparer.pullTask, networkTask)
        }

        icmServerTaskPreparer.removeTask.configure {
            it.dependsOn(customizationRemoveTasks)
            it.finalizedBy(removeVolumes)
        }

        icmServerTaskPreparer.stopTask.configure {
            it.dependsOn(customizationRemoveTasks)
            it.finalizedBy(removeVolumes)
        }
    }

    fun getICMServerTaskPreparer() = icmServerTaskPreparer

    private fun configureASTasks(task: Task, description: String) {
        task.group = "icm container appserver "
        task.description = description
    }
}