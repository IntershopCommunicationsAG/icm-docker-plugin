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

import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.utils.Configuration
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.options.Option
import org.gradle.kotlin.dsl.getByType
import javax.inject.Inject

open class StartServerContainer
    @Inject constructor(objectFactory: ObjectFactory) : StartExtraContainer(objectFactory) {

    private val debugProperty: Property<Boolean> = objectFactory.property(Boolean::class.java)
    private val jmxProperty: Property<Boolean> = objectFactory.property(Boolean::class.java)
    private val gclogProperty: Property<Boolean> = objectFactory.property(Boolean::class.java)
    private val heapdumpProperty: Property<Boolean> = objectFactory.property(Boolean::class.java)
    private val appserverProperty: Property<String> = objectFactory.property(String::class.java)
    private val envpropsProperty: ListProperty<String> = objectFactory.listProperty(String::class.java)

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
        set(value) {
            debugProperty.set(value)
            if(value) {
                configureDebug()
            }
        }

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
        set(value) {
            jmxProperty.set(value)
            if(value) {
                envVars.put("ENABLE_JMX", "true")

                val extension = project.extensions.getByType<IntershopDockerExtension>()

                val httpJMXContainerPort = extension.developmentConfig.getConfigProperty(
                    Configuration.AS_JMX_CONNECTOR_CONTAINER_PORT,
                    Configuration.AS_JMX_CONNECTOR_CONTAINER_PORT_VALUE
                )
                val httpJMXPort = extension.developmentConfig.getConfigProperty(
                    Configuration.AS_JMX_CONNECTOR_PORT,
                    Configuration.AS_JMX_CONNECTOR_PORT_VALUE
                )

                hostConfig.portBindings.add("${httpJMXPort}:${httpJMXContainerPort}")
            }
        }

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
        set(value) {
            gclogProperty.set(value)
            if(value) {
                envVars.put("ENABLE_GCLOG", "true")
            }
        }

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
        set(value) {
            heapdumpProperty.set(value)
            if(value) {
                this.envVars.put("ENABLE_HEAPDUMP", "true")
            }
        }

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
        set(value) {
            appserverProperty.set(value)
            if(value.isNotEmpty()) {
                envVars.put("SERVER_NAME", appserverProperty.get())
            }
        }

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
        set(list) {
            envpropsProperty.set(list)
            list.forEach {  prop ->
                val pl = prop.split("=")
                if(pl.size > 2) {
                    envVars.put(pl[0], pl[1])
                } else {
                    project.logger.quiet("This is not a correct parameter: {}", prop)
                }
            }
        }

    init {
        debugProperty.convention(false)
        jmxProperty.convention(false)
        gclogProperty.convention(false)
        heapdumpProperty.convention(false)
        appserverProperty.convention("")
        envpropsProperty.empty()

        val debug = System.getProperty("debug-jvm", "false")
        if(debug != "false") {
            configureDebug()
        }
    }

    private fun configureDebug() {
        envVars.put("ENABLE_DEBUG", "true")
        hostConfig.portBindings.add("5005:7746")
    }
}
