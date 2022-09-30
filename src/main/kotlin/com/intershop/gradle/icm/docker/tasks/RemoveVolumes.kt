/*
 * Copyright 2022 Intershop Communications AG.
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
import com.github.dockerjava.api.exception.ConflictException
import com.github.dockerjava.api.exception.NotFoundException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import javax.inject.Inject

open class RemoveVolumes @Inject constructor(objectFactory: ObjectFactory) : AbstractDockerRemoteApiTask() {

    @get:Input
    val volumeNames: ListProperty<String> = objectFactory.listProperty(String::class.java)

    init {
        volumeNames.empty()
    }

    /**
     * Executes the remote Docker command.
     */
    override fun runRemoteCommand() {
        volumeNames.get().forEach {
            try {
                dockerClient.removeVolumeCmd(it).exec()
            } catch (exnf: NotFoundException) {
                project.logger.warn("Volume '${it}' not found! ", exnf.message)
            } catch (exc: ConflictException) {
                var retry = 0
                project.logger.warn("Volume '${it}' is still used! Try it again ... ({}).", retry, exc.message)
                // try it again ...
                do {
                    Thread.sleep(10000)
                    try {
                        dockerClient.removeVolumeCmd(it).exec()
                        break
                    } catch (ex: Exception) {
                        retry++
                        project.logger.warn("Volume '${it}' is still used! Try it again ... ({}).", retry, ex.message)
                    }
                } while( retry < 5)
            }
        }
    }
}
