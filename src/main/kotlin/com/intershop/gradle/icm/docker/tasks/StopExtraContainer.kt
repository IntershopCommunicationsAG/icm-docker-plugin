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

import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.exception.NotModifiedException

abstract class StopExtraContainer : AbstractExistingContainerTask() {

    init {
        this.onlyIf("Container is running") {
            val currentContainerState = currentContainerState().get()
            val isRunning = currentContainerState.isRunning()
            if (!isRunning) {
                project.logger.quiet("{} is not running, no need to stop", currentContainerState)
                return@onlyIf false
            }
            return@onlyIf true
        }
    }

    override fun runRemoteCommand() {
        val currentContainerState = currentContainerState().get()
        val stopContainerCmd = dockerClient.stopContainerCmd(currentContainerState.getContainerId())
        try {
            stopContainerCmd.exec()
            logger.quiet("Stopped {}.", currentContainerState)
        } catch (e: Exception) {
            when(e) {
                is NotFoundException, is NotModifiedException -> {
                    logger.error("Unable to stop {}.", currentContainerState, e)
                }
                else -> throw e
            }
        }
    }
}
