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

import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.tasks.AbstractPullImage
import com.intershop.gradle.icm.docker.tasks.CreateExtraContainer
import com.intershop.gradle.icm.docker.tasks.FindContainer
import com.intershop.gradle.icm.docker.tasks.PrepareNetwork
import com.intershop.gradle.icm.docker.tasks.PullImage
import com.intershop.gradle.icm.docker.tasks.RemoveContainerByName
import com.intershop.gradle.icm.docker.tasks.StartExtraContainer
import com.intershop.gradle.icm.docker.tasks.StopExtraContainer
import com.intershop.gradle.icm.docker.tasks.utils.ContainerEnvironment
import com.intershop.gradle.icm.extension.IntershopExtension
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByType
import org.gradle.nativeplatform.platform.OperatingSystem
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import java.io.File

abstract class AbstractTaskPreparer(
        protected val project: Project,
        private val networkTask: Provider<PrepareNetwork>,
) {

    protected abstract fun getExtensionName(): String
    protected open fun getContainerExt(): String = getExtensionName().lowercase()
    protected abstract fun getImage(): Provider<String>
    protected fun useHostUser() : Boolean {
        return dockerExtension.developmentConfig.getBooleanProperty(getUseHostUserConfigProperty(), useHostUserDefaultValue())
    }
    protected open fun useHostUserDefaultValue() : Boolean = false
    protected abstract fun getUseHostUserConfigProperty() : String

    protected fun autoRemoveContainer() : Boolean {
        return dockerExtension.developmentConfig.getBooleanProperty(getAutoRemoveContainerConfigProperty(), autoRemoveContainerDefaultValue())
    }
    protected open fun autoRemoveContainerDefaultValue() : Boolean = true
    protected abstract fun getAutoRemoveContainerConfigProperty() : String

    protected val dockerExtension = project.extensions.getByType<IntershopDockerExtension>()
    protected val icmExtension = project.extensions.getByType<IntershopExtension>()
    protected val devConfig = dockerExtension.developmentConfig

    fun getContainerName(): String = "${dockerExtension.containerPrefix}-${getContainerExt()}"

    protected fun taskNameOf(operation: String): String = operation + getExtensionName()

    protected val taskGroup: String by lazy { "icm container ${getTaskGroupExt()}" }
    protected open fun getTaskGroupExt(): String = getContainerExt()

    protected fun initBaseTasks() {
        val findTask = project.tasks.register(taskNameOf("find"), FindContainer::class.java) { task ->
            task.group = taskGroup
            task.description = "Finds the ${getContainerExt()}-container to check if it exists and is running"
            task.containerName.set(getContainerName())
            task.expectedImage.set(getImage())
        }

        project.tasks.register(taskNameOf("pull"), PullImage::class.java) { task ->
            task.group = taskGroup
            task.description = "Pull image from registry"
            task.image.set(getImage())
        }

        val stopTask = project.tasks.register(taskNameOf("stop"), StopExtraContainer::class.java) { task ->
            task.group = taskGroup
            task.description = "Stop running container"
            task.dependsOn(findTask)
            task.existingContainer.set(project.provider { findTask.get().foundContainer.orNull })
            task.containerName.set(getContainerName())
        }

        project.tasks.register(taskNameOf("remove"), RemoveContainerByName::class.java) { task ->
            task.group = taskGroup
            task.description = "Remove container from Docker"
            task.containerName.set(getContainerName())
            task.dependsOn(findTask, stopTask)
            task.existingContainer.set(project.provider { findTask.get().foundContainer.orNull })
            task.containerName.set(getContainerName())
        }
    }

    val pullTask: TaskProvider<AbstractPullImage> by lazy {
        project.tasks.named(taskNameOf("pull"), AbstractPullImage::class.java)
    }

    val stopTask: TaskProvider<StopExtraContainer> by lazy {
        project.tasks.named(taskNameOf("stop"), StopExtraContainer::class.java)
    }

    val removeTask: TaskProvider<RemoveContainerByName> by lazy {
        project.tasks.named(taskNameOf("remove"), RemoveContainerByName::class.java)
    }

    val findTask: TaskProvider<FindContainer> by lazy {
        project.tasks.named(taskNameOf("find"), FindContainer::class.java)
    }

    val createTask: TaskProvider<CreateExtraContainer> by lazy {
        project.tasks.named(taskNameOf("create"), CreateExtraContainer::class.java)
    }

    val startTask: TaskProvider<StartExtraContainer> by lazy {
        project.tasks.named(taskNameOf("start"), StartExtraContainer::class.java)
    }

    protected val networkId: Property<String> = networkTask.get().networkId

    /**
     * Same as [registerCreateContainerTask] using `taskType == CreateExtraContainer`. `volumes` and `env` are wrapped into [Provider]s.
     */
    protected fun registerCreateContainerTask(
            findTask: TaskProvider<FindContainer>, volumes: Map<String, String>,
            env: ContainerEnvironment,
    ): TaskProvider<CreateExtraContainer> {
        return registerCreateContainerTask(findTask, CreateExtraContainer::class.java, project.provider { volumes },
                project.provider { env })
    }

    /**
     * Registers the task that creates the container
     * @param findTask a [TaskProvider] pointing to the [FindContainer]-task
     * @param taskType the actual task type to be created
     * @param volumes a [Provider] for the volumes to be bound. Local directories are created on demand.
     * @param env a [Provider] for the container environment to be used
     * @return a [TaskProvider] pointing to the registered task
     */
    protected fun <T> registerCreateContainerTask(
            findTask: TaskProvider<FindContainer>, taskType: Class<T>,
            volumes: Provider<Map<String, String>>,
            env: Provider<ContainerEnvironment>,
    ): TaskProvider<T> where T : CreateExtraContainer {
        return project.tasks.register(taskNameOf("create"), taskType) { task ->
            task.group = taskGroup
            task.description = "Creates the ${getContainerExt()}-container if not already existing"
            task.attachStderr.set(true)
            task.attachStdout.set(true)
            task.hostConfig.autoRemove.set(autoRemoveContainer())

            task.targetImageId(project.provider { pullTask.get().image.get() })
            task.image.set(project.provider { pullTask.get().image.get() })

            task.hostConfig.network.set(networkId)
            task.withEnvironment(env)

            task.dependsOn(findTask, networkTask, pullTask)
            task.existingContainer.set(project.provider { findTask.get().foundContainer.orNull })
            task.containerName.set(getContainerName())

            val os: OperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()
            if (useHostUser() && !os.isWindows) {
                val system = com.sun.security.auth.module.UnixSystem()
                val uid = system.uid
                val userName = system.username
                val gid = system.gid
                project.logger.info("Using user {}({}:{}) to start container {}", userName, uid, gid,
                        getContainerName())
                task.user.set(uid.toString())
            }

            task.withVolumes(volumes)

            task.doFirst {
                volumes.get().forEach { (path, _) -> File(path).mkdirs() }
            }
        }
    }

    /**
     * Same as [registerStartContainerTask] using `taskType == StartExtraContainer`.
     */
    protected fun registerStartContainerTask(
            createTask: TaskProvider<CreateExtraContainer>,
    ): TaskProvider<StartExtraContainer> {
        return registerStartContainerTask(createTask, StartExtraContainer::class.java)
    }

    /**
     * Registers the task that starts the container
     * @param createTask a [TaskProvider] pointing to the [CreateExtraContainer]-task
     * @param taskType the actual task type to be created
     * @return a [TaskProvider] pointing to the registered task
     */
    protected fun <T> registerStartContainerTask(
            createTask: TaskProvider<out CreateExtraContainer>,
            taskType: Class<T>,
    ): TaskProvider<T> where T : StartExtraContainer {
        return project.tasks.register<T>(taskNameOf("start"), taskType) { task ->
            task.group = taskGroup
            task.description = "Starts the ${getContainerExt()}-container if not already running"


            task.dependsOn(createTask, networkTask)
            task.container.set(project.provider {
                createTask.get().createdContainer.orNull
            })
        }
    }
}
