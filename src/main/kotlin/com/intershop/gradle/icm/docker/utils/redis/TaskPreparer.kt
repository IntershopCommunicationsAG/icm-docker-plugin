/*
 * Copyright 2023 Intershop Communications AG.
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
package com.intershop.gradle.icm.docker.utils.redis

import com.intershop.gradle.icm.docker.tasks.PrepareNetwork
import com.intershop.gradle.icm.docker.tasks.utils.ContainerEnvironment
import com.intershop.gradle.icm.docker.utils.AbstractTaskPreparer
import com.intershop.gradle.icm.docker.utils.Configuration
import com.intershop.gradle.icm.docker.utils.PortMapping
import org.gradle.api.Project
import org.gradle.api.provider.Provider

class TaskPreparer(
    project: Project,
    networkTask: Provider<PrepareNetwork>
) : AbstractTaskPreparer(project, networkTask) {

    companion object {
        const val EXT_NAME: String = "Redis"
        const val CONTAINER_PORT = 6379
    }

    override fun getExtensionName(): String = EXT_NAME

    override fun getImage(): Provider<String> = dockerExtension.images.redis
    override fun getUseHostUserConfigProperty(): String = Configuration.REDIS_USE_HOST_USER

    init {
        initBaseTasks()
        val portMapping = PortMapping(EXT_NAME, CONTAINER_PORT, CONTAINER_PORT, true)

        val createTask = registerCreateContainerTask(findTask, mapOf(), ContainerEnvironment())
        createTask.configure { task ->
            task.withPortMappings(portMapping)
        }
        registerStartContainerTask(createTask)
    }

}
