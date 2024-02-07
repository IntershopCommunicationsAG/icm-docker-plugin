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
package com.intershop.gradle.icm.docker.tasks

import com.intershop.gradle.icm.utils.Probe
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

open class WaitForServer @Inject constructor(objectFactory: ObjectFactory) : DefaultTask() {

    @get:Internal
    val probes: ListProperty<Probe> = objectFactory.listProperty(Probe::class.java)

    @TaskAction
    fun waiting() {

        with(probes.get()) {
            forEach { probe ->
                val success = probe.execute()
                if (!success) {
                    throw GradleException("Failed to wait for server: probe $probe failed")
                }
            }
        }

        project.logger.quiet("Server is ready!")
    }
}

