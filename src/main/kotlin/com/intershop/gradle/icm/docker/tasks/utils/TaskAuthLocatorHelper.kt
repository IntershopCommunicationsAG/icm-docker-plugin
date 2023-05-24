/*
 * Copyright 2023 Intershop Communications AG.
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

import com.bmuschko.gradle.docker.internal.RegistryAuthLocator
import org.gradle.api.Project
import java.io.File

object TaskAuthLocatorHelper {

    fun getLocator(project: Project, registryAuthLocator: RegistryAuthLocator) : RegistryAuthLocator {
        var regAuthLocator = registryAuthLocator

        if (project.findProperty("icm.docker.config") != null) {
            val configFilePath = project.property("icm.docker.config").toString()
            val configFile = File(File(configFilePath), "config.json")
            regAuthLocator = RegistryAuthLocator(configFile)
            project.logger.info("This docker configuration is used: '{}'", configFile.absolutePath)
        }

        return regAuthLocator
    }
}