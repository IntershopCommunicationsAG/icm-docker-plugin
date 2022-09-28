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

import com.intershop.gradle.icm.docker.tasks.PrepareNetwork
import com.intershop.gradle.icm.docker.tasks.StartServerContainer
import com.intershop.gradle.icm.docker.utils.Configuration
import org.gradle.api.Project
import org.gradle.api.provider.Provider

class ContainerTaskPreparer(
    project: Project,
    networkTask: Provider<PrepareNetwork>
) : AbstractASTaskPreparer(project, networkTask) {

    companion object {
        const val extName: String = "Container"
    }

    override fun getImage(): Provider<String> {
        if(dockerExtension.developmentConfig.getConfigProperty(
                Configuration.AS_USE_TESTIMAGE,
                Configuration.AS_USE_TESTIMAGE_VALUE
            ).toBoolean()) {
            return icmExtension.projectConfig.base.testImage
        }
        return icmExtension.projectConfig.base.image
    }

    override fun getExtensionName(): String = extName

    init {
        project.tasks.register("start${this.getExtensionName()}", StartServerContainer::class.java) { task ->
            configureContainerTask(task)

            task.description = "Start container without any special command (sleep)"

            task.targetImageId(project.provider { pullTask.get().image.get() })
            task.image.set(pullTask.get().image)

            task.entrypoint.set(listOf("/intershop/bin/startAndWait.sh"))

            task.hostConfig.binds.set(project.provider {
                getServerVolumes(task, true).apply {
                    project.logger.info("Using the following volume binds for container startup in task {}: {}",
                        task.name, this)
                }
            })
            task.withPortMappings(*getPortMappings().toTypedArray())
            task.hostConfig.network.set(networkId)
        }
    }
}
