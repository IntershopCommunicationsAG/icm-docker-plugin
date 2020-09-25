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

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.options.Option
import javax.inject.Inject

open class StopExtraContainerTask
    @Inject constructor(objectFactory: ObjectFactory) :  AbstractCommandByName(objectFactory) {

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

    override fun runRemoteCommand() {
        val containerIDList = getContainerIDList()

        containerIDList.forEach {
            val stopContainer = dockerClient.stopContainerCmd(it)
            stopContainer.withTimeout(waitTime.getOrElse(0))

            logger.quiet("Stop container with ID '${it}'('${containerName.get()}').")

            stopContainer.exec()

            if (remove.get()) {
                val removeContainerCmd = dockerClient.removeContainerCmd(it)
                removeContainerCmd.withRemoveVolumes(true)
                removeContainerCmd.withForce(true)

                logger.quiet("Removing container with ID '${it}'('${containerName.get()}').")

                removeContainerCmd.exec()
            }
        }
    }
}
