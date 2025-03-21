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
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import java.time.Duration
import java.util.Optional
import javax.inject.Inject

abstract class AbstractCommandByNameTask
    @Inject constructor(objectFactory: ObjectFactory) :  AbstractDockerRemoteApiTask() {

    @get:Input
    val containerName: Property<String> = objectFactory.property(String::class.java)

    /**
     * Finds a container by the data provided using parameter `expected`
     */
    protected fun findContainer(expected : ContainerHandle) : Optional<ContainerHandle> {
        val containers =
            dockerClient.listContainersCmd().withShowAll(true).withNameFilter(listOf("/${expected.getContainerName()}"))
                .exec()

        for (c in containers) {
            val expectedImage = expected.getContainerImage()
            if (c.names.contains("/${expected.getContainerName()}")) {
                if (c.image != expectedImage) {
                    throw GradleException(
                        "The running container was started with image '${c.image}', but the configured image is " +
                                "'${expectedImage}'. Please remove running containers!")
                }
                return Optional.of(ContainerHandle.of(c))
            }
        }
        return Optional.empty()
    }

    /**
     * Waits for a container to be in a specific state
     * @param what the container to wait for
     * @param callback the callback to check the condition and provide additional information
     * @return the container handle representing the container's state wrapped into an Optional or an empty Optional
     * representing the container not being found
     */
    protected fun waitFor(what : ContainerHandle, callback: WaitForCallback) : Optional<ContainerHandle> {
        var optHandle = findContainer(what)
        var retryCnt = 0
        while (callback.checkCondition(optHandle, retryCnt++)) {
            project.logger.warn("Waiting for: {} ({})", callback.describeCondition(), retryCnt)
            Thread.sleep(callback.getRetryDelay().toMillis())
            optHandle = findContainer(what)
        }
        return optHandle
    }

    /**
     * Callback interface for the `waitFor` method
     * @see waitFor
     */
    interface WaitForCallback {
        /**
         * Returns a description of the condition to be checked (used for logging)
         */
        fun describeCondition() : String

        /**
         * Checks the condition and returns `true` if the condition is met e.g. the container is running
         */
        fun checkCondition(optContainerHandle : Optional<ContainerHandle>, retryCnt : Int) : Boolean

        /**
         * Returns the delay between two retries
         */
        fun getRetryDelay() : Duration
    }
}
