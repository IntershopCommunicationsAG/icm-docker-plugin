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

import com.intershop.gradle.icm.docker.extension.DevelopmentConfiguration.ASPortConfiguration
import com.intershop.gradle.icm.docker.extension.DevelopmentConfiguration.DatabaseParameters
import com.intershop.gradle.icm.utils.JavaDebugSupport

/**
 * Encapsulates a builder for [ContainerEnvironment] instances for an ICM appserver.
 */
class ICMContainerEnvironmentBuilder {

    companion object {
        const val ENV_IS_DBPREPARE = "IS_DBPREPARE"
        const val ENV_DEBUG_ICM = "DEBUG_ICM"
        const val ENV_DB_TYPE = "INTERSHOP_DATABASETYPE"
        const val ENV_DB_JDBC_URL = "INTERSHOP_JDBC_URL"
        const val ENV_DB_JDBC_USER = "INTERSHOP_JDBC_USER"
        const val ENV_DB_JDBC_PASSWORD = "INTERSHOP_JDBC_PASSWORD"
        const val ENV_CARTRIDGE_LIST = "CARTRIDGE_LIST"
        const val ENV_ADDITIONAL_PARAMETERS = "ADDITIONAL_PARAMETERS"
        const val ENV_CARTRIDGE_CLASSPATH_LAYOUT = "CARTRIDGE_CLASSPATH_LAYOUT"
        const val ENV_INTERSHOP_SERVLETENGINE_CONNECTOR_ADDRESS = "INTERSHOP_SERVLETENGINE_CONNECTOR_ADDRESS"
        const val ENV_INTERSHOP_SERVLETENGINE_CONNECTOR_PORT = "INTERSHOP_SERVLETENGINE_CONNECTOR_PORT"
        const val ENV_SERVER_NAME = "SERVER_NAME"
        const val ENV_ENABLE_HEAPDUMP = "ENABLE_HEAPDUMP"
        const val ENV_ENABLE_GCLOG = "ENABLE_GCLOG"
    }

    private var classpathLayout: Set<ClasspathLayout> = setOf()
    private var triggerDbPrepare: Boolean? = null
    private var serverName: String? = null
    private var containerName: String? = null
    private var databaseConfig: DatabaseParameters? = null
    private var portConfig: ASPortConfiguration? = null
    private var cartridgeList: Set<String> = setOf()
    private var additionalParameters: AdditionalICMParameters? = null
    private var debugOptions: JavaDebugSupport? = null
    private var enableHeapDump: Boolean? = null
    private var enableGCLog: Boolean? = null

    fun withClasspathLayout(classpathLayout: Set<ClasspathLayout>) : ICMContainerEnvironmentBuilder {
        this.classpathLayout = classpathLayout
        return this
    }

    fun triggerDbPrepare(triggerDbPrepare: Boolean) : ICMContainerEnvironmentBuilder {
        this.triggerDbPrepare = triggerDbPrepare
        return this
    }

    fun withServerName(serverName: String) : ICMContainerEnvironmentBuilder {
        this.serverName = serverName.takeIf { it.isNotEmpty() }
        return this
    }

    fun withContainerName(containerName: String) : ICMContainerEnvironmentBuilder {
        this.containerName = containerName.takeIf { it.isNotEmpty() }
        return this
    }

    fun withDatabaseConfig(databaseConfig: DatabaseParameters) : ICMContainerEnvironmentBuilder {
        this.databaseConfig = databaseConfig
        return this
    }

    fun withPortConfig(portConfig: ASPortConfiguration) : ICMContainerEnvironmentBuilder {
        this.portConfig = portConfig
        return this
    }

    fun withCartridgeList(cartridgeList: Set<String>) : ICMContainerEnvironmentBuilder {
        this.cartridgeList = cartridgeList
        return this
    }

    fun withAdditionalParameters(additionalParameters: AdditionalICMParameters) : ICMContainerEnvironmentBuilder {
        this.additionalParameters = additionalParameters
        return this
    }

    fun withDebugOptions(debugOptions: JavaDebugSupport) : ICMContainerEnvironmentBuilder {
        this.debugOptions = debugOptions
        return this
    }

    fun enableHeapDump(enableHeapDump: Boolean) : ICMContainerEnvironmentBuilder {
        this.enableHeapDump = enableHeapDump
        return this
    }

    fun enableGCLog(enableGCLog: Boolean) : ICMContainerEnvironmentBuilder {
        this.enableGCLog = enableGCLog
        return this
    }

    fun build() : ContainerEnvironment {
        val env = ContainerEnvironment()
        additionalParameters?.run {
            env.add(ENV_ADDITIONAL_PARAMETERS, render())
        }

        // configure debugging
        debugOptions?.run {
            env.add(ENV_DEBUG_ICM, renderEnvVariableValue())
        }

        // add database config to env
        databaseConfig?.run {
            env.add(ENV_DB_TYPE, type.get())
                    .add(ENV_DB_JDBC_URL, jdbcUrl.get())
                    .add(ENV_DB_JDBC_USER, jdbcUser.get())
                    .add(ENV_DB_JDBC_PASSWORD, jdbcPassword.get())
        }

        // configure servlet engine port
        portConfig?.run {
            env.add(ENV_INTERSHOP_SERVLETENGINE_CONNECTOR_PORT, servletEngine.get().containerPort)
        }

        // configure servlet engine address
        containerName?.run {
            env.add(ENV_INTERSHOP_SERVLETENGINE_CONNECTOR_ADDRESS, this)
        }

        // add cartridge list (values separated by space)
        if (cartridgeList.isNotEmpty()) {
            env.add(ENV_CARTRIDGE_LIST, cartridgeList.joinToString(separator = " "))
        }

        // ensure release (product cartridges) and source (customization cartridges) layouts are recognized
        if (classpathLayout.isNotEmpty()) {
            env.add(ENV_CARTRIDGE_CLASSPATH_LAYOUT, classpathLayout.joinToString(separator = ",") { it.value })
        }

        serverName?.run {
            env.add(ENV_SERVER_NAME, this)
        }

        triggerDbPrepare?.run {
            env.add(ENV_IS_DBPREPARE, triggerDbPrepare)
        }
        enableHeapDump?.run {
            env.add(ENV_ENABLE_HEAPDUMP, enableHeapDump)
        }
        enableGCLog?.run {
            env.add(ENV_ENABLE_GCLOG, enableGCLog)
        }
        return env
    }

    enum class ClasspathLayout(val value: String) {
        RELEASE("release"), SOURCE("source"), ECLIPSE("eclipse")
    }
}
