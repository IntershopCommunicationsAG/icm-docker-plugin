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
import com.intershop.gradle.icm.docker.tasks.utils.ICMContainerEnvironmentBuilder
import com.intershop.gradle.icm.docker.utils.Configuration
import com.intershop.gradle.icm.docker.utils.HostAndPort
import com.intershop.gradle.icm.utils.JavaDebugSupport
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.options.Option
import org.gradle.api.tasks.options.OptionValues
import org.gradle.kotlin.dsl.getByType
import javax.inject.Inject

open class StartServerContainer
@Inject constructor(objectFactory: ObjectFactory) : StartExtraContainer(objectFactory) {

    private val debugProperty: Property<JavaDebugSupport> = objectFactory.property(JavaDebugSupport::class.java)
    private val gcLogProperty: Property<Boolean> = objectFactory.property(Boolean::class.java)
    private val heapDumpProperty: Property<Boolean> = objectFactory.property(Boolean::class.java)
    private val appserverNameProperty: Property<String> = objectFactory.property(String::class.java)
    private val solrCloudZookeeperHostListProperty: Property<String> = objectFactory.property(String::class.java)
    private val mailServerProperty: Property<HostAndPort> = objectFactory.property(HostAndPort::class.java)

    companion object {
        const val PATTERN_READINESS_PROBE_URL = "http://localhost:%d/status/ReadinessProbe"
    }

    /**
     * Enable debugging for the JVM running the ICM-AS inside the container. This option defaults to the value
     * of the JVM property [SYSPROP_DEBUG_JVM] respectively `false` if not set.
     * The port on the host can be configured using the property `icm.properties/intershop.as.debug.port`
     *
     * @property debug is the task property
     * @see com.intershop.gradle.icm.docker.utils.Configuration.AS_DEBUG_PORT
     */
    @set:Option(
            option = "debug-icm",
            description = "Enable/control debugging for the process. The following values are supported: " +
                          "${JavaDebugSupport.TASK_OPTION_VALUE_TRUE}/${JavaDebugSupport.TASK_OPTION_VALUE_YES} - " +
                          "enable debugging, ${JavaDebugSupport.TASK_OPTION_VALUE_SUSPEND} - enable debugging in " +
                          "suspend-mode, every other value - disable debugging. The debugging port is controlled by " +
                          "icm-property '${Configuration.AS_DEBUG_PORT}'."
    )
    @get:Input
    var debug: String
        get() = debugProperty.map { it.renderTaskOptionValue() }.getOrElse("")
        set(value) {
            val debugOptions = JavaDebugSupport.parse(project, value)
            debugProperty.set(debugOptions)
            withEnvironment(ICMContainerEnvironmentBuilder().withDebugOptions(debugOptions).build())
        }

    /**
     * Return the possible values for the task option [debug]
     */
    @OptionValues("debug-icm")
    fun getDebugOptionValues(): Collection<String> = listOf(JavaDebugSupport.TASK_OPTION_VALUE_TRUE,
            JavaDebugSupport.TASK_OPTION_VALUE_YES,
            JavaDebugSupport.TASK_OPTION_VALUE_SUSPEND, JavaDebugSupport.TASK_OPTION_VALUE_FALSE,
            JavaDebugSupport.TASK_OPTION_VALUE_NO)

    /**
     * Enable GC logging for the process.
     *
     * @property gcLog is the task property
     */
    @set:Option(
            option = "gclog",
            description = "Enable gclog for the process."
    )
    @get:Input
    var gcLog: Boolean
        get() = gcLogProperty.get()
        set(value) {
            gcLogProperty.set(value)
            withEnvironment(ICMContainerEnvironmentBuilder().enableGCLog(value).build())
        }

    /**
     * Enable heap dumps for the process.
     *
     * @property heapDump is the task property
     */
    @set:Option(
            option = "heapdump",
            description = "Enable heapdump creation for the process."
    )
    @get:Input
    var heapDump: Boolean
        get() = heapDumpProperty.get()
        set(value) {
            heapDumpProperty.set(value)
            withEnvironment(ICMContainerEnvironmentBuilder().enableHeapDump(value).build())
        }

    /**
     * Set a special name for the appserver
     */
    @set:Option(
            option = "appserver-name",
            description = "Provide a special name for the appserver."
    )
    @get:Input
    var appserverName: String
        get() = appserverNameProperty.get()
        set(value) {
            appserverNameProperty.set(value)
            withEnvironment(ICMContainerEnvironmentBuilder().withServerName(value).build())
        }

    /**
     * Provide the host list of the Zookeeper required by Solr Cloud
     */
    @get:Optional
    @get:Input
    var solrCloudZookeeperHostList : Provider<String>
        get() = solrCloudZookeeperHostListProperty
        set(value) {
            solrCloudZookeeperHostListProperty.set(value)
            withEnvironment(ICMContainerEnvironmentBuilder().withSolrCloudZookeeperHostList(value).build())
        }

    /**
     * Provide the smtp host
     */
    @get:Optional
    @get:Input
    var mailServer : Provider<HostAndPort>
        get() = mailServerProperty
        set(value) {
            mailServerProperty.set(value)
            withEnvironment(ICMContainerEnvironmentBuilder().withMailServer(value).build())
        }

    init {
        debugProperty.convention(JavaDebugSupport.defaults(project))
        gcLogProperty.convention(false)
        heapDumpProperty.convention(false)
        appserverNameProperty.convention("")
        enableLogWatcher.convention(true)

        val devConfig = project.extensions.getByType<IntershopDockerExtension>().developmentConfig
        withEnvironment(
                ICMContainerEnvironmentBuilder()
                        .withDatabaseConfig(devConfig.databaseConfiguration)
                        .withPortConfig(devConfig.asPortConfiguration)
                        .withCartridgeList(devConfig.cartridgeList.get())
                        .withClasspathLayout(setOf(
                                ICMContainerEnvironmentBuilder.ClasspathLayout.SOURCE,
                                ICMContainerEnvironmentBuilder.ClasspathLayout.RELEASE
                        ))
                        .build()
        )
    }

}
