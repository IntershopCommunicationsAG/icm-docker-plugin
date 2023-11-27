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
package com.intershop.gradle.icm.utils

import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.process.JavaDebugOptions
import org.gradle.process.internal.DefaultJavaDebugOptions

/**
 * Encapsulate JVM debug options
 * + parse gradle task option value (enabled, suspend, etc.)
 * + determine debug port from [com.intershop.gradle.icm.extension.DevelopmentConfiguration]
 * + render JVM debug parameter string
 *
 * The internal values are backed by a [org.gradle.process.internal.DefaultJavaDebugOptions] to it used it's
 * defaults.
 */
class JavaDebugSupport(private val options: JavaDebugOptions) : JavaDebugOptions {

    companion object {
        const val PATTERN_COMMAND_LINE = "-agentlib:jdwp=transport=dt_socket,server=%s,suspend=%s,address=%s:%d"

        const val TASK_OPTION_VALUE_TRUE = "TRUE"
        const val TASK_OPTION_VALUE_YES = "YES"
        const val TASK_OPTION_VALUE_FALSE = "FALSE"
        const val TASK_OPTION_VALUE_NO = "NO"
        const val TASK_OPTION_VALUE_SUSPEND = "SUSPEND"

        /**
         * Returns an [JavaDebugSupport] instance that contains the default values
         * @see DefaultJavaDebugOptions
         */
        fun defaults(project: Project): JavaDebugSupport {
            val javaDebugOptions = project.objects.newInstance(DefaultJavaDebugOptions::class.java)
            javaDebugOptions.suspend.value(false)
            return JavaDebugSupport(javaDebugOptions)
        }

        /**
         * Describes the valid values for the `taskOptionValue`
         * @see parse
         */
        fun describe(): String = """
            Valid values are [$TASK_OPTION_VALUE_TRUE, $TASK_OPTION_VALUE_YES, $TASK_OPTION_VALUE_SUSPEND]. 
            $TASK_OPTION_VALUE_TRUE and $TASK_OPTION_VALUE_YES enable debugging. 
            $TASK_OPTION_VALUE_SUSPEND enables debugging and switches 'suspend=y'. Every other value disables debugging.
        """.trimIndent()

        /**
         * Parses a `taskOptionValue` to a new instance of [JavaDebugSupport]:
         * + flags `enabled` and `suspend` are parsed from `taskOptionValue`
         * + flag `server` always is `true`
         * + value `port` is determined from
         * [com.intershop.gradle.icm.docker.extension.DevelopmentConfiguration.asPortConfiguration] (`debug.hostPort`)
         *
         * @see describe
         */
        fun parse(project: Project, taskOptionValue: String): JavaDebugSupport {

            val javaDebugOptions = project.objects.newInstance(DefaultJavaDebugOptions::class.java)
            val intershopDockerExtension = project.extensions.getByType(IntershopDockerExtension::class.java)

            with(javaDebugOptions) {
                port.value(project.provider {
                    intershopDockerExtension.developmentConfig.asPortConfiguration.debug.get().hostPort
                })

                server.convention(true)

                when (taskOptionValue.uppercase()) {
                    TASK_OPTION_VALUE_TRUE -> {
                        enabled.value(true)
                    }
                    TASK_OPTION_VALUE_YES -> {
                        enabled.value(true)
                    }
                    TASK_OPTION_VALUE_SUSPEND -> {
                        enabled.value(true)
                        suspend.value(true)
                    }
                    else -> {
                        enabled.value(false)
                        suspend.value(false)
                    }
                }
            }
            return JavaDebugSupport(javaDebugOptions)
        }
    }

    fun renderJVMCommandLineParameter(): String {
        if (!enabled.get()) {
            return ""
        }
        return PATTERN_COMMAND_LINE.format(renderYesNo(server), renderYesNo(suspend), host.get(), port.get())
    }

    /**
     * Renders the value for usage with an environment variable
     */
    fun renderEnvVariableValue(): String =
            if (enabled.get()) {
                if (suspend.get()) {
                    "suspend"
                } else {
                    "true"
                }
            } else {
                "false" // something else then suspend or true
            }

    fun renderTaskOptionValue(): String =
            if (enabled.get()) {
                if (suspend.get()) {
                    TASK_OPTION_VALUE_SUSPEND
                } else {
                    TASK_OPTION_VALUE_TRUE
                }
            } else {
                TASK_OPTION_VALUE_FALSE // something else then TASK_OPTION_VALUE_SUSPEND or TASK_OPTION_VALUE_TRUE
            }

    override fun getEnabled(): Property<Boolean> = options.enabled.convention(false)

    override fun getHost(): Property<String> = options.host.convention("*")

    override fun getPort(): Property<Int> = options.port.convention(5005)

    override fun getServer(): Property<Boolean> = options.server.convention(true)

    override fun getSuspend(): Property<Boolean> = options.suspend.convention(false)

    private fun renderYesNo(value: Property<Boolean>): String = if (value.getOrElse(false)) {
        "y"
    } else {
        "n"
    }

}
