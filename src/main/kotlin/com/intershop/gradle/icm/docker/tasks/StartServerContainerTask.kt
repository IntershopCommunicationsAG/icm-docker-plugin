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
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.async.ResultCallbackTemplate
import com.github.dockerjava.api.model.Frame
import com.intershop.gradle.icm.docker.tasks.utils.LogContainerCallback
import org.gradle.api.GradleException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.options.Option
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.concurrent.thread

open class StartServerContainerTask
    @Inject constructor(objectFactory: ObjectFactory) : DockerCreateContainer(objectFactory) {

    private val debugProperty: Property<Boolean> = project.objects.property(Boolean::class.java)
    private val jmxProperty: Property<Boolean> = project.objects.property(Boolean::class.java)
    private val gclogProperty: Property<Boolean> = project.objects.property(Boolean::class.java)
    private val heapdumpProperty: Property<Boolean> = project.objects.property(Boolean::class.java)
    private val appserverProperty: Property<String> = project.objects.property(String::class.java)
    private val envpropsProperty: ListProperty<String> = project.objects.listProperty(String::class.java)
    private val finishedCheckProperty: Property<String> = project.objects.property(String::class.java)
    private val timeoutProperty: Property<Long> = project.objects.property(Long::class.java)

    /**
     * Enable debugging for the process. The process is started suspended and listening on port 5005.
     * This can be configured also over the gradle parameter "debug-java".
     *
     * @property debug is the task property
     */
    @set:Option(
            option = "debug-jvm",
            description = "Enable debugging for the process." +
                    "The process is started suspended and listening on port 5005."
    )
    @get:Input
    var debug: Boolean
        get() = debugProperty.get()
        set(value) = debugProperty.set(value)

    /**
     * Enable jmx port for the process. The process listening on port 7747.
     * This can be configured also over the gradle parameter "jmx".
     *
     * @property jmx is the task property
     */
    @set:Option(
            option = "jmx",
            description = "Enable jmx for the process. The process listening on port 7747."
    )
    @get:Input
    var jmx: Boolean
        get() = jmxProperty.get()
        set(value) = jmxProperty.set(value)

    /**
     * Enable gclog for the process.
     * This can be configured also over the gradle parameter "gclog".
     *
     * @property gclog is the task property
     */
    @set:Option(
            option = "gclog",
            description = "Enable gclog for the process."
    )
    @get:Input
    var gclog: Boolean
        get() = gclogProperty.get()
        set(value) = gclogProperty.set(value)

    /**
     * Enable heapdump for the process.
     * This can be configured also over the gradle parameter "heapdump".
     *
     * @property heapdump is the task property
     */
    @set:Option(
            option = "heapdump",
            description = "Enable heapdump creation for the process."
    )
    @get:Input
    var heapdump: Boolean
        get() = heapdumpProperty.get()
        set(value) = heapdumpProperty.set(value)

    /**
     * Set an special name for an appserver over
     * environment variable SERVER_NAME.
     */
    @set:Option(
            option = "appserver",
            description = "Provide a special name for the appserver."
    )
    @get:Input
    var appserver: String
        get() = appserverProperty.get()
        set(value) = appserverProperty.set(value)

    /**
     * Set environment properties to provide additional
     * environment variables.
     */
    @set:Option(
            option = "envprops",
            description = "Provide a additional environment parameters for the appserver."
    )
    @get:Input
    var envprops: List<String>
        get() = envpropsProperty.get()
        set(value) = envpropsProperty.set(value)

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
    @get:Optional
    @get:Input
    var timeout: Long
        get() = timeoutProperty.get()
        set(value) = timeoutProperty.set(value)

    init {
        debugProperty.convention(false)
        jmxProperty.convention(false)
        gclogProperty.convention(false)
        heapdumpProperty.convention(false)
        appserverProperty.convention("")
        finishedCheckProperty.convention("")
        timeoutProperty.convention(900000)
        envpropsProperty.empty()
    }

    override fun runRemoteCommand() {
        if(debugProperty.get()) {
            this.envVars.put("ENABLE_DEBUG", "true")
        }
        if(gclogProperty.get()) {
            this.envVars.put("ENABLE_GCLOG", "true")
        }
        if(jmxProperty.get()) {
            this.envVars.put("ENABLE_JMX", "")
        }
        if(heapdumpProperty.get()) {
            this.envVars.put("ENABLE_HEAPDUMP", "")
        }

        if(appserverProperty.get().isNotEmpty()) {
            this.envVars.put("SERVER_NAME", appserverProperty.get())
        }

        for (prop in envpropsProperty.get()) {
            val pl = prop.split("=")
            if(pl.size > 2) {
                this.envVars.put(pl[0], pl[1])
            } else {
                project.logger.quiet("This is not a correct parameter: {}", prop)
            }
        }

        super.runRemoteCommand()

        logger.quiet("Starting container with ID '${containerId.get()}'.")
        val startCommand = dockerClient.startContainerCmd(containerId.get())
        startCommand.exec()

        try {
            Thread.sleep(5000)
        } catch (e: Exception) {
            throw e
        }

        if(finishedCheckProperty.isPresent && finishedCheckProperty.get().isNotEmpty()) {
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
