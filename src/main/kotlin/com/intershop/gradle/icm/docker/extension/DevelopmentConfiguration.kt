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
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.provider.SetProperty
import org.gradle.wrapper.GradleUserHomeLookup
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.Serializable
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

        const val LICENSE_DIR_ENV = "LICENSEDIR"
        const val CONFIG_DIR_ENV = "CONFIGDIR"

        const val LICENSE_DIR_SYS = "licenseDir"
        const val CONFIG_DIR_SYS = "configDir"

        const val APPSRV_AS_CONTAINER_ENV = "APPSRVASCONTAINER"
        const val APPSRV_AS_CONTAINER_SYS = "appSrvAsContainer"

        const val DEFAULT_LIC_PATH = "icm-default/lic"
        const val DEFAULT_CONFIG_PATH = "icm-default/conf"

        const val LICENSE_FILE_NAME = "license.xml"
        const val CONFIG_FILE_NAME = "icm.properties"
    }

    private val licenseDirectoryProperty: Property<String> = objectFactory.property(String::class.java)
    private val configDirectoryProperty: Property<String> = objectFactory.property(String::class.java)
    private val appserverAsContainerProperty: Property<Boolean> = objectFactory.property(Boolean::class.java)
    private val configProperties: Properties = Properties()

    init {
        // read environment
        val gradleUserHomePath = GradleUserHomeLookup.gradleUserHome().absolutePath

        var licDirPath = providerFactory.environmentVariable(LICENSE_DIR_ENV).forUseAtConfigurationTime().orNull
        var configDirPath = providerFactory.environmentVariable(CONFIG_DIR_ENV).forUseAtConfigurationTime().orNull

        // read system if necessary
        if (licDirPath == null) {
            licDirPath = providerFactory.systemProperty(LICENSE_DIR_SYS).forUseAtConfigurationTime().orNull
        }

        if (configDirPath == null) {
            configDirPath = providerFactory.systemProperty(CONFIG_DIR_SYS).forUseAtConfigurationTime().orNull
        }

        if (licDirPath == null) {
            try {
                licDirPath = providerFactory.gradleProperty(LICENSE_DIR_SYS).forUseAtConfigurationTime().orNull
            } catch (ise: IllegalStateException) {
                log.error(ise.message)
            }
        }

        if (configDirPath == null) {
            try {
                configDirPath = providerFactory.gradleProperty(CONFIG_DIR_SYS).forUseAtConfigurationTime().orNull
            } catch (ise: IllegalStateException) {
                log.error(ise.message)
            }
        }

        if (licDirPath == null) {
            licDirPath = File(File(gradleUserHomePath), DEFAULT_LIC_PATH).absolutePath
        }

        if (configDirPath == null) {
            configDirPath = File(File(gradleUserHomePath), DEFAULT_CONFIG_PATH).absolutePath
        }

        licenseDirectoryProperty.set(licDirPath)
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
    val licenseDirectory: String
        get() = licenseDirectoryProperty.get()

    val licenseFilePath: String
        get() = File(licenseDirectory, LICENSE_FILE_NAME).absolutePath

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
                    "Configuration property ${Configuration.AS_CONNECTOR_CONTAINER_PORT} is not a valid int value", e)
        }
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

    /**
     * The port configuration (initialized lazily)
     */
    val asPortConfiguration: ASPortConfiguration by lazy {
        val config = ASPortConfiguration(objectFactory)
        config.servletEngine.value(getPortMapping(
                Configuration.AS_CONNECTOR_CONTAINER_PORT,
                Configuration.AS_CONNECTOR_CONTAINER_PORT_VALUE,
                Configuration.AS_EXT_CONNECTOR_PORT,
                Configuration.AS_EXT_CONNECTOR_PORT_VALUE))
        config.debug.value(getPortMapping(
                Configuration.AS_DEBUG_CONTAINER_PORT_VALUE,
                Configuration.AS_DEBUG_PORT,
                Configuration.AS_DEBUG_PORT_VALUE))
        config.jmx.value(getPortMapping(
                Configuration.AS_JMX_CONNECTOR_CONTAINER_PORT_VALUE,
                Configuration.AS_JMX_CONNECTOR_PORT,
                Configuration.AS_JMX_CONNECTOR_PORT_VALUE))
        config
    }

    class DatabaseParameters(objectFactory: ObjectFactory) : Serializable {
        val type: Property<String> = objectFactory.property(String::class.java)
        val jdbcUrl: Property<String> = objectFactory.property(String::class.java)
        val jdbcUser: Property<String> = objectFactory.property(String::class.java)
        val jdbcPassword: Property<String> = objectFactory.property(String::class.java)
    }

    class ASPortConfiguration(objectFactory: ObjectFactory) : Serializable {
        val servletEngine: Property<PortMapping> = objectFactory.property(PortMapping::class.java)
        val debug: Property<PortMapping> = objectFactory.property(PortMapping::class.java)
        val jmx: Property<PortMapping> = objectFactory.property(PortMapping::class.java)
    }

    private fun getPortMapping(containerValue: Int, hostKey: String, hostDefaultValue: Int): PortMapping =
            PortMapping(containerValue, getIntProperty(hostKey, hostDefaultValue))

    @Suppress("SameParameterValue")
    private fun getPortMapping(
            containerKey: String, containerDefaultValue: Int, hostKey: String,
            hostDefaultValue: Int,
    ): PortMapping =
            getPortMapping(getIntProperty(containerKey, containerDefaultValue), hostKey, hostDefaultValue)

}
