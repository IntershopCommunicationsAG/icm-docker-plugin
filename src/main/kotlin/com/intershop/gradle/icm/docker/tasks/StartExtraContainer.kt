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
import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import com.intershop.gradle.icm.docker.tasks.utils.LogContainerCallback
import com.intershop.gradle.icm.docker.utils.PortMapping
import org.gradle.api.GradleException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.concurrent.thread

open class StartExtraContainer
    @Inject constructor(objectFactory: ObjectFactory) : DockerCreateContainer(objectFactory) {

    private val finishedCheckProperty: Property<String> = objectFactory.property(String::class.java)
    private val timeoutProperty: Property<Long> = objectFactory.property(Long::class.java)
    private val portMappings : MapProperty<String, PortMapping> =
            objectFactory.mapProperty(String::class.java, PortMapping::class.java)

    /**
     * Set an string for log file check. Log is displayed as long
     * the string was not part of the output.
     */
    @get:Optional
    @get:Input
    var finishedCheck: String
        get() = finishedCheckProperty.get()
        set(value) = finishedCheckProperty.set(value)

    /**
     * Milliseconds for waiting on the finish string.
     * Default is 600000.
     */
    @get:Input
    var timeout: Long
        get() = timeoutProperty.get()
        set(value) = timeoutProperty.set(value)

    /**
     * Returns the primary port mapping if there is such a port mapping
     */
    @Internal
    fun getPrimaryPortMapping() : PortMapping? =
        this.portMappings.get().values.firstOrNull { mapping -> mapping.primary }

    /**
     * Returns all port mappings
     */
    @Internal
    fun getPortMappings() : Set<PortMapping> =
            this.portMappings.get().values.toSet()

    /**
     * Adds port mapping to be used with the container
     */
    fun withPortMappings(vararg portMappings: PortMapping) {
        portMappings.forEach{ portMapping ->
            // check if there's already a primary port mapping
            if (portMapping.primary && getPrimaryPortMapping() != null) {
                throw GradleException("Duplicate primary port mapping detected for task $name")
            }

            this.portMappings.put(portMapping.name, portMapping)
            hostConfig.portBindings.add(project.provider { portMapping.render() })
        }
    }

    init {
        finishedCheckProperty.convention("")
        timeoutProperty.convention(900000)
    }

    override fun runRemoteCommand() {
        var containerCreated = false
        var containerRunning = false

        val iterator = dockerClient.listContainersCmd().withShowAll(true).
                            withNameFilter(listOf("/${containerName.get()}")).exec().iterator()

        while (iterator.hasNext()) {
            val container = iterator.next()
            if(container.names.contains("/${containerName.get()}")) {
                if (container.image != image.get()) {
                    throw GradleException(
                        "The running container was started with image '" + container.image +
                                "', but the configured image is '" + image.get() +
                                "'. Please remove running containers!"
                    )
                }

                containerId.set(container.id)
                containerCreated = true
                containerRunning = (container.state == "running")
            }
        }

        if(! containerRunning) {
            if (!containerCreated) {
                super.runRemoteCommand()
            } else {
                logger.quiet("Container '{}' still exists.", "/${containerName.get()}")
            }

            logger.quiet("Starting container with ID '${containerId.get()}'.")
            val startCommand = dockerClient.startContainerCmd(containerId.get())
            startCommand.exec()

            if(finishedCheckProperty.get().isNotBlank()) {
                try {
                    Thread.sleep(5000)
                } catch (e: Exception) {
                    throw e
                }

                waitForLogout()
            }
        } else {
            logger.quiet("Container '{}' is still running.", "/${containerName.get()}")
        }
    }

    protected fun waitForLogout() {
        if (finishedCheckProperty.isPresent && finishedCheckProperty.get().isNotEmpty()) {
            logger.quiet("Starting logging for container with ID '${containerId.get()}'.")
            val logCommand = dockerClient.logContainerCmd(containerId.get())
            logCommand.withStdErr(true)
            logCommand.withStdOut(true)
            logCommand.withTailAll()
            logCommand.withFollowStream(true)

            val localProbe = ExecProbe(timeoutProperty.get(), 5000)

            // create progressLogger for pretty printing of terminal log progression.
            val progressLogger = IOUtils.getProgressLogger(project, this.javaClass)
            try {
                var localPollTime = localProbe.pollTime
                var pollTimes = 0

                progressLogger.started()
                val containerCallback = LogContainerCallback(project.logger, finishedCheckProperty.get())

                thread(start = true) {
                    try {
                        logCommand.exec(containerCallback).awaitCompletion()
                    } catch (ex: Exception) {
                        logger.quiet("Log command finished.")
                    }
                }

                while (localPollTime > 0) {
                    pollTimes += 1
                    val totalMillis = pollTimes * localProbe.pollInterval
                    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(totalMillis)

                    progressLogger.progress("Executing for ${totalMinutes}m...")
                    if (containerCallback.startSuccessful) {
                        logger.quiet("Container startet successfully in a expected time.")
                        containerCallback.close()
                        localPollTime = -1
                    } else {
                        try {
                            localPollTime -= localProbe.pollInterval
                            Thread.sleep(localProbe.pollInterval)
                        } catch (e: Exception) {
                            logger.error("It is not possible to wait for logging.")
                        }
                    }
                }

                if (!containerCallback.startSuccessful) {
                    logger.error("Container not startet successfully in a expected time.")
                    containerCallback.close()
                    throw GradleException("Container not startet successfully in a expected time.")
                }
            } finally {
                progressLogger.completed()
            }
        }
    }
}
