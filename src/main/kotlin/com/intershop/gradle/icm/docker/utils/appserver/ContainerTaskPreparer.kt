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

package com.intershop.gradle.icm.docker.utils.appserver

import com.intershop.gradle.icm.docker.tasks.PrepareNetwork
import com.intershop.gradle.icm.docker.tasks.StartServerContainer
import org.gradle.api.Project
import org.gradle.api.provider.Provider

class ContainerTaskPreparer(project: Project,
                            networkTask: Provider<PrepareNetwork>) : AbstractTaskPreparer(project, networkTask) {

    companion object {
        const val extName: String = "Container"
    }

    override val extensionName: String = extName
    override val containerExt: String = extensionName.lowercase()

    init {
        initBaseTasks()

        project.tasks.register("start${extensionName}", StartServerContainer::class.java) { task ->
            configureContainerTask(task)

            task.description = "Start container without any special command (sleep)"

            task.targetImageId(project.provider { pullTask.get().image.get() })
            task.image.set(pullTask.get().image)

            task.entrypoint.set(listOf("/intershop/bin/startAndWait.sh"))

            task.hostConfig.binds.set(getServerVolumes().apply {
                project.logger.info("Using the following volume binds for container startup in task {}: {}", task.name, this)
            })
            task.hostConfig.portBindings.set(project.provider { getPortMappings().map { pm -> pm.render() }.apply {
                project.logger.info("Using the following port mappings for container startup in task {}: {}", task.name, this)
            }} )
            task.hostConfig.network.set(networkId)

            task.dependsOn(prepareServer, pullTask, networkTask)
        }
    }
}
