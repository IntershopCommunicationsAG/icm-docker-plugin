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

package com.intershop.gradle.icm.docker.utils

/**
 * Encapsulates a container-port-mapping (container port to host port). Such a mapping also contains a symbolic name.
 */
class PortMapping(val name: String, val hostPort:Int, val containerPort:Int, val primary: Boolean = false) {
    /**
     * Renders the contained port numbers following the pattern: `hostPort`:`containerPort`
     * as it is understood by [com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer.HostConfig.portBindings]
     */
    fun render() : String = "$hostPort:$containerPort"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PortMapping

        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    override fun toString(): String {
        val prim = if(primary) { ", PRIMARY" }else{""}
        return "PortMapping($name = $hostPort:${containerPort}${prim})"
    }

}
