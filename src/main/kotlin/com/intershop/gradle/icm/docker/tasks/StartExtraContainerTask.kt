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

import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import org.gradle.api.GradleException
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

open class StartExtraContainerTask
    @Inject constructor(objectFactory: ObjectFactory) : DockerCreateContainer(objectFactory) {

    override fun runRemoteCommand() {
        var containerCreated = false
        var containerRunning = false

        val iterator = dockerClient.listContainersCmd().withShowAll(true).
                            withNameFilter(listOf("/${containerName.get()}")).exec().iterator()

        while (iterator.hasNext()) {
            val container = iterator.next()
            if(container.names.contains("/${containerName.get()}")) {
                if (container.image != image.get()) {
                    throw GradleException(
                        "The running container was started with image '" + container.image +
                                "', but the configured image is '" + image.get() + "'. Please remove running containers!"
                    )
                }

                containerCreated = true
                containerRunning = (container.state == "running")
            }
        }

        if(! containerRunning) {
            if (!containerCreated) {
                super.runRemoteCommand()
            } else {
                logger.quiet("Container '{}' still exists.", "/${containerName.get()}")
            }

            logger.quiet("Starting container with ID '${containerId.get()}'.")
            val startCommand = dockerClient.startContainerCmd(containerId.get())
            startCommand.exec()
        } else {
            logger.quiet("Container '{}' is still running.", "/${containerName.get()}")
        }
    }
}
