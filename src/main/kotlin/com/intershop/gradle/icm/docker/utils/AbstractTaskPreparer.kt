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

package com.intershop.gradle.icm.docker.utils

import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.tasks.AbstractPullImage
import com.intershop.gradle.icm.docker.tasks.PrepareNetwork
import com.intershop.gradle.icm.docker.tasks.PullExtraImage
import com.intershop.gradle.icm.docker.tasks.RemoveContainerByName
import com.intershop.gradle.icm.docker.tasks.StopExtraContainer
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByType

abstract class AbstractTaskPreparer(protected val project: Project,
                                    networkTask: Provider<PrepareNetwork>) {

    protected abstract fun getExtensionName(): String
    protected open fun getContainerExt(): String = getExtensionName().lowercase()
    protected abstract fun getImage(): Provider<String>

    protected val extension = project.extensions.getByType<IntershopDockerExtension>()

    protected fun initBaseTasks() {
        project.tasks.register("pull${getExtensionName()}", PullExtraImage::class.java) { task ->
            task.group = "icm container ${getContainerExt()}"
            task.description = "Pull image from registry"
            task.image.set(getImage())
        }

        project.tasks.register("stop${getExtensionName()}", StopExtraContainer::class.java) { task ->
            task.group = "icm container ${getContainerExt()}"
            task.description = "Stop running container"
            task.containerName.set("${extension.containerPrefix}-${getContainerExt()}")
        }

        project.tasks.register("remove${getExtensionName()}", RemoveContainerByName::class.java) { task ->
            task.group = "icm container ${getContainerExt()}"
            task.description = "Remove container from Docker"

            task.containerName.set("${extension.containerPrefix}-${getContainerExt()}")
        }
    }

    val pullTask: TaskProvider<AbstractPullImage> by lazy {
        project.tasks.named("pull${getExtensionName()}", AbstractPullImage::class.java) }

    val stopTask: TaskProvider<StopExtraContainer> by lazy {
        project.tasks.named("stop${getExtensionName()}", StopExtraContainer::class.java)
    }

    val removeTask: TaskProvider<RemoveContainerByName> by lazy {
        project.tasks.named("remove${getExtensionName()}", RemoveContainerByName::class.java)
    }

    val startTask: TaskProvider<DockerCreateContainer> by lazy {
        project.tasks.named("start${getExtensionName()}", DockerCreateContainer::class.java)
    }

    protected val networkId: Property<String> = networkTask.get().networkId

    protected fun configureContainerTask(task: DockerCreateContainer) {
        task.group = "icm container ${getContainerExt()}"
        task.attachStderr.set(true)
        task.attachStdout.set(true)
        task.hostConfig.autoRemove.set(true)

        task.containerName.set("${extension.containerPrefix}-${getContainerExt()}")
    }
}
