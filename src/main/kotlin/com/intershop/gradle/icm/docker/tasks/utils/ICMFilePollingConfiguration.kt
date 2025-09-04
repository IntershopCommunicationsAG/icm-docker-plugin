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

import com.intershop.gradle.icm.docker.extension.DevelopmentConfiguration
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Purpose of this class is to ensure the ICM is configured to use the [com.intershop.beehive.file.internal.monitoring.PollingFileMonitoringManagerImpl]
 * instead of the [com.intershop.beehive.file.internal.monitoring.FileMonitoringManagerImpl] (default) to allow all the [*.CheckSource]
 * properties to work.
 * So if `icm.properties`:
 *  * does not contain the property `intershop.file.monitoring.polling` the `intershop.file.monitoring.polling` is `true` in ICM
 *  * contains the property `intershop.file.monitoring.polling` set to `false` the `intershop.file.monitoring.polling` is `false` in ICM (explicit developer decision)
 *  Furthermore the value of the property `intershop.file.monitoring.pollInterval` (polling interval in seconds) is transferred to the ICM as is to
 *  allow to set the polling interval.
 *
 * @see com.intershop.beehive.file.internal.monitoring.PollingFileMonitoringManagerImpl
 * @see com.intershop.beehive.file.internal.monitoring.FileMonitoringManagerImpl
 */
open class ICMFilePollingConfiguration(val isEnabled: (Unit) -> Boolean, val interval : (Unit) -> Duration)  {
    companion object {
        const val PROP_ENABLED = "intershop.file.monitoring.polling"
        const val PROP_INTERVAL = "intershop.file.monitoring.pollInterval"

        fun fromDevelopmentConfiguration(developmentConfiguration: DevelopmentConfiguration): ICMFilePollingConfiguration {
            return ICMFilePollingConfiguration({
                developmentConfiguration.getConfigProperty(
                    PROP_ENABLED,
                    true.toString()
                ).toBoolean()
            }, {
                developmentConfiguration.getConfigProperty(PROP_INTERVAL, 1000.toString()).toLong().toDuration(
                    DurationUnit.MILLISECONDS
                )
            })
        }

    }

    fun isEnabled() : Boolean = isEnabled.invoke(Unit)

    fun getInterval() : Duration = interval.invoke(Unit)

    fun applyICMParameters(actualApply : (enabled: Pair<String, Boolean>, interval: Pair<String, Long>) -> Unit) {
        actualApply.invoke(Pair(PROP_ENABLED, isEnabled()), Pair(PROP_INTERVAL, getInterval().inWholeMilliseconds))
    }
}