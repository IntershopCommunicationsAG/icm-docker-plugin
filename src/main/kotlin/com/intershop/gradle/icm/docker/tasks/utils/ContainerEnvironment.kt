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

/**
 * Encapsulates environment variables to use used when starting a container
 * @see com.github.dockerjava.api.command.ExecCreateCmd.withEnv
 */
open class ContainerEnvironment {
    private val entries: MutableMap<String, String> = mutableMapOf()

    fun <V> add(key: String, value: V): ContainerEnvironment {
        if (value != null) {
            this.entries[key] = value.toString()
        }
        return this
    }

    fun toList(): List<String> {
        return entries.map { entry -> "${entry.key}=${entry.value}" }.toList()
    }

    fun merge(other: ContainerEnvironment): ContainerEnvironment {
        val merged = ContainerEnvironment()
        merged.entries.putAll(this.entries)
        merged.entries.putAll(other.entries)
        return merged
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
                        "'$value'"
                    }
                }"
            }.joinToString(separator = ", ")
        })"
    }

}
