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

import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.options.Option
import javax.inject.Inject

open class StartServerContainerTask
    @Inject constructor(objectFactory: ObjectFactory) : DockerCreateContainer(objectFactory) {

    private val debugProperty: Property<Boolean> = project.objects.property(Boolean::class.java)
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


    private val jmxProperty: Property<Boolean> = project.objects.property(Boolean::class.java)
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

    private val gclogProperty: Property<Boolean> = project.objects.property(Boolean::class.java)
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

    private val heapdumpProperty: Property<Boolean> = project.objects.property(Boolean::class.java)
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

    private val appserverProperty: Property<String> = project.objects.property(String::class.java)

    @set:Option(
            option = "appserver",
            description = "Provide a special name for the appserver."
    )
    @get:Input
    var appserver: String
        get() = appserverProperty.get()
        set(value) = appserverProperty.set(value)

    private val envpropsProperty: ListProperty<String> = project.objects.listProperty(String::class.java)

    @set:Option(
            option = "envprops",
            description = "Provide a additional environment parameters for the appserver."
    )
    @get:Input
    var envprops: List<String>
        get() = envpropsProperty.get()
        set(value) = envpropsProperty.set(value)

    init {
        debugProperty.convention(false)
        jmxProperty.convention(false)
        gclogProperty.convention(false)
        heapdumpProperty.convention(false)
        appserverProperty.convention("")
        envpropsProperty.empty()
    }

    override fun runRemoteCommand() {
        this.envVars.put("ENABLE_DEBUG", "")
        this.envVars.put("ENABLE_GCLOG", "")
        this.envVars.put("ENABLE_JMX", "")
        this.envVars.put("ENABLE_HEAPDUMP", "")

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
    }
}
