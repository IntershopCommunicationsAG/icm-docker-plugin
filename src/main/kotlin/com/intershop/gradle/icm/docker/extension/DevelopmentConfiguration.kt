/*
 * Copyright 2019 Intershop Communications AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.intershop.gradle.icm.docker.extension

import com.intershop.gradle.icm.docker.utils.Configuration
import com.intershop.gradle.icm.docker.utils.PortMapping
import org.gradle.api.GradleException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.provider.SetProperty
import org.gradle.wrapper.GradleUserHomeLookup
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.Serializable
import java.time.Duration
import java.util.Locale
import java.util.Properties
import javax.inject.Inject

/**
 * Extends the extension with important
 * file directories for the server.
 *
 * @constructor creates a configuration from environment variables.
 */
open class DevelopmentConfiguration
@Inject constructor(objectFactory: ObjectFactory, providerFactory: ProviderFactory) {

    private val logger: Logger = LoggerFactory.getLogger(DevelopmentConfiguration::class.java)

    companion object {
        /**
         * Logger instance for logging.
         */
        val log: Logger = LoggerFactory.getLogger(this::class.java.name)

        @Deprecated(
            message = "Environment variable LICENSEDIR is unsupported since 2.9.0 and no longer used",
            level = DeprecationLevel.WARNING
        )
        const val LICENSE_DIR_ENV = "LICENSEDIR"
        const val CONFIG_DIR_ENV = "CONFIGDIR"

        @Deprecated(
            message = "LICENSE_DIR_SYS is unsupported since 2.9.0 and no longer used",
            level = DeprecationLevel.WARNING
        )
        const val LICENSE_DIR_SYS = "licenseDir"
        const val CONFIG_DIR_SYS = "configDir"

        const val APPSRV_AS_CONTAINER_ENV = "APPSRVASCONTAINER"
        const val APPSRV_AS_CONTAINER_SYS = "appSrvAsContainer"

        @Deprecated(
            message = "DEFAULT_LIC_PATH is unsupported since 2.9.0 and no longer used",
            level = DeprecationLevel.WARNING
        )
        const val DEFAULT_LIC_PATH = "icm-default/lic"
        const val DEFAULT_CONFIG_PATH = "icm-default/conf"

        @Deprecated(
            message = "LICENSE_FILE_NAME is unsupported since 2.9.0 and no longer used",
            level = DeprecationLevel.WARNING
        )
        const val LICENSE_FILE_NAME = "license.xml"
        const val CONFIG_FILE_NAME = "icm.properties"

        const val PORT_MAPPING_AS_SERVICE_CONNECTOR = "SERVICE_CONNECTOR"
        const val PORT_MAPPING_AS_MANAGEMENT_CONNECTOR = "MANAGEMENT_CONNECTOR"
        const val PORT_MAPPING_AS_DEBUG = "DEBUG"
        const val PORT_MAPPING_AS_JMX = "JMX"

        const val ENV_PREFIX = "ISH_ENV_"

        val DEVPROPS = listOf(
            "intershop.extensions.CheckSource",
            "intershop.queries.CheckSource",
            "intershop.pipelines.CheckSource",
            "intershop.pagelets.CheckSource",
            "intershop.webforms.CheckSource",
            "intershop.template.CheckSource",
            "intershop.template.CheckSourceModified",
            "intershop.template.isfilebundle.CheckSource",
            "intershop.urlrewrite.CheckSource",

            "intershop.pipelines.PreloadFromCartridges",
            "intershop.pipelines.PreloadFromSites",
            "intershop.pipelets.PreloadFromCartridges",
            "intershop.template.CompileOnStartup",
            "intershop.webforms.Preload",
            "intershop.queries.Preload",

            "intershop.pipelines.strict.CheckParameterTypes",
            "intershop.pipelets.OnLoadError",

            "intershop.monitoring.requests",
            "intershop.monitoring.pipelines",
            "intershop.monitoring.pipelets",
            "intershop.monitoring.pipelinenodes",
            "intershop.monitoring.templates",
            "intershop.monitoring.queries",
            "intershop.monitoring.sql",
            "intershop.monitoring.class",
            "intershop.monitoring.maxSensors",

            "intershop.template.PrintTemplateName",
            "intershop.template.PrintTemplateMarker",
            "intershop.session.TimeOut",
            "intershop.CSRFGuard.allowRecovery")
    }

    private val configDirectoryProperty: Property<String> = objectFactory.property(String::class.java)
    private val appserverAsContainerProperty: Property<Boolean> = objectFactory.property(Boolean::class.java)
    private val configProperties: Properties = Properties()
    private val environmentProperties: MutableMap<String, String> = mutableMapOf<String, String>()

    init {
        // read environment
        val gradleUserHomePath = GradleUserHomeLookup.gradleUserHome().absolutePath

        var configDirPath = providerFactory.environmentVariable(CONFIG_DIR_ENV).orNull

        if (configDirPath == null) {
            configDirPath = providerFactory.systemProperty(CONFIG_DIR_SYS).orNull
        }

        if (configDirPath == null) {
            try {
                configDirPath = providerFactory.gradleProperty(CONFIG_DIR_SYS).orNull
            } catch (ise: IllegalStateException) {
                log.error(ise.message)
            }
        }

        if (configDirPath == null) {
            configDirPath = File(File(gradleUserHomePath), DEFAULT_CONFIG_PATH).absolutePath
        }

        configDirectoryProperty.set(configDirPath)

        val configFile = File(configDirectory, CONFIG_FILE_NAME)
        if (configFile.exists() && configFile.canRead()) {
            configProperties.load(configFile.inputStream())
        } else {
            logger.warn("File '{}' does not exists!", configFile.absolutePath)
        }

        appserverAsContainerProperty.convention(false)
    }

    /**
     * AppServer runs as a container if the value is true
     */
    var appserverAsContainer: Boolean
        get() = appserverAsContainerProperty.get()
        set(value) = appserverAsContainerProperty.set(value)

    /**
     * Set of cartridges to be started for instance by task startAS
     */
    val cartridgeList: SetProperty<String> = objectFactory.setProperty(String::class.java)

    /**
     * Set of cartridges for tests to be started for instance by task dbPrepare
     */
    val testCartridgeList: SetProperty<String> = objectFactory.setProperty(String::class.java)

    /**
     * License directory path of the project.
     */
    @Deprecated(
        message = "licenseDirectory is unsupported since 2.9.0 and no longer used",
        level = DeprecationLevel.WARNING
    )
    val licenseDirectory: String
        get() = ""

    @Deprecated(
        message = "licenseFilePath is unsupported since 2.9.0 and no longer used",
        level = DeprecationLevel.WARNING
    )
    val licenseFilePath: String
        get() = ""

    /**
     * Local configuration path of the project.
     */
    val configDirectory: String
        get() = configDirectoryProperty.get()

    val configFilePath: String
        get() = File(configDirectory, CONFIG_FILE_NAME).absolutePath

    fun getConfigProperty(property: String): String {
        return configProperties.getProperty(property, "")
    }

    fun getConfigProperty(property: String, defaultValue: String): String {
        return configProperties.getProperty(property, defaultValue)
    }

    fun getIntProperty(property: String, defaultValue: Int): Int {
        val strValue = configProperties.getProperty(property, defaultValue.toString())
        try {
            return strValue.toInt()
        } catch (e: NumberFormatException) {
            throw GradleException(
                    "Configuration property 'property' is not a valid int value", e)
        }
    }

    fun getDurationProperty(property: String, defaultSeconds: Int): Duration {
        return Duration.ofSeconds(getIntProperty (property, defaultSeconds).toLong())
    }

    /**
     * The database configuration (initialized lazily)
     */
    val databaseConfiguration: DatabaseParameters by lazy {
        val config = DatabaseParameters(objectFactory)
        config.type.set(getConfigProperty(Configuration.DB_TYPE))
        config.jdbcUrl.set(getConfigProperty(Configuration.DB_JDBC_URL))
        config.jdbcUser.set(getConfigProperty(Configuration.DB_USER_NAME))
        config.jdbcPassword.set(getConfigProperty(Configuration.DB_USER_PASSWORD))
        config
    }

    val asEnvironment: String? by lazy {
        val environment = getConfigProperty(Configuration.ICM_AS_ENVIRONMENT)
        environment.ifEmpty {
            null
        }
    }

    /**
     * The webserver configuration (initialized lazily)
     */
    val webserverConfiguration: WebserverConfiguration by lazy {
        val config = WebserverConfiguration(objectFactory)
        config.webserverUrl.set(getConfigProperty(Configuration.WS_URL))
        config.webserverSecureURL.set(getConfigProperty(Configuration.WS_SECURE_URL))
        config
    }

    val developmentProperties: DevelopmentProperties by lazy {
        val config = DevelopmentProperties(objectFactory)
        DEVPROPS.forEach {
            val p = getConfigProperty(it)
            if(p.isNotEmpty()) {
                config.developmentConfig.put( it, p)
            }
        }
        config
    }

    val intershopEnvironmentProperties: EnvironmentProperties by lazy {
        val envConfig = EnvironmentProperties(objectFactory)
        val keys = configProperties.keys.filter {it.toString().startsWith(Configuration.INTERSHOP_ENVIRONMENT_PREFIX)
                && ! it.equals(Configuration.CONTAINER_ENV_PROP)}
            .stream().map { it.toString()}
        keys.forEach {
            val p = getConfigProperty(it)
            val k = it.replaceFirst(Configuration.INTERSHOP_ENVIRONMENT_PREFIX, ENV_PREFIX)
                .uppercase(Locale.getDefault())
            envConfig.config.put(k, p)
        }
        envConfig
    }

    /**
     * The port configuration (initialized lazily)
     */
    val asPortConfiguration: ASPortConfiguration by lazy {
        val config = ASPortConfiguration(objectFactory)
        config.serviceConnector.value(getPortMapping(
                PORT_MAPPING_AS_SERVICE_CONNECTOR,
                Configuration.AS_SERVICE_CONNECTOR_HOST_PORT,
                Configuration.AS_SERVICE_CONNECTOR_HOST_PORT_VALUE,
                Configuration.AS_SERVICE_CONNECTOR_PORT,
                Configuration.AS_SERVICE_CONNECTOR_PORT_VALUE,
                true
        ))
        config.managementConnector.value(getPortMapping(
                PORT_MAPPING_AS_MANAGEMENT_CONNECTOR,
                Configuration.AS_MANAGEMENT_CONNECTOR_HOST_PORT,
                Configuration.AS_MANAGEMENT_CONNECTOR_HOST_PORT_VALUE,
                Configuration.AS_MANAGEMENT_CONNECTOR_PORT,
                Configuration.AS_MANAGEMENT_CONNECTOR_PORT_VALUE
        ))
        config.debug.value(getPortMapping(
                PORT_MAPPING_AS_DEBUG,
                Configuration.AS_DEBUG_PORT,
                Configuration.AS_DEBUG_PORT_VALUE,
                Configuration.AS_DEBUG_CONTAINER_PORT_VALUE
        ))
        config.jmx.value(getPortMapping(
                PORT_MAPPING_AS_JMX,
                Configuration.AS_JMX_CONNECTOR_PORT,
                Configuration.AS_JMX_CONNECTOR_PORT_VALUE,
                Configuration.AS_JMX_CONNECTOR_CONTAINER_PORT_VALUE,
        ))
        config
    }

    class DatabaseParameters(objectFactory: ObjectFactory) : Serializable {
        val type: Property<String> = objectFactory.property(String::class.java)
        val jdbcUrl: Property<String> = objectFactory.property(String::class.java)
        val jdbcUser: Property<String> = objectFactory.property(String::class.java)
        val jdbcPassword: Property<String> = objectFactory.property(String::class.java)
    }

    class DevelopmentProperties(objectFactory: ObjectFactory) : Serializable {
        val developmentConfig: MapProperty<String, String> =
            objectFactory.mapProperty(String::class.java, String::class.java)
    }

    class EnvironmentProperties(objectFactory: ObjectFactory) : Serializable {
        val config: MapProperty<String, String> =
            objectFactory.mapProperty(String::class.java, String::class.java)
    }

    class ASPortConfiguration(objectFactory: ObjectFactory) : Serializable {
        val serviceConnector: Property<PortMapping> = objectFactory.property(PortMapping::class.java)
        val managementConnector: Property<PortMapping> = objectFactory.property(PortMapping::class.java)
        val debug: Property<PortMapping> = objectFactory.property(PortMapping::class.java)
        val jmx: Property<PortMapping> = objectFactory.property(PortMapping::class.java)
    }

    class WebserverConfiguration(objectFactory: ObjectFactory) : Serializable {
        val webserverUrl: Property<String> = objectFactory.property(String::class.java)
        val webserverSecureURL: Property<String> = objectFactory.property(String::class.java)
    }

    fun getPortMapping(
            name: String,
            hostKey: String,
            hostDefaultValue: Int,
            containerValue: Int,
            primary: Boolean = false
    ): PortMapping =
            PortMapping(
                    name = name,
                    hostPort = getIntProperty(hostKey, hostDefaultValue),
                    containerPort = containerValue,
                    primary = primary
            )

    @Suppress("SameParameterValue")
    fun getPortMapping(
            name: String,
            hostKey: String,
            hostDefaultValue: Int,
            containerKey: String,
            containerDefaultValue: Int,
            primary: Boolean = false
    ): PortMapping =
            getPortMapping(
                    name = name,
                    hostKey = hostKey,
                    hostDefaultValue = hostDefaultValue,
                    containerValue = getIntProperty(containerKey, containerDefaultValue),
                    primary = primary
            )

}
