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
import com.github.dockerjava.api.command.ExecCreateCmdResponse
import com.github.dockerjava.api.model.Frame
import com.intershop.gradle.icm.docker.extension.DevelopmentConfiguration.ASPortConfiguration
import com.intershop.gradle.icm.docker.extension.DevelopmentConfiguration.DatabaseParameters
import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.tasks.utils.AdditionalICMParameters
import com.intershop.gradle.icm.docker.tasks.utils.ContainerEnvironment
import com.intershop.gradle.icm.docker.tasks.utils.ICMContainerEnvironmentBuilder
import com.intershop.gradle.icm.docker.tasks.utils.ICMContainerEnvironmentBuilder.ClasspathLayout
import com.intershop.gradle.icm.docker.utils.Configuration
import com.intershop.gradle.icm.utils.JavaDebugSupport
import com.intershop.gradle.icm.utils.JavaDebugSupport.Companion.TASK_OPTION_VALUE_FALSE
import com.intershop.gradle.icm.utils.JavaDebugSupport.Companion.TASK_OPTION_VALUE_NO
import com.intershop.gradle.icm.utils.JavaDebugSupport.Companion.TASK_OPTION_VALUE_SUSPEND
import com.intershop.gradle.icm.utils.JavaDebugSupport.Companion.TASK_OPTION_VALUE_TRUE
import com.intershop.gradle.icm.utils.JavaDebugSupport.Companion.TASK_OPTION_VALUE_YES
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.options.Option
import org.gradle.api.tasks.options.OptionValues
import org.gradle.kotlin.dsl.getByType
import javax.inject.Inject

/**
 * Abstract base task to run a typical ICM-AS classes on a previously prepared container
 * @see com.intershop.gradle.icm.docker.utils.appserver.ContainerTaskPreparer
 * @param <RC> result callback type
 * @param <RCT> result callback template type
 * @param <ER> execution result type
 */
