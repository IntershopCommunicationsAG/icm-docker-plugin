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

import com.github.dockerjava.api.command.ExecCreateCmdResponse
import com.intershop.gradle.icm.docker.tasks.utils.AdditionalICMParameters
import com.intershop.gradle.icm.docker.tasks.utils.ContainerEnvironment
import com.intershop.gradle.icm.docker.tasks.utils.ICMContainerEnvironmentBuilder
import com.intershop.gradle.icm.docker.tasks.utils.RedirectToLoggerCallback
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.options.Option
import org.gradle.api.tasks.options.OptionValues
import javax.inject.Inject

/**
 * Task to run dbPrepare on a running container.
 */
open class DBPrepareTask
@Inject constructor(project: Project) :
        AbstractICMASContainerTask<RedirectToLoggerCallback, RedirectToLoggerCallback, Long>(project) {

    companion object {
        const val TASK_NAME = "dbPrepare"
    }

    @get:Option(option = "mode", description = "Mode in which dbPrepare runs: 'init', 'migrate' or 'auto'. " +
                                               "The default is 'auto'.")
    @get:Input
    val mode: Property<String> = project.objects.property(String::class.java)

    /**
     * Return the possible values for the task option [mode]
     */
    @OptionValues("mode")
    fun getNodeValues(): Collection<String> = listOf("init", "migrate", "auto")

    @get:Option(option = "clean",
            description = "can be 'only', 'yes' or 'no', default is 'no'. In case of 'only', only the database+sites " +
                          "are cleaned up. If 'yes' the database is cleaned up before preparing other steps. " +
                          "If 'no' no database cleanup is done.")
    @get:Input
    val clean: Property<String> = project.objects.property(String::class.java)

    /**
     * Return the possible values for the task option [clean]
     */
    @OptionValues("clean")
    fun getCleanDBValues(): Collection<String> = listOf("only", "yes", "no")

    @get:Option(option = "cartridges", description = "A comma-separated cartridge list. Executes the cartridges in " +
                                                     "that list. This is an optional parameter.")
    @get:Input
    val cartridges: Property<String> = project.objects.property(String::class.java)

    @get:Option(option = "property-keys", description = "Comma-separated list of preparer property keys to execute. " +
                                                        "This is an optional parameter.")
    @get:Input
    val propertyKeys: Property<String> = project.objects.property(String::class.java)

    @set:Option(option = "additional-parameter", description = "Additional command line parameters to be passed to " +
                                                               "the dbPrepare tool. For more than 1 parameter use " +
                                                               "this task option as often as needed.")
    @get:Input
    var additionalParameters: List<String> = listOf()

    init {
        mode.convention("auto")
        clean.convention("no")
        cartridges.convention("")
        propertyKeys.convention("")
    }

    override fun processExecutionResult(executionResult: Long) {
        super.processExecutionResult(executionResult)
        if (executionResult > 0) {
            throw GradleException("DBPrepare failed! Please check the log.")
        }
    }

    override fun createContainerEnvironment(): ContainerEnvironment {
        val ownEnv = ContainerEnvironment()
        ownEnv.add(ICMContainerEnvironmentBuilder.ENV_IS_DBPREPARE, true)
        return super.createContainerEnvironment().merge(ownEnv)
    }

    override fun createAdditionalParameters(): AdditionalICMParameters {
        // add additional parameters to env
        val ownParameters = AdditionalICMParameters()
                .add("-classic")
                .add("--mode", mode)
                .add("--clean", clean)

        if (cartridges.get().trim().isNotEmpty()) {
            ownParameters.add("--cartridges", cartridges.get().replace(" ", ""))
        }
        if (propertyKeys.get().isNotEmpty()) {
            ownParameters.add("--property-keys", propertyKeys.get().replace(" ", ""))
        }
        if (additionalParameters.isNotEmpty()) {
            additionalParameters.forEach { parameter ->
                ownParameters.add(parameter)
            }
        }

        return super.createAdditionalParameters().merge(ownParameters)
    }

    override fun createCartridgeList(): Provider<Set<String>> = testCartridgeList

    override fun createCallback(): RedirectToLoggerCallback {
        return RedirectToLoggerCallback(project.logger)
    }

    override fun waitForCompletion(
            resultCallbackTemplate: RedirectToLoggerCallback,
            execResponse: ExecCreateCmdResponse,
    ): Long {
        resultCallbackTemplate.awaitCompletion()
        return waitForExit(execResponse.id)
    }
}
