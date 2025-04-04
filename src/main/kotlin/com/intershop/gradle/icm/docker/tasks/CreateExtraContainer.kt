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
import com.intershop.gradle.icm.docker.tasks.utils.ContainerEnvironment
import com.intershop.gradle.icm.docker.utils.PortMapping
import org.gradle.api.GradleException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import javax.inject.Inject

abstract class CreateExtraContainer
@Inject constructor(objectFactory: ObjectFactory) : DockerCreateContainer(objectFactory) {

    private val portMappings: MapProperty<String, PortMapping> =
            objectFactory.mapProperty(String::class.java, PortMapping::class.java)

    @get:Internal
    val existingContainer: Property<ContainerHandle> = objectFactory.property(ContainerHandle::class.java)

    @get:Internal
    val createdContainer: Property<ContainerHandle> =
            objectFactory.property(ContainerHandle::class.java).convention(project.provider {
                // if container does not exist yet, just use a desired-ContainerHandle
                if (existingContainer.isPresent) {
                    existingContainer.get()
                } else {
                    ContainerHandle.desired(containerName, image)
                }
            })

    /**
     * Returns a [Provider] that provides the primary port mapping if there is such a port mapping otherwise
     * [Provider.get] will fail
     */
    @Internal
    fun getPrimaryPortMapping(): Provider<PortMapping> = project.provider {
        this.portMappings.get().values.firstOrNull { mapping -> mapping.primary }
    }

    /**
     * Returns all port mappings
     */
    @Internal
    fun getPortMappings(): Set<PortMapping> =
            this.portMappings.get().values.toSet()

    /**
     * Adds port mappings to be used with the container
     */
    fun withPortMappings(vararg portMappings: PortMapping) {
        portMappings.forEach { currPortMapping ->
            // check if there's already a primary port mapping
            if (currPortMapping.primary && getPrimaryPortMapping().isPresent) {
                throw GradleException("Duplicate primary port mapping detected for task $name")
            }

            this.portMappings.put(currPortMapping.name, currPortMapping)
            hostConfig.portBindings.add(project.provider { currPortMapping.render() })
        }
    }

    /**
     * Same as [CreateExtraContainer.withEnvironment(Provider<ContainerEnvironment>)] but using a [Provider<ContainerEnvironment>]
     */
    fun withEnvironment(environment: ContainerEnvironment) {
        withEnvironment(project.provider { environment })
    }

    /**
     * Applies the given `environment` to this task's [DockerCreateContainer.envVars] (using [MapProperty.putAll]).
     */
    fun withEnvironment(environment: Provider<ContainerEnvironment>) {
        envVars.putAll(
                project.provider { // use a provider around environment.get() to defer the execution until actual usage
                    environment.get().toMap()
                })
    }

    fun withVolumes(volumes: Map<String, String>) {
        withVolumes(project.provider { volumes })
    }

    fun withVolumes(volumes: Provider<Map<String, String>>) {
        this.hostConfig.binds.putAll(volumes)
    }

    init {
        this.onlyIf("Container does not exist") {
            val currentContainerState = existingContainer.map { c -> c.currentState(dockerClient) }.get()
            if (currentContainerState.exists()) {
                project.logger.quiet("{} still exists, skipping creation", existingContainer.get())
                return@onlyIf false
            }
            return@onlyIf true
        }
    }

    override fun runRemoteCommand() {
        val currentContainerState = existingContainer.map { c -> c.currentState(dockerClient) }.get()
        if (currentContainerState.exists()) {
            throw GradleException("Expecting ${currentContainerState} to not currently exist but it does.")
        }

        logger.info("Creating container '{}' using the following port mappings: {}",
                containerName.get(), getPortMappings())
        logger.info("Creating container '{}' using volumes: {}",
                containerName.get(), volumes.get())
        super.runRemoteCommand()
        createdContainer.set(ContainerHandle.notRunning(containerId, containerName, image))
    }
}
