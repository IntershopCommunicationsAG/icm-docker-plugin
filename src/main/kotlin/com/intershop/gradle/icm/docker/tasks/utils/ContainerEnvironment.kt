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


    /**
     * Just calls [ContainerEnvironment.add] with `key=Pair.first` and `value=Pair.second`
     */
    fun add(entry: Pair<String, Any?>): ContainerEnvironment {
        return add(entry.first, entry.second)
    }

    /**
     * Adds multiply environment variables calling [ContainerEnvironment.add] for each
     */
    fun addAll(vararg entries: Pair<String, Any?>): ContainerEnvironment {
        entries.forEach {
            add(it)
        }
        return this
    }

    fun toList(): List<String> {
        return toMap().map { entry -> "${entry.key}=${entry.value}" }.toList()
    }

    fun toMap(): Map<String, String> {
        return entries.mapValues { entry -> valueToString(entry.key, entry.value) }
    }

    fun merge(other: ContainerEnvironment): ContainerEnvironment {
        val merged = ContainerEnvironment()
        merged.entries.putAll(this.entries)
        merged.entries.putAll(other.entries)
        return merged
    }

    private fun valueToString(key: String, value: Any) : String {
        if (value !is Provider<*>){
            return value.toString()
        }
        val renderedValueProvider = value.map { v -> v.toString() }
        if (renderedValueProvider.isPresent){
            return renderedValueProvider.get()
        }
        throw IllegalStateException("Provider mapped to ContainerEnvironment entry '$key' must not be empty.")
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
                        "'${valueToString(key, value)}'"
                    }
                }"
            }.joinToString(separator = ", ")
        })"
    }

}
