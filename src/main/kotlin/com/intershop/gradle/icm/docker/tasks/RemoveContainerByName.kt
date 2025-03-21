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

import com.github.dockerjava.api.exception.ConflictException
import com.github.dockerjava.api.exception.NotFoundException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import java.time.Duration
import javax.inject.Inject

/**
 * Task to remove a container by name.
 */
open class RemoveContainerByName
    @Inject constructor(objectFactory: ObjectFactory) : AbstractCommandByNameTask(objectFactory) {

    @get:Input
    @get:Optional
    val existingContainer: Property<ContainerHandle> = objectFactory.property(ContainerHandle::class.java)

    init {
        this.onlyIf("Container exists ") {
            val containerExists = existingContainer.isPresent
            if (!containerExists) {
                project.logger.quiet("Container '{}' does not exist, no need to remove", containerName.get())
                return@onlyIf false
            }
            return@onlyIf true
        }
    }

    override fun runRemoteCommand() {
        val removeContainerCmd = dockerClient.removeContainerCmd(existingContainer.get().getContainerId())
        try {
            removeContainerCmd.exec()
            logger.quiet("Removed {}.", existingContainer.get())
        } catch(ex: ConflictException) {
            logger.warn("Removal of {} already is in progress. ({})", existingContainer.get(), ex.message)
            waitFor(existingContainer.get(), object: WaitForCallback {
                override fun checkCondition(optContainerHandle: java.util.Optional<ContainerHandle>, retryCnt: Int): Boolean {
                    return !optContainerHandle.isPresent && (retryCnt < 5)
                }

                override fun getRetryDelay(): Duration = Duration.ofSeconds(5)

                override fun describeCondition(): String {
                    return "removal of container"
                }
            })
            logger.quiet("Removal of {} finished.", existingContainer.get())
        } catch(ex: NotFoundException) {
            logger.quiet("{} is already removed. ({})", existingContainer.get(), ex.message)
        }
    }

}
