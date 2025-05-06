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

package com.intershop.gradle.icm.docker.utils

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceRegistry

abstract class GenerateOpenAPIModelService : BuildService<BuildServiceParameters.None> {
    companion object {
        const val NAME = "generateOpenAPIModelService"

        fun register(project: Project) {
            project.gradle.sharedServices.registerIfAbsent(NAME, GenerateOpenAPIModelService::class.java) {
                it.parameters
            }
        }

        fun lookup(provideBuildServiceRegistry: Provider<BuildServiceRegistry>): Provider<out BuildService<*>> {
            val registration = provideBuildServiceRegistry.get().registrations.findByName(NAME)
                ?: throw GradleException("Unable to find build service with name '$NAME'.")

            return registration.service
        }
    }
}