abstract class AbstractICMASContainerTask<RC : ResultCallback<Frame>, RCT : ResultCallbackTemplate<RC, Frame>, ER>
@Inject constructor(project: Project) : AbstractContainerTask() {

    companion object {
        const val DEFAULT_COMMAND = "/intershop/bin/intershop.sh"
    }

    private val debugProperty: Property<JavaDebugSupport> = project.objects.property(JavaDebugSupport::class.java)

    /**
     * The database configuration. It is lazily determined from
     * [com.intershop.gradle.icm.docker.extension.DevelopmentConfiguration.databaseConfiguration]
     */
    @get:Input
    val databaseConfiguration: Property<DatabaseParameters> by lazy {
        project.objects.property(DatabaseParameters::class.java)
                .value(project.extensions.getByType<IntershopDockerExtension>().developmentConfig.databaseConfiguration)
    }


    /**
     * The port configuration. It is lazily determined from
     * [com.intershop.gradle.icm.docker.extension.DevelopmentConfiguration.asPortConfiguration]
     */
    @get:Input
    val portConfiguration: Property<ASPortConfiguration> by lazy {
        project.objects.property(ASPortConfiguration::class.java)
                .value(project.extensions.getByType<IntershopDockerExtension>().developmentConfig.asPortConfiguration)
    }

    /**
     * The cartridge list to be used to start the ICM-AS server
     */
    @get: Input
    val cartridgeList: SetProperty<String> by lazy {
        val cartListProvider = project.extensions.getByType<IntershopDockerExtension>().developmentConfig.cartridgeList
        if (cartListProvider.get().isEmpty()) {
            throw GradleException("Build property intershop_docker.developmentConfig.cartridgeList denotes an empty " +
                                  "set. Please provide a non-empty set.")
        }
        cartListProvider
    }

    /**
     * The cartridge list to be used to start the ICM-AS server for tests
     */
    @get: Input
    val testCartridgeList: SetProperty<String> by lazy {
        val cartListProvider =
                project.extensions.getByType<IntershopDockerExtension>().developmentConfig.testCartridgeList
        if (cartListProvider.get().isEmpty()) {
            throw GradleException(
                    "Build property intershop_docker.developmentConfig.testCartridgeList denotes an empty " +
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
            option = "debug-icm",
            description = "Enable/control debugging for the process. The following values are supported: " +
                          "$TASK_OPTION_VALUE_TRUE/$TASK_OPTION_VALUE_YES - " +
                          "enable debugging, $TASK_OPTION_VALUE_SUSPEND - enable debugging in " +
                          "suspend-mode, every other value - disable debugging. The debugging port is controlled by " +
                          "icm-property '${Configuration.AS_DEBUG_PORT}'."
    )
    @get:Input
    var debug: String
        get() = debugProperty.get().renderTaskOptionValue()
        set(value) = debugProperty.set(JavaDebugSupport.parse(project, value))

    /**
     * Return the possible values for the task option [debug]
     */
    @OptionValues("debug-icm")
    fun getDebugOptionValues(): Collection<String> = listOf(TASK_OPTION_VALUE_TRUE, TASK_OPTION_VALUE_YES,
            TASK_OPTION_VALUE_SUSPEND, TASK_OPTION_VALUE_FALSE, TASK_OPTION_VALUE_NO)

    init {
        debugProperty.convention(JavaDebugSupport.defaults(project))

        group = "icm docker project"
    }

    /**
     * Executes a `docker-exec` on the container provided by [containerId]. This `docker-exec` uses the environment
     * variables provided by [createContainerEnvironment] and the callback created by [createCallback].
     * When the `docker-exec` has finished the return code is processed by [processExecutionResult]. Finally
     * [postRunRemoteCommand] is executed.
     */
    override fun runRemoteCommand() {
        val callback = createCallback()

        val execCmd = dockerClient.execCreateCmd(containerId.get())
        execCmd.withAttachStderr(true)
        execCmd.withAttachStdout(true)
        val command = getCommand()
        execCmd.withCmd(*command.toTypedArray())

        val env = createContainerEnvironment()
        execCmd.withEnv(env.toList())

        project.logger.quiet("Attempting to execute command '{}' on container {} using {}", command, containerId.get(),
                env)

        val execResponse: ExecCreateCmdResponse = execCmd.exec()

        val execCallback = dockerClient.execStartCmd(execResponse.id).withDetach(false).exec(callback)

        val exitCode = waitForCompletion(execCallback, execResponse)

        processExecutionResult(exitCode)

        postRunRemoteCommand(execCallback)
    }

    /**
     * Returns the command to be executed inside the container
     */
    @Internal
    protected open fun getCommand(): List<String> = listOf("/bin/sh", "-c", DEFAULT_COMMAND)

    /**
     * Processes the exit code of the command executed inside the container. This function is executed right after
     * the command executed inside the container has finished.
     * Subclasses may overwrite this method to do same custom stuff.
     */
    protected open fun processExecutionResult(executionResult: ER) {
        project.logger.quiet("Command execution inside the container finished with execution result: {}",
                executionResult)
    }

    /**
     * This function (actually does nothing) is executed right after [processExecutionResult].
     * Subclasses may overwrite this method to do same custom stuff.
     */
    protected open fun postRunRemoteCommand(resultCallbackTemplate: RCT) = Unit

    /**
     * This function creates the environment used for the docker-exec.
     * Subclasses may overwrite this method to add some extract environment variables (keep super-variables).
     */
    protected open fun createContainerEnvironment(): ContainerEnvironment {
        return ICMContainerEnvironmentBuilder()
                .withContainerName(containerName)
                .withDatabaseConfig(databaseConfiguration.get())
                .withPortConfig(portConfiguration.get())
                .withCartridgeList(createCartridgeList().get())
                .withAdditionalParameters(createAdditionalParameters())
                .withDebugOptions(debugProperty.get())
                // ensure release (product cartridges) and source (customization cartridges) layouts are recognized
                .withClasspathLayout(setOf(ClasspathLayout.RELEASE, ClasspathLayout.SOURCE))
                .build()
    }

    protected abstract fun waitForCompletion(resultCallbackTemplate: RCT, execResponse: ExecCreateCmdResponse): ER

    /**
     * Creates the list of cartridges to be used for the ICM-AS.
     */
    protected open fun createCartridgeList(): Provider<Set<String>> = cartridgeList

    /**
     * This function creates the additional parameters used for the environment variables [ENV_ADDITIONAL_PARAMETERS].
     * Subclasses may overwrite this method to add some extract parameters (keep super-parameters).
     * @see intershop.sh
     */
    protected open fun createAdditionalParameters(): AdditionalICMParameters = AdditionalICMParameters()

    /**
     * Creates the [ResultCallbackTemplate] to be used for the docker-exec.
     */
    protected abstract fun createCallback(): RCT

}
