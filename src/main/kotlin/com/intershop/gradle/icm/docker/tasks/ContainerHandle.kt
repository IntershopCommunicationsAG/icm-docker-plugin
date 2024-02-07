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

import com.github.dockerjava.api.model.Container
import org.gradle.api.provider.Provider

interface ContainerHandle {
    companion object {
        fun of(container: Container): ContainerHandle {
            return object : ContainerHandle {
                override fun isRunning(): Boolean = container.state == "running"
                override fun getContainerId(): String = container.id
                override fun getContainerName(): String = container.names.first().removePrefix("/")
                override fun getContainerImage(): String = container.image
                override fun toString(): String {
                    return "Container '${getContainerName()}' with ID '${getContainerId()}'"
                }
            }
        }

        fun notRunning(id: Provider<String>, name: Provider<String>, image: Provider<String>): ContainerHandle {

            return object : ContainerHandle {
                override fun isRunning(): Boolean = false
                override fun getContainerId(): String = id.get()
                override fun getContainerName(): String = name.get()
                override fun getContainerImage(): String = image.get()
                override fun toString(): String {
                    return "Container '${getContainerName()}' with ID '${getContainerId()}'"
                }
            }
        }

        fun desired(name: Provider<String>, image: Provider<String>): ContainerHandle {

            return object : ContainerHandle {
                override fun isRunning(): Boolean = false
                override fun getContainerId(): String =
                        throw IllegalStateException("Desired containers to not have an id assigned")

                override fun getContainerName(): String = name.get()
                override fun getContainerImage(): String = image.get()
                override fun toString(): String {
                    return "Desired container '${getContainerName()}'"
                }
            }
        }
    }

    fun isRunning(): Boolean

    fun getContainerId(): String

    fun getContainerName(): String

    fun getContainerImage(): String

}