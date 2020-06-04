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
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import javax.inject.Inject

open class RemoveContainerByName @Inject constructor(objectFactory: ObjectFactory) : AbstractDockerRemoteApiTask() {

    @get:Input
    val containerName: Property<String> = objectFactory.property(String::class.java)

    override fun runRemoteCommand() {
        val listContainerCmd = dockerClient.listContainersCmd().withNameFilter(listOf(containerName.get()))
        val listContainers = listContainerCmd.exec()
        val containerIDList = mutableListOf<String>()

        listContainers.forEach { c ->
            if(c.names.filter{ it.contains(containerName.get()) }.size > 0)  {
                containerIDList.add(c.id)
            }
        }

        containerIDList.forEach {
            val removeContainerCmd = dockerClient.removeContainerCmd(it)
            removeContainerCmd.withRemoveVolumes(true)
            removeContainerCmd.withForce(true)

            logger.quiet("Removing container with ID '${it}'('${containerName.get()}').")

            removeContainerCmd.exec()
        }
    }

}
