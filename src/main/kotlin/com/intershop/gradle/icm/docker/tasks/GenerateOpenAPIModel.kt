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
import com.intershop.gradle.icm.docker.utils.GenerateOpenAPIModelService
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.internal.BuildServiceRegistryInternal
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.options.Option
import org.gradle.api.tasks.options.OptionValues
import org.gradle.internal.resources.ResourceLock
import javax.inject.Inject

/**
 * Task to execute the OpenAPI model generator on a running container.
 */
abstract class GenerateOpenAPIModel
@Inject constructor(project: Project) :
    AbstractICMASContainerTask<RedirectToLoggerCallback, RedirectToLoggerCallback, Long>(project) {

    companion object {
        /**
         * Default task name.
         */
        const val TASK_NAME = "generateOpenAPIModel"

        const val MAIN_CLASS = "com.intershop.swagger.tool.SwaggerModelGenerator"
        const val SERVER_NAME = "OpenAPIServer"
        const val ENV_INTERSHOP_PREPARATION_EMBEDDED = "intershop.preparation.embedded"

        /**
         * Working directory name for output in build directory.
         */
        const val WORKING_DIRECTORY = "openAPI"
        const val CONTAINER_VOLUME = "/intershop/$WORKING_DIRECTORY"


        /**
         * Subdirectory to store OpenAPI model files.
         */
        const val MODELS_SUBDIRECTORY = "models"

        /**
         * Subdirectory to store OpenAPI property files.
         */
        const val PROPERTIES_SUBDIRECTORY = "properties"

        fun calculateOutputDirectory(project: Project): Provider<Directory> =
            project.layout.buildDirectory.dir(WORKING_DIRECTORY)
    }

    /**
     * Model file output formats.
     */
    enum class FileOutputFormat {
        YAML,
        JSON
    }

    /**
     * The model file output format property, if omitted YAML is used.
     */
    @get:Option(
        option = "openapi-file-format",
        description = "Model files output format, if omitted YAML is used."
    )

    @get:Input
    val fileOutputFormat: Property<FileOutputFormat> = project.objects.property(
        FileOutputFormat::class.java
    ).convention(FileOutputFormat.YAML)

    /**
     * Returns a list of possible model file output formats for console help output.
     */
    @OptionValues("openapi-file-format")
    @Suppress("Unused")
    open fun getAvailableFileOutputFormats(): List<FileOutputFormat> = FileOutputFormat.entries

    /**
     * CLI option for application IDs property.
     */
    @get:Option(
        option = "openapi-application-ids",
        description = "Comma separated list of application IDs to process, if omitted all applications are processed."
    )
    @get:Input
    @get:Optional
    val applicationsIDs: Property<String> = project.objects.property(String::class.java)

    /**
     * CLI option for embedded dbPrepare property (default=false).
     */
    @get:Option(
        option = "openapi-embedded-dbprepare",
        description = "Determine whether to run the embedded dbPrepare process during the OpenAPI model generator startup (possible values: {true, false})."
    )
    @get:Input
    @get:Optional
    val embeddedDBPrepare: Property<Boolean> = project.objects.property(Boolean::class.java).convention(false)

    /*
        @get:Internal
        val outputDirectory: DirectoryProperty = project.objects.directoryProperty().value(calculateOutputDirectory(project))
    */
    @Internal
    override fun getSharedResources(): List<ResourceLock> {
        val serviceRegistry: BuildServiceRegistryInternal = services.get(BuildServiceRegistryInternal::class.java)
        val generateOpenAPIModelService = GenerateOpenAPIModelService.lookup(project.provider { serviceRegistry })
        return super.getSharedResources() + serviceRegistry.getSharedResources(setOf(generateOpenAPIModelService))
    }

    override fun processExecutionResult(executionResult: Long) {
        super.processExecutionResult(executionResult)
        if (executionResult > 0) {
            throw GradleException("OpenAPI model generation failed! Please check the log.")
        }
    }

    override fun createContainerEnvironment(): ContainerEnvironment {
        val ownEnv = ICMContainerEnvironmentBuilder()
            .withMainClass(MAIN_CLASS)
            .withServerName(SERVER_NAME)
            .withAdditionalEnvironment(ENV_INTERSHOP_PREPARATION_EMBEDDED, embeddedDBPrepare.get().toString())
            .build()
        return super.createContainerEnvironment().merge(ownEnv)
    }

    override fun createAdditionalParameters(): AdditionalICMParameters {
        val renderWithSpace = { name: String, value: Any? ->
            if (value != null) {
                "$name $value"
            } else {
                name
            }
        }

        // add additional parameters to env
        val ownParameters = AdditionalICMParameters()
        if (fileOutputFormat.isPresent) {
            ownParameters.add("-format", fileOutputFormat.get(), renderWithSpace)
        }
        if (applicationsIDs.isPresent) {
            ownParameters.add("-apps", applicationsIDs.get(), renderWithSpace)
        }
        ownParameters.add("-propertiesExportDir", "$CONTAINER_VOLUME/$PROPERTIES_SUBDIRECTORY", renderWithSpace)
        ownParameters.add("-dir", "$CONTAINER_VOLUME/$MODELS_SUBDIRECTORY", renderWithSpace)
        return super.createAdditionalParameters().merge(ownParameters)
    }

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
