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
import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
import com.intershop.gradle.icm.docker.tasks.utils.ContainerLogWatcher
import com.intershop.gradle.icm.docker.tasks.utils.LogContainerCallback
import com.intershop.gradle.icm.utils.HttpProbe
import com.intershop.gradle.icm.utils.Probe
import com.intershop.gradle.icm.utils.SocketProbe
import org.gradle.api.GradleException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import java.net.URI
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.concurrent.thread

open class StartExtraContainer
@Inject constructor(objectFactory: ObjectFactory) : DockerStartContainer() {

    /**
     * Set a string for log file check. Log is displayed as long
     * the string was not part of the output.
     */
    @get:Optional
    @get:Input
    val finishedCheck: Property<String> = objectFactory.property(String::class.java)

    /**
     * Max duration for the container to start up (only evaluated if [finishedCheck] is set)
     */
    @get:Input
    val startupTimeout: Property<Duration> =
            objectFactory.property(Duration::class.java).convention(Duration.ofSeconds(900))

    @get:Input
    val probes: ListProperty<Probe> = project.objects.listProperty(Probe::class.java)

    fun withHttpProbe(uri: URI, retryInterval: Duration, retryTimeout: Duration) {
        withProbes(
                HttpProbe(project, { services }, uri).withRetryInterval(retryInterval).withRetryTimeout(retryTimeout)
        )
    }

    fun withSocketProbe(port: Int, retryInterval: Duration, retryTimeout: Duration) {
        withProbes(
                SocketProbe.toLocalhost(
                        project,
                        { services },
                        port
                ).withRetryInterval(retryInterval).withRetryTimeout(retryTimeout)
        )
    }

    /**
     * Configures this task to (additionally) use the given `probes`
     */
    fun withProbes(vararg probes: Probe) {
        this.probes.addAll(probes.toList())
    }

    @get:Input
    val enableLogWatcher: Property<Boolean> = objectFactory.property(Boolean::class.java).convention(false)

    @get:Input
    val container: Property<ContainerHandle> = project.objects.property(ContainerHandle::class.java)

    init {
        containerId.convention(project.provider {
            if (container.isPresent) {
                container.get().getContainerId()
            } else {
                null
            }
        })
        @Suppress("LeakingThis")
        onlyIf("Container not running") {
            val containerRuns = isAlreadyRunning()
            if (containerRuns) {
                project.logger.quiet("{} still is running, skipping to start it", container.get())
            }
            !containerRuns
        }
    }

    override fun runRemoteCommand() {
        if (isAlreadyRunning()) {
            throw GradleException("Expecting ${container.get()} to not currently run but it does.")
        }

        logger.quiet("Starting {}.", container.get())

        val startCommand = dockerClient.startContainerCmd(containerId.get())
        startCommand.exec()

        val logWatcherHandle: AutoCloseable? = if (enableLogWatcher.get()) {
            ContainerLogWatcher(project, dockerClient).start(containerId.get())
        } else {
            null
        }

        try {
            with(probes.get()) {
                forEach { probe ->
                    val success = probe.execute()
                    if (!success) {
                        throw GradleException(
                                "Container ${container.get()} failed to start properly: probe $probe failed")
                    }
                    project.logger.debug("Probe '{}' was executed successfully on {}.",
                            probe, container.get())
                }
                project.logger.quiet("{} started properly.", container.get())
            }


            // TODO remove when all containers use probes
            if (finishedCheck.isPresent) {
                try {
                    Thread.sleep(5000)
                } catch (e: Exception) {
                    throw e
                }

                waitForLogout()
            }
        } catch (e: Exception) {
            onFailure(e)
        } finally {
            logWatcherHandle?.close()
        }
    }

    @Internal
    protected fun getContainerName(): String? = container.map { c -> c.getContainerName() }.orNull

    @Internal
    protected fun isAlreadyRunning(): Boolean = container.map { c -> c.isRunning() }.getOrElse(false)

    protected fun waitForLogout() {
        if (finishedCheck.isPresent && finishedCheck.get().isNotEmpty()) {
            logger.quiet("Starting logging for container with ID '${containerId.get()}'.")
            val logCommand = dockerClient.logContainerCmd(containerId.get())
            logCommand.withStdErr(true)
            logCommand.withStdOut(true)
            logCommand.withTailAll()
            logCommand.withFollowStream(true)

            val localProbe = ExecProbe(startupTimeout.get().toMillis(), 5000)

            // create progressLogger for pretty printing of terminal log progression.
            val progressLogger = IOUtils.getProgressLogger(services, this.javaClass)
            try {
                var localPollTime = localProbe.pollTime
                var pollTimes = 0

                progressLogger.started()
                val containerCallback = LogContainerCallback(project.logger, finishedCheck.get())

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
                    logger.error("Container not started successfully in a expected time.")
                    containerCallback.close()
                    throw GradleException("Container not started successfully in a expected time.")
                }
            } finally {
                progressLogger.completed()
            }
        }
    }

    protected fun onFailure(cause: Exception) {
        project.logger.quiet("Stopping failed {}", container.get())
        dockerClient.stopContainerCmd(container.get().getContainerId()).exec()
        throw cause
    }
}
