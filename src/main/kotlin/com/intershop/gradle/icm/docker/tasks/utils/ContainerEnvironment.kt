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
package com.intershop.gradle.icm.docker.tasks.utils

import org.gradle.api.provider.Provider
import java.io.Serializable

/**
 * Encapsulates environment variables to be used when starting a container. If [Provider]s are added they are stored
 * as is. [Provider.get] is not called until [ContainerEnvironment.toList] or [ContainerEnvironment.toString] are
 * called. So the actual value is determined lazily.
 * @see com.github.dockerjava.api.command.ExecCreateCmd.withEnv
 */
open class ContainerEnvironment : Serializable {
    private val entries: MutableMap<String, Any> = mutableMapOf()

    companion object {
        /**
         * Converts a property name into an environment variable name the way it will be found by the ICM-AS
         * 1. convert to upper case
         * 2. replace dots (`.`) by underscores (`_`)
         */
        fun propertyNameToEnvName(propertyName : String) : String = propertyName.uppercase().replace('.', '_')
    }

    /**
     * Adds an environment variable with a name of `key` and a value of `value` if `value != null`.
     */
    fun add(key: String, value: Any?): ContainerEnvironment {
        if (value != null) {
            this.entries[key] = value
        }
        return this
    }

    fun toList(): List<String> {
        return entries.map { entry -> "${entry.key}=${valueToString(entry.value)}" }.toList()
    }

    fun merge(other: ContainerEnvironment): ContainerEnvironment {
        val merged = ContainerEnvironment()
        merged.entries.putAll(this.entries)
        merged.entries.putAll(other.entries)
        return merged
    }

    private fun valueToString(value: Any) : String? {
        if (value !is Provider<*>){
            return value.toString()
        }
        return value.map { v -> v.toString() }.orNull
    }

    override fun toString(): String {
        return "ContainerEnvironment(${
            // render [entries]
            entries.map { (key, value) ->
                "$key = ${
                    // star out passwords etc.
                    if (key.matches(Regex("(?i).*PASSWORD.*"))) {
                        "<present>"
                    } else {
                        "'${valueToString(value)}'"
                    }
                }"
            }.joinToString(separator = ", ")
        })"
    }

}
