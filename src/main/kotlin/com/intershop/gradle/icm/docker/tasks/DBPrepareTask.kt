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

import com.intershop.gradle.icm.docker.extension.DevelopmentConfiguration
import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.tasks.utils.DBPrepareCallback
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.options.Option
import org.gradle.kotlin.dsl.getByType
import javax.inject.Inject

/**
 * Task to run dbinit on a running container.
 */
open class DBPrepareTask
    @Inject constructor(project: Project) : AbstractContainerTask() {

    companion object {
        const val ENV_IS_DBPREPARE = "IS_DBPREPARE"
        const val ENV_ENABLE_DEBUG = "ENABLE_DEBUG"
        const val ENV_DB_TYPE = "INTERSHOP_DATABASETYPE"
        const val ENV_DB_JDBC_URL = "INTERSHOP_JDBC_URL"
        const val ENV_DB_JDBC_USER = "INTERSHOP_JDBC_USER"
        const val ENV_DB_JDBC_PASSWORD = "INTERSHOP_JDBC_PASSWORD"
        const val ENV_CARTRIDGE_LIST = "CARTRIDGE_LIST"
        const val ENV_CARTRIDGE_CLASSPATH_LAYOUT = "CARTRIDGE_CLASSPATH_LAYOUT"
        const val ENV_VALUE_CARTRIDGE_CLASSPATH_LAYOUT = "release,source"
        const val COMMAND = "/intershop/bin/intershop.sh"

        const val SYSPROP_DEBUG_JVM = "debug-jvm"
    }
    private val debugProperty: Property<Boolean> = project.objects.property(Boolean::class.java)

    @get:Option(option = "mode", description = "Mode in which dbPrepare runs: 'init', 'migrate' or 'auto'. " +
                                               "The default is 'auto'.")
    @get:Input
    val mode: Property<String> = project.objects.property(String::class.java)

    @get:Option(option = "clean-db", description = "can be 'only', 'yes' or 'no', default is 'no'. In case of 'only'," +
                                                   " only the database is cleaned up. If 'yes' is shown, the database is cleaned up before preparing other " +
                                                   " steps. If 'no' is displayed, no database cleanup is done.")
    @get:Input
    val cleanDB: Property<String> = project.objects.property(String::class.java)

    @get:Option(option = "cartridges", description = "A comma-separated cartridge list. Executes the cartridges in " +
                                                     "that list. This is an optional parameter.")
    @get:Input
    val cartridges: Property<String> = project.objects.property(String::class.java)

    @get:Option(option = "property-keys", description = "Comma-separated list of preparer property keys to execute. " +
                                                        "This is an optional parameter.")
    @get:Input
    val propertyKeys: Property<String> = project.objects.property(String::class.java)

    @get:Input
    val databaseConfiguration: Property<DevelopmentConfiguration.DatabaseParameters> by lazy {
        project.objects.property(DevelopmentConfiguration.DatabaseParameters::class.java)
                .value(project.extensions.getByType<IntershopDockerExtension>().developmentConfig.databaseConfiguration)
    }

    /**
     * The cartridge list to be used
     */
    @get: Input
    val cartridgeList : SetProperty<String> by lazy {
        project.extensions.getByType<IntershopDockerExtension>().developmentConfig.cartridgeList
    }

    /**
     * Enable debugging for the JVM running the ICM-AS inside the container. This option defaults to the value
     * of the JVM property [SYSPROP_DEBUG_JVM] respectively `false` if not set.
     * The port on the host can be configured using the property `icm.properties/intershop.as.debug.port`
     *
     * @see com.intershop.gradle.icm.docker.utils.Configuration.AS_DEBUG_PORT
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

    init {
        mode.convention("auto")
        cleanDB.convention("no")
        cartridges.convention("")
        propertyKeys.convention("")
        debugProperty.convention(project.provider { System.getProperty(SYSPROP_DEBUG_JVM, "false").equals("true", ignoreCase = true) })

        group = "icm docker project"
    }

    /**
     * Executes the remote Docker command.
     */
    override fun runRemoteCommand() {
        val execCallback = createCallback()

        val execCmd = dockerClient.execCreateCmd(containerId.get())
        execCmd.withAttachStderr(true)
        execCmd.withAttachStdout(true)
        val command = arrayOf("/bin/sh", "-c", COMMAND)
        execCmd.withCmd(*command)

        val additionalParameters = AdditionalParameters()
                .add("--mode", mode)
                .add("--clean-db", cleanDB)

        if (cartridges.get().trim().isNotEmpty()) {
            additionalParameters.add("--cartridges", cartridges.get().replace(" ", ""))
        }
        if (propertyKeys.get().isNotEmpty()) {
            additionalParameters.add("--property-keys", propertyKeys.get().replace(" ", ""))
        }

        val env = Env()
        env.add(ENV_IS_DBPREPARE, true)

        // configure debugging
        if (debugProperty.get()){
            env.add(ENV_ENABLE_DEBUG, true)
        }

        // add database config to env
        databaseConfiguration.get().run {
            env.add(ENV_DB_TYPE, type.get()).add(ENV_DB_JDBC_URL, jdbcUrl.get()).add(ENV_DB_JDBC_USER, jdbcUser.get())
                    .add(ENV_DB_JDBC_PASSWORD, jdbcPassword.get())
        }

        // add cartridge list (values separated by space)
        env.add(ENV_CARTRIDGE_LIST, cartridgeList.get().joinToString(separator = " "))

        // ensure release (product cartridges) and source (customization cartridges) layouts are recognized
        env.add(ENV_CARTRIDGE_CLASSPATH_LAYOUT, ENV_VALUE_CARTRIDGE_CLASSPATH_LAYOUT)

        execCmd.withEnv(env.toList())

        project.logger.quiet("Attempting to execute command '{}' on container {} using {}", command, containerId.get(), env)

        val localExecId = execCmd.exec().id

        dockerClient.execStartCmd(localExecId).withDetach(false).exec(execCallback).awaitCompletion()

        if (waitForExit(localExecId) > 0) {
            throw GradleException("DBPrepare failed! Please check your log files")
        }

        val info = execCallback.getDBInfo()

        if (info == null) {
            throw GradleException("DBPrepare didn't finish correctly! Please check your log files")
        } else {
            if (info.failure > 0) {
                throw GradleException("DBPrepare failed with '" + info.failure + "' failures. " +
                                      "Please check your log files")
            }
        }
    }

    private fun createCallback(): DBPrepareCallback {
        return DBPrepareCallback(System.out, System.err)
    }

    internal class Env {
        private val entries: MutableMap<String, String> = mutableMapOf()

        fun <V> add(key: String, value: V): Env {
            if (value != null) {
                this.entries[key] = value.toString()
            }
            return this
        }

        fun toList(): List<String> {
            return entries.map { entry -> "${entry.key}=${entry.value}" }.toList()
        }

        override fun toString(): String {
            return "Env(entries=$entries)"
        }
    }

    internal class AdditionalParameters {
        private val entries: MutableMap<String, String> = mutableMapOf()

        fun <V> add(name: String, valueProvider: Provider<V>): AdditionalParameters {
            return add(name, valueProvider.get())
        }

        fun <V> add(name: String, value: V): AdditionalParameters {
            if (value != null) {
                this.entries[name] = value.toString()
            }
            return this
        }

        fun render(): String {
            return entries.map { entry -> "${entry.key}=${entry.value}" }.joinToString(separator = " ")
        }

        override fun toString(): String {
            return "AdditionalParameters(entries=$entries)"
        }
    }
}
