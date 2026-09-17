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
package com.intershop.gradle.icm.docker.tasks.utils

import com.github.dockerjava.api.DockerClient
import com.intershop.gradle.icm.docker.tasks.utils.ContainerLogWatcher.Handle
import org.gradle.api.logging.Logger
import kotlin.concurrent.thread

/**
 * Encapsulates the functionality execute `docker logs`-command for a running container. The container logs are
 * logged using the given [Logger] inside a new [Thread] (so [start] does not block). Calling code has to ensure
 * [Handle.close] is called to actually finish the `docker logs`-command.
 *
 * NOTE: this class deliberately takes a [Logger] instead of the `Project`. It is used at task execution time, where
 * accessing `Task.project` is deprecated in Gradle 9 and fails in Gradle 10 (configuration cache incompatible).
 */
class ContainerLogWatcher(
        private val logger: Logger,
        private val dockerClient: DockerClient,
) {

    /**
     * Starts the `docker logs`-command
     * @return a [Handle] the has to be used to close this [ContainerLogWatcher] and actually end the
     * `docker logs`-command.
     *
     * @param containerId the id of the container
     */
    fun start(containerId: String): Handle {
        val logCommand = dockerClient.logContainerCmd(containerId)
        logCommand.withStdErr(true)
        logCommand.withStdOut(true)
        logCommand.withTailAll()
        logCommand.withFollowStream(true)
        val containerCallback = RedirectToLoggerCallback(logger)

        val thread = thread(start = true) {
            try {
                logCommand.exec(containerCallback).awaitCompletion()
            } catch (ex: Exception) {
                logger.info("Log command finished.")
            }
        }

        return object : Handle {
            override fun close() {
                thread.interrupt() // ensure thread is stopped
                containerCallback.close()
            }
        }
    }

    /**
     * Encapsulates the actually [ContainerLogWatcher] closing.
     * @see start
     */
    interface Handle : AutoCloseable
}
