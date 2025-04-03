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

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Container
import org.gradle.api.GradleException
import org.gradle.api.provider.Provider
import java.io.Serializable

/**
 * Defines a handle / pointer to a container identified by its name and using a certain image.
 */
interface ContainerHandle : Serializable {
    companion object {
        /**
         * Creates a new [ContainerHandle] that represents an existing and running container.
         */
        fun of(container: Container): ContainerHandle {
            return object : ContainerHandle {
                override fun exists(): Boolean = true
                override fun isRunning(): Boolean = container.state == "running"
                override fun getContainerId(): String = container.id
                override fun getContainerName(): String = container.names.first().removePrefix("/")
                override fun getContainerImage(): String = container.image
                override fun toString(): String {
                    return "Container '${getContainerName()}' with ID '${getContainerId()}'"
                }
            }
        }

        /**
         * Creates a new [ContainerHandle] that represents an existing container not running yet.
         */
        fun notRunning(id: Provider<String>, name: Provider<String>, image: Provider<String>): ContainerHandle {

            return object : ContainerHandle {
                override fun exists(): Boolean = true
                override fun isRunning(): Boolean = false
                override fun getContainerId(): String = id.get()
                override fun getContainerName(): String = name.get()
                override fun getContainerImage(): String = image.get()
                override fun toString(): String {
                    return "Container '${getContainerName()}' with ID '${getContainerId()}'"
                }
            }
        }

        /**
         * Creates a new [ContainerHandle] that represents a desired (expected) container.
         */
        fun desired(name: Provider<String>, image: Provider<String>): ContainerHandle {
            return desired({ name.get()}, { image.get() })
        }

        /**
         * Creates a new [ContainerHandle] that represents a desired (expected) container.
         */
        fun desired(supplyName: () -> String, supplyImage: () -> String): ContainerHandle {

            return object : ContainerHandle {
                override fun exists(): Boolean = false
                override fun isRunning(): Boolean = false
                override fun getContainerId(): String =
                        throw IllegalStateException("Desired containers don't have an id assigned")

                override fun getContainerName(): String = supplyName.invoke()
                override fun getContainerImage(): String = supplyImage.invoke()
                override fun toString(): String {
                    return "Container '${getContainerName()}'"
                }
            }
        }
    }

    fun exists(): Boolean

    fun isRunning(): Boolean

    fun getContainerId(): String

    fun getContainerName(): String

    fun getContainerImage(): String

    /**
     * Returns a new [ContainerHandle] representing the container's current state.
     */
    fun currentState(dockerClient : DockerClient) : ContainerHandle {
        val containers: List<Container> =
            dockerClient.listContainersCmd().withShowAll(true).withNameFilter(listOf("/${getContainerName()}"))
                .exec()

        for (c in containers) {
            val expectedImage = getContainerImage()
            if (c.names.contains("/${getContainerName()}")) {
                if (c.image != expectedImage) {
                    throw GradleException(
                        "The running container was started with image '${c.image}', but the configured image is " +
                                "'${expectedImage}'. Please remove running containers!")
                }
                return of(c)
            }
        }
        return desired({getContainerName()}, {getContainerImage()})
    }
}