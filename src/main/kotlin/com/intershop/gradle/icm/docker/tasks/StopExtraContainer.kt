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

package com.intershop.gradle.icm.docker.tasks

import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.exception.NotModifiedException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.options.Option
import javax.inject.Inject

open class StopExtraContainer
    @Inject constructor(objectFactory: ObjectFactory) :  AbstractCommandByNameTask(objectFactory) {

    /**
     * Stop timeout in seconds.
     */
    @get:Input
    @get:Optional
    val waitTime: Property<Int> = objectFactory.property(Int::class.java)

    @get:Option(option = "remove", description = "Container will be removed with a stop call.")
    @get:Input
    val remove: Property<Boolean> = objectFactory.property(Boolean::class.java)

    init {
        remove.set(false)
    }

    @get:Input
    @get:Optional
    val existingContainer: Property<ContainerHandle> = objectFactory.property(ContainerHandle::class.java)

    init {
        this.onlyIf("Container exists and is running") {
            val containerExists = existingContainer.isPresent
            if (!containerExists) {
                project.logger.quiet("Container '{}' does not exist, no need to stop", containerName.get())
                return@onlyIf false
            }
            val isRunning = existingContainer.get().isRunning()
            if (!isRunning) {
                project.logger.quiet("Container '{}' is not running, no need to stop", containerName.get())
                return@onlyIf false
            }
            return@onlyIf true
        }
    }

    override fun runRemoteCommand() {
        val stopContainerCmd = dockerClient.stopContainerCmd(existingContainer.get().getContainerId())
        try {
            stopContainerCmd.exec()
            logger.quiet("Stopped {}.", existingContainer.get())
        } catch (e: Exception) {
            when(e) {
                is NotFoundException, is NotModifiedException -> {
                    logger.error("Unable to stop {}.", existingContainer.get(), e)
                }
                else -> throw e
            }
        }
    }
}
