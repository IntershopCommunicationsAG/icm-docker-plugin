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

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import com.github.dockerjava.api.model.Container
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional

abstract class FindContainer : AbstractDockerRemoteApiTask() {

    @get:Input
    val containerName: Property<String> = project.objects.property(String::class.java)

    @get:Optional
    @get:Input
    val expectedImage: Property<String> = project.objects.property(String::class.java)

    @get:Internal
    val foundContainer: Property<ContainerHandle> = project.objects.property(ContainerHandle::class.java)

    /**
     * Checks if the container exists
     * @return `true` if the container exists
     */
    fun containerExists(): Boolean = foundContainer.isPresent

    @Internal
    fun getContainer(): ContainerHandle = foundContainer.get()

    override fun runRemoteCommand() {
        foundContainer.set(null as ContainerHandle?)

        val containers: MutableList<Container> =
                dockerClient.listContainersCmd().withShowAll(true).withNameFilter(listOf("/${containerName.get()}"))
                        .exec()

        for (c in containers) {
            if (c.names.contains("/${containerName.get()}")) {
                if (expectedImage.isPresent && c.image != expectedImage.get()) {
                    throw GradleException(
                            "The running container was started with image '${c.image}', but the configured image is " +
                            "'${expectedImage.get()}'. Please remove running containers!")
                }
                foundContainer.set(ContainerHandle.of(c))

                break
            }
        }

        if (foundContainer.isPresent) {
            project.logger.quiet("Container '{}' exists using id={} ({})", containerName.get(),
                    getContainer().getContainerId(), if (getContainer().isRunning()) {
                "RUNNING"
            } else {
                "NOT RUNNING"
            })
        } else {
            project.logger.quiet("Container '{}' does not exist", containerName.get())
        }
    }
}