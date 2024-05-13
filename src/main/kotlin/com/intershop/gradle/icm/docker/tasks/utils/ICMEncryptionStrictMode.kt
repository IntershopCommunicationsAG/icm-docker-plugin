package com.intershop.gradle.icm.docker.tasks.utils

import com.intershop.gradle.icm.docker.extension.DevelopmentConfiguration
import com.intershop.gradle.icm.utils.ICMEncryptionStrictMode
import com.intershop.gradle.icm.utils.ICMEncryptionStrictMode as GradleICMEncryptionStrictMode

/**
 * Extension of [com.intershop.gradle.icm.utils.ICMEncryptionStrictMode] for the docker plugin providing an
 * additional factory methods using [com.intershop.gradle.icm.docker.extension.DevelopmentConfiguration]
 */
class ICMEncryptionStrictMode(isStrictModeEnabled: (Unit) -> Boolean) :
    GradleICMEncryptionStrictMode(isStrictModeEnabled) {

    companion object {
        fun fromDevelopmentConfiguration(developmentConfiguration: DevelopmentConfiguration) : ICMEncryptionStrictMode {
            return ICMEncryptionStrictMode { developmentConfiguration.getConfigProperty(PROP_STRICT_MODE_ENABLED, false.toString()).toBoolean() }
        }
    }
}