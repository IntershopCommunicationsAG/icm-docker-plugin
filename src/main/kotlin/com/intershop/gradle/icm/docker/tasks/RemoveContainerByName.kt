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
import javax.inject.Inject

/**
 * Task to remove a container by name.
 */
open class RemoveContainerByName
    @Inject constructor(objectFactory: ObjectFactory) : AbstractCommandByNameTask(objectFactory) {

    /**
     * Executes the remote Docker command.
     */
    override fun runRemoteCommand() {
        val containerIDList = getContainerIDList()

        containerIDList.forEach {
            val removeContainerCmd = dockerClient.removeContainerCmd(it)
            removeContainerCmd.withRemoveVolumes(true)
            removeContainerCmd.withForce(true)

            logger.quiet("Removing container with ID '${it}'('${containerName.get()}').")
            try {
                removeContainerCmd.exec()
            } catch(ex: ConflictException) {
                logger.info("Removal of ${it} is in progress. (${ex.message})")
            } catch(ex: NotFoundException) {
                logger.info("Can not find ${it}. (${ex.message})")
            }
        }
    }
}
