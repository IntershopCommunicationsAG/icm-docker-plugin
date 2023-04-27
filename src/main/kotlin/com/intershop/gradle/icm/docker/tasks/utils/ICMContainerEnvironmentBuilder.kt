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
@file:Suppress("TooManyFunctions")
package com.intershop.gradle.icm.docker.tasks.utils

import com.intershop.gradle.icm.docker.extension.DevelopmentConfiguration.WebserverConfiguration
import com.intershop.gradle.icm.docker.extension.DevelopmentConfiguration.ASPortConfiguration
import com.intershop.gradle.icm.docker.extension.DevelopmentConfiguration.DatabaseParameters
import com.intershop.gradle.icm.docker.extension.DevelopmentConfiguration.DevelopmentProperties
import com.intershop.gradle.icm.docker.extension.DevelopmentConfiguration.EnvironmentProperties
import com.intershop.gradle.icm.docker.utils.Configuration
import com.intershop.gradle.icm.docker.utils.HostAndPort
import com.intershop.gradle.icm.utils.JavaDebugSupport
import org.gradle.api.provider.Provider

/**
 * Encapsulates a builder for [ContainerEnvironment] instances for an ICM appserver.
 */
class ICMContainerEnvironmentBuilder {

    companion object {
        const val ENV_IS_DBPREPARE = "IS_DBPREPARE"
        const val ENV_ENVIRONMENT = "ENVIRONMENT"
        const val ENV_DEBUG_ICM = "DEBUG_ICM"
        const val ENV_DB_TYPE = "INTERSHOP_DATABASETYPE"
        const val ENV_DB_JDBC_URL = "INTERSHOP_JDBC_URL"
        const val ENV_DB_JDBC_USER = "INTERSHOP_JDBC_USER"
        const val ENV_DB_JDBC_PASSWORD = "INTERSHOP_JDBC_PASSWORD"
        const val ENV_CARTRIDGE_LIST = "CARTRIDGE_LIST"
        const val ENV_ADDITIONAL_PARAMETERS = "ADDITIONAL_PARAMETERS"
        const val ENV_INTERSHOP_WEBSERVERURL = "INTERSHOP_WEBSERVERURL"
        const val ENV_INTERSHOP_WEBSERVERSECUREURL = "INTERSHOP_WEBSERVERSECUREURL"
        const val ENV_CARTRIDGE_CLASSPATH_LAYOUT = "CARTRIDGE_CLASSPATH_LAYOUT"
        const val ENV_INTERSHOP_SERVLETENGINE_CONNECTOR_ADDRESS = "INTERSHOP_SERVLETENGINE_CONNECTOR_ADDRESS"
        const val ENV_INTERSHOP_SERVLETENGINE_CONNECTOR_PORT = "INTERSHOP_SERVLETENGINE_CONNECTOR_PORT"
        const val ENV_INTERSHOP_SERVLETENGINE_MANAGEMENTCONNECTOR_PORT =
                "INTERSHOP_SERVLETENGINE_MANAGEMENTCONNECTOR_PORT"
        const val ENV_SERVER_NAME = "SERVER_NAME"
        const val ENV_ENABLE_HEAPDUMP = "ENABLE_HEAPDUMP"
        const val ENV_ENABLE_GCLOG = "ENABLE_GCLOG"
        const val ENV_MAIL = "ISH_ENV_MAIL"
        const val PROP_MAIL_HOST = "mail.smtp.host"
        const val PROP_MAIL_PORT = "mail.smtp.port"
    }

    private var classpathLayout: Set<ClasspathLayout> = setOf()
    private var triggerDbPrepare: Boolean? = null
    private var asEnvironment: String? = null
    private var serverName: String? = null
    private var containerName: String? = null
    private var databaseConfig: DatabaseParameters? = null
    private var webserverConfig: WebserverConfiguration? = null
    private var portConfig: ASPortConfiguration? = null
    private var cartridgeList: Set<String> = setOf()
    private var additionalParameters: AdditionalICMParameters? = null
    private var debugOptions: JavaDebugSupport? = null
    private var enableHeapDump: Boolean? = null
    private var enableGCLog: Boolean? = null
    private var solrCloudTZookeeperHostList : Provider<String>? = null
    private var mailServer : Provider<HostAndPort>? = null
    private var developmentProperties: DevelopmentProperties? = null
    private var intershopEnvironmentProperties: EnvironmentProperties? = null

    fun withClasspathLayout(classpathLayout: Set<ClasspathLayout>) : ICMContainerEnvironmentBuilder {
        this.classpathLayout = classpathLayout
        return this
    }

    fun withEnvironment(asEnvironment: String?) : ICMContainerEnvironmentBuilder {
        this.asEnvironment = asEnvironment
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

    fun withWebserverConfig(webserverConfig: WebserverConfiguration) : ICMContainerEnvironmentBuilder {
        this.webserverConfig = webserverConfig
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

    fun withSolrCloudZookeeperHostList(hostList : Provider<String>) : ICMContainerEnvironmentBuilder {
        this.solrCloudTZookeeperHostList = hostList
        return this
    }

    fun withMailServer(mailServer : Provider<HostAndPort>) : ICMContainerEnvironmentBuilder {
        this.mailServer = mailServer
        return this
    }

    fun withDevelopmentConfig(developmentProperties: DevelopmentProperties): ICMContainerEnvironmentBuilder {
        this.developmentProperties = developmentProperties
        return this
    }

    fun withEnvironmentProperties(environmentProperties: EnvironmentProperties) : ICMContainerEnvironmentBuilder {
        this.intershopEnvironmentProperties = environmentProperties
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

        webserverConfig?.run {
            env.add(ENV_INTERSHOP_WEBSERVERURL, webserverUrl.get())
                .add(ENV_INTERSHOP_WEBSERVERSECUREURL, webserverSecureURL.get())
        }

        // configure servlet engine connector ports
        portConfig?.run {
            env.add(ENV_INTERSHOP_SERVLETENGINE_CONNECTOR_PORT, serviceConnector.get().containerPort)
            env.add(ENV_INTERSHOP_SERVLETENGINE_MANAGEMENTCONNECTOR_PORT, managementConnector.get().containerPort)
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
            env.add(ENV_IS_DBPREPARE, this)
        }

        asEnvironment?.run {
            env.add(ENV_ENVIRONMENT, this)
        }

        enableHeapDump?.run {
            env.add(ENV_ENABLE_HEAPDUMP, this)
        }
        enableGCLog?.run {
            env.add(ENV_ENABLE_GCLOG, this)
        }
        solrCloudTZookeeperHostList?.run {
            if (isPresent) {
                env.add(ContainerEnvironment.propertyNameToEnvName(Configuration.SOLR_CLOUD_HOSTLIST), this)
            }
        }
        mailServer?.run {
            if (isPresent) {
                val value : HostAndPort = get()
                env.add(ENV_MAIL, "$PROP_MAIL_HOST=${value.hostName},$PROP_MAIL_PORT=${value.port}")
            }
        }

        val properties = developmentProperties?.developmentConfig?.orNull
        properties?.keys?.forEach {
            env.add(ContainerEnvironment.propertyNameToEnvName(it), properties[it])
        }

        intershopEnvironmentProperties?.run {
            this.config.get().forEach { (key, value) ->
                env.add(key, value)
            }
        }

        return env
    }

    enum class ClasspathLayout(val value: String) {
        RELEASE("release"), SOURCE("source"), ECLIPSE("eclipse")
    }
}
