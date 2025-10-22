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

open class AdditionalICMParameters {
    private val entries: MutableSet<Entry<*>> = mutableSetOf()

    fun add(name: String): AdditionalICMParameters {
        return add(name, null)
    }

    fun <V : Any> add(name: String, valueProvider: Provider<V>): AdditionalICMParameters {
        return add(name, valueProvider.get())
    }

    fun <V> add(name: String, value: V): AdditionalICMParameters {
        this.entries.add(Entry(name, value,))
        return this
    }

    fun <V> add(name: String, value: V, renderEntry : ((name: String, value: V?) -> String)?): AdditionalICMParameters {
        this.entries.add(Entry(name, value, renderEntry))
        return this
    }

    fun merge(other: AdditionalICMParameters): AdditionalICMParameters {
        val merged = AdditionalICMParameters()
        merged.entries.addAll(this.entries)
        merged.entries.addAll(other.entries)
        return merged
    }

    fun render(): String {
        return entries.joinToString(separator = " ") { entry -> entry.render() }
    }

    override fun toString(): String {
        return "AdditionalICMParameters(entries=$entries)"
    }

    class Entry<V>(val name: String, val value: V?, val renderEntry : ((name: String, value: V?) -> String)? = null) {

        fun render(): String {
            return renderEntry?.invoke(name, value) ?: renderDefault(name, value)
        }

        fun renderDefault(name: String, value: V?): String =
                if (value != null) {
                    "$name=$value"
                } else {
                    name
                }


        override fun toString(): String =
                if (value != null) {
                    "Entry(name=$name, value=$value)"
                } else {
                    "Entry(name=$name, value=<unset>)"
                }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Entry<*>

            if (name != other.name) return false

            return true
        }

        override fun hashCode(): Int {
            return name.hashCode()
        }
    }
}
