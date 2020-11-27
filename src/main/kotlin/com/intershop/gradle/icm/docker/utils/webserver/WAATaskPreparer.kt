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

import com.intershop.gradle.icm.docker.tasks.PrepareNetwork
import com.intershop.gradle.icm.docker.tasks.StartExtraContainer
import com.intershop.gradle.icm.docker.utils.AbstractTaskPreparer
import org.gradle.api.Project
import org.gradle.api.provider.Provider

class WAATaskPreparer(project: Project,
                      networkTask: Provider<PrepareNetwork>,
                      volumes: Map<String,String>,
                      volumesSec: Map<String,String>) : AbstractTaskPreparer(project, networkTask, true) {

    companion object {
        const val extName: String = "WAA"
        const val taskGroup: String = "icm container webserver"
    }

    override val image: Provider<String> = extension.images.webadapteragent
    override val extensionName: String = extName
    override val containerExt: String = extensionName.toLowerCase()

    init{
        initBaseTasks()

        pullTask.configure {
            it.group = taskGroup
        }
        stopTask.configure {
            it.group = taskGroup
        }
        removeTask.configure {
            it.group = taskGroup
        }

        configureStartTask(
            "start${extensionName}",
            taskGroup,
            volumes,
            networkTask)

        if(secInstance) {
            stopTaskSec.configure {
                it.group = taskGroup
            }
            removeTaskSec.configure {
                it.group = taskGroup
            }

            configureStartTask(
                "start${extensionName}${addContainerPrefixSec}",
                taskGroup,
                volumesSec,
                networkTask)
        }
    }

    private fun configureStartTask( taskName: String,
                                    taskGroupName: String,
                                    pVolumes: Map<String,String>,
                                    pNetworkTask: Provider<PrepareNetwork>) {
        project.tasks.register(taskName, StartExtraContainer::class.java) { task ->
            configureContainerTask(task, secInstance)
            task.group = taskGroupName
            task.description = "Start ICM WebAdapterAgent"

            task.targetImageId(project.provider { pullTask.get().image.get() })
            task.image.set(pullTask.get().image)

            task.hostConfig.binds.set( pVolumes )
            task.hostConfig.network.set(networkId)

            task.dependsOn(pullTask, pNetworkTask)
        }
    }
}
