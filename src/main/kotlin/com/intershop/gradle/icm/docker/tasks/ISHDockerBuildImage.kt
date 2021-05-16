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

import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.intershop.gradle.icm.docker.ICMDockerPlugin
import com.intershop.gradle.icm.docker.utils.BuildImageRegistry
import org.gradle.api.GradleException
import org.gradle.api.services.BuildServiceRegistry
import org.gradle.api.services.internal.BuildServiceRegistryInternal

open class ISHDockerBuildImage: DockerBuildImage() {

    init {
        doLast {
            val serviceRegistry = services.get(BuildServiceRegistryInternal::class.java)
            val buildImgRegistry = getBuildService(serviceRegistry, ICMDockerPlugin.BUILD_IMG_REGISTRY)
            buildImgRegistry?.addImages(images.get().toList())
        }
    }

    private fun getBuildService(registry: BuildServiceRegistry, name: String): BuildImageRegistry? {
        val registration = registry.registrations.findByName(name)
            ?: throw GradleException ("Unable to find build service with name '$name'.")

        val buildservice = registration.service.get()
        return if(buildservice is BuildImageRegistry) {
            buildservice
        } else {
            null
        }
    }
}
