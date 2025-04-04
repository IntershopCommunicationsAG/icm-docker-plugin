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
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import java.time.Duration

abstract class AbstractContainerTask :  AbstractDockerRemoteApiTask() {

    @get:Input
    val container : Property<ContainerHandle> = project.objects.property(ContainerHandle::class.java)

    /**
     * Returns a [Provider] that requests the current state of the [container].
     */
    fun currentContainerState() : Provider<ContainerHandle> {
        return project.provider { getContainer().currentState(dockerClient) }
    }

    fun getContainer() : ContainerHandle = container.get()

    /**
     * Waits for a container to be in a specific state
     * @param what the container to wait for
     * @param callback the callback to check the condition and provide additional information
     * @return the container handle representing the container's state wrapped into an Optional or an empty Optional
     * representing the container not being found
     */
    protected fun waitFor(what : ContainerHandle, callback: WaitForCallback) : ContainerHandle {
        var currHandle = what.currentState(dockerClient)
        var retryCnt = 0
        while (callback.checkCondition(currHandle, retryCnt++)) {
            project.logger.warn("Waiting for: {} ({})", callback.describeCondition(), retryCnt)
            Thread.sleep(callback.getRetryDelay().toMillis())
            currHandle = what.currentState(dockerClient)
        }
        return currHandle
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
        fun checkCondition(containerHandle : ContainerHandle, retryCnt : Int) : Boolean

        /**
         * Returns the delay between two retries
         */
        fun getRetryDelay() : Duration
    }
}
