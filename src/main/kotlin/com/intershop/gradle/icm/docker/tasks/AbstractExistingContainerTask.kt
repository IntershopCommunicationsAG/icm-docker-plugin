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

import com.bmuschko.gradle.docker.domain.ExecProbe
import com.bmuschko.gradle.docker.internal.IOUtils
import com.github.dockerjava.api.command.InspectExecResponse
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskProvider
import java.util.concurrent.TimeUnit

abstract class AbstractExistingContainerTask : AbstractContainerTask() {

    init {
        this.onlyIf("Container exists") {
            val currentContainerState = currentContainerState().get()
            if (!currentContainerState.exists()) {
                project.logger.quiet("{} does not exist", currentContainerState)
                return@onlyIf false
            }
            return@onlyIf true
        }
    }

    fun executeUsing(startContainerTaskProvider: TaskProvider<StartExtraContainer>) {
        container.value(project.provider { startContainerTaskProvider.get().container.get() })
        dependsOn(startContainerTaskProvider)
    }

    protected fun waitForExit(localExecId: String): Long {

        // create progressLogger for pretty printing of terminal log progression.
        val progressLogger = IOUtils.getProgressLogger(services, this.javaClass)
        progressLogger.started()

        // if no livenessProbe defined then create a default
        val localProbe = ExecProbe(6000000, 50000)

        var localPollTime = localProbe.pollTime
        var pollTimes = 0
        var isRunning = true

        // 3.) poll for some amount of time until container is in a non-running state.
        var lastExecResponse: InspectExecResponse = dockerClient.inspectExecCmd(localExecId).exec()

        while (isRunning && localPollTime > 0) {
            pollTimes += 1

            lastExecResponse = dockerClient.inspectExecCmd(localExecId).exec()
            isRunning = lastExecResponse.isRunning

            if (isRunning) {
                val totalMillis = pollTimes * localProbe.pollInterval
                val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(totalMillis)

                progressLogger.progress("Executing for ${totalMinutes}m...")
                try {
                    localPollTime -= localProbe.pollInterval
                    Thread.sleep(localProbe.pollInterval)
                } catch (e: Exception) {
                    throw e
                }
            } else {
                break
            }
        }
        progressLogger.completed()

        // if still running then throw an exception otherwise check the exitCode
        if (isRunning) {
            throw GradleException("Command did not finish in a timely fashion: $localProbe")
        }

        return lastExecResponse.exitCodeLong
    }
}
