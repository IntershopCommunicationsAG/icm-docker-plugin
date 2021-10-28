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

import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.async.ResultCallbackTemplate
import com.github.dockerjava.api.model.Frame
import com.intershop.gradle.icm.docker.extension.DevelopmentConfiguration
import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.tasks.utils.AdditionalICMParameters
import com.intershop.gradle.icm.docker.tasks.utils.ContainerEnvironment
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
 * Abstract base task to run a typical ICM-AS classes on a previously prepared container
 * @see com.intershop.gradle.icm.docker.utils.appserver.ContainerTaskPreparer
 */
abstract class AbstractICMASContainerTask<RC : ResultCallback<Frame>, RCT : ResultCallbackTemplate<RC, Frame>>
@Inject constructor(project: Project) : AbstractContainerTask() {

    companion object {
        const val ENV_IS_DBPREPARE = "IS_DBPREPARE"
        const val ENV_ENABLE_DEBUG = "ENABLE_DEBUG"
        const val ENV_DB_TYPE = "INTERSHOP_DATABASETYPE"
        const val ENV_DB_JDBC_URL = "INTERSHOP_JDBC_URL"
        const val ENV_DB_JDBC_USER = "INTERSHOP_JDBC_USER"
        const val ENV_DB_JDBC_PASSWORD = "INTERSHOP_JDBC_PASSWORD"
        const val ENV_CARTRIDGE_LIST = "CARTRIDGE_LIST"
        const val ENV_ADDITIONAL_PARAMETERS = "ADDITIONAL_PARAMETERS"
        const val ENV_ADDITIONAL_VM_PARAMETERS = "ADDITIONAL_VM_PARAMETERS"
        const val ENV_CARTRIDGE_CLASSPATH_LAYOUT = "CARTRIDGE_CLASSPATH_LAYOUT"
        const val ENV_ADDITIONAL_CLASSPATH="ADDITIONAL_CLASSPATH"
        const val ENV_ADDITIONAL_CARTRIDGE_REPOSITORIES="ADDITIONAL_CARTRIDGE_REPOSITORIES"
        const val ENV_ADDITIONAL_LIBRARY_REPOSITORIES="ADDITIONAL_LIBRARY_REPOSITORIES"
        const val ENV_MAIN_CLASS="MAIN_CLASS"
        const val ENV_VALUE_CARTRIDGE_CLASSPATH_LAYOUT = "release,source"
        const val COMMAND = "/intershop/bin/intershop.sh"

        const val SYSPROP_DEBUG_JVM = "debug-jvm"
    }

    private val debugProperty: Property<DebugMode> = project.objects.property(DebugMode::class.java)

    @get:Input
    val databaseConfiguration: Property<DevelopmentConfiguration.DatabaseParameters> by lazy {
        project.objects.property(DevelopmentConfiguration.DatabaseParameters::class.java)
                .value(project.extensions.getByType<IntershopDockerExtension>().developmentConfig.databaseConfiguration)
    }

    /**
     * The cartridge list to be used to start the ICM-AS server
     */
    @get: Input
    val cartridgeList: SetProperty<String> by lazy {
        val cartListProvider = project.extensions.getByType<IntershopDockerExtension>().developmentConfig.cartridgeList
        if (cartListProvider.get().isEmpty()){
            throw GradleException("Build property intershop_docker.developmentConfig.cartridgeList denotes an empty " +
                                  "set. Please provide a non-empty set.")
        }
        cartListProvider
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
                          "The process is started suspended and listening on port 7746."
    )
    @get:Input
    var debug: DebugMode
        get() = debugProperty.get()
        set(value) = debugProperty.set(value)

    init {
        debugProperty.convention(provideFallbackDebugMode())

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

        val env = createContainerEnvironment()
        execCmd.withEnv(env.toList())

        project.logger.quiet("Attempting to execute command '{}' on container {} using {}", command, containerId.get(),
                env)

        val localExecId = execCmd.exec().id

        dockerClient.execStartCmd(localExecId).withDetach(false).exec(execCallback).awaitCompletion()

        val exitCode = waitForExit(localExecId)

        processExitCode(exitCode)

        postRunRemoteCommand(execCallback)
    }

    protected open fun processExitCode(exitCode : Long) {
        project.logger.quiet("Command execution inside the container finished with exit code {}", exitCode)
    }

    protected open fun postRunRemoteCommand(resultCallbackTemplate : RCT) = Unit

    protected open fun createContainerEnvironment() : ContainerEnvironment {
        val env = ContainerEnvironment()

        // add additional parameters to env
        val additionalParameters = createAdditionalParameters()

        env.add(ENV_ADDITIONAL_PARAMETERS, additionalParameters.render())

        // configure debugging
        env.add(ENV_ENABLE_DEBUG, debugProperty.get().targetMode)

        // add database config to env
        databaseConfiguration.get().run {
            env.add(ENV_DB_TYPE, type.get()).add(ENV_DB_JDBC_URL, jdbcUrl.get()).add(ENV_DB_JDBC_USER, jdbcUser.get())
                    .add(ENV_DB_JDBC_PASSWORD, jdbcPassword.get())
        }

        // add cartridge list (values separated by space)
        env.add(ENV_CARTRIDGE_LIST, createCartridgeList().get().joinToString(separator = " "))

        // ensure release (product cartridges) and source (customization cartridges) layouts are recognized
        env.add(ENV_CARTRIDGE_CLASSPATH_LAYOUT, ENV_VALUE_CARTRIDGE_CLASSPATH_LAYOUT)

        return env
    }

    protected open fun createCartridgeList(): Provider<Set<String>> = cartridgeList

    protected open fun createAdditionalParameters() : AdditionalICMParameters = AdditionalICMParameters()

    protected abstract fun createCallback(): RCT

    private fun provideFallbackDebugMode() : Provider<DebugMode> {
        return project.provider {
            val sysPropValue = System.getProperty(SYSPROP_DEBUG_JVM, "false")
            try {
                DebugMode.valueOf(sysPropValue)
            } catch (e: IllegalArgumentException) {
                throw GradleException("System property '$SYSPROP_DEBUG_JVM' must either be absent or have one of the " +
                                      "following value: ${DebugMode.values()}")
            }
        }
    }

    enum class DebugMode(val targetMode : String) {
        TRUE("true"), SUSPEND("suspend"), FALSE("false")
    }
}
