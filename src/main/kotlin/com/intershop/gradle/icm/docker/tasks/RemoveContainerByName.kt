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
import org.gradle.api.provider.ProviderFactory
import org.gradle.work.DisableCachingByDefault
import java.time.Duration
import javax.inject.Inject

/**
 * Task to remove a container by name.
 */
@DisableCachingByDefault(because = "Interacts with a live Docker daemon - container, image, network and " +
        "volume state is external to the build and must never be taken from the build cache")
abstract class RemoveContainerByName
@Inject constructor(
        objectFactory: ObjectFactory,
        providerFactory: ProviderFactory,
) : AbstractExistingContainerTask(objectFactory, providerFactory) {

    /*
        this.onlyIf("Container exists ")...

        defined by AbstractExistingContainerTask
    */

    override fun runRemoteCommand() {
        val currentContainerState = currentContainerState().get()
        if (!currentContainerState.exists()){
            logger.quiet("{} does not exist, no need to remove", getContainer())
            return
        }

        val removeContainerCmd = dockerClient.removeContainerCmd(currentContainerState.getContainerId())
        try {
            removeContainerCmd.exec()
            logger.quiet("Removed {}.", currentContainerState)
        } catch(ex: ConflictException) {
            logger.warn("Removal of {} already is in progress. ({})", currentContainerState, ex.message)
            waitFor(currentContainerState, object: WaitForCallback {
                override fun checkCondition(containerHandle: ContainerHandle, retryCnt: Int): Boolean {
                    return !containerHandle.exists() && (retryCnt < 5)
                }

                override fun getRetryDelay(): Duration = Duration.ofSeconds(5)

                override fun describeCondition(): String {
                    return "removal of container"
                }
            })
            logger.quiet("Removal of {} finished.", currentContainerState)
        } catch(ex: NotFoundException) {
            logger.quiet("{} is already removed. ({})", currentContainerState, ex.message)
        }
    }

}
