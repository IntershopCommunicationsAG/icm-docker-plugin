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
package com.intershop.gradle.icm.docker

import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.utils.ContainerPreparer
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Main plugin class of this project.
 */
open class ICMDockerProjectPlugin : Plugin<Project> {

    companion object {
        const val TASK_REMOVECONTAINER = "rmContainer"
    }

    override fun apply(project: Project) {
        with(project) {
            if(project.rootProject == this) {
                logger.info("ICM Docker build plugin for projects will be initialized")
                plugins.apply(ICMDockerPlugin::class.java)

                val extension = extensions.findByType(
                        IntershopDockerExtension::class.java
                ) ?: extensions.create("intershop_docker", IntershopDockerExtension::class.java)

                val containerPreparer = ContainerPreparer(project, extension)

                val removeContainerByName = containerPreparer.getRemoveContainerByName()
                val pullImage = containerPreparer.getPullImage()
                val baseContainer = containerPreparer.getBaseContainer(pullImage)
                val startContainer = containerPreparer.getStartContainer(baseContainer)
                val removeContainer = containerPreparer.getFinalizeContainer(startContainer)

                baseContainer.dependsOn(removeContainerByName)
                startContainer.finalizedBy(removeContainer)

            }
        }
    }
}
