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

import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.tasks.PullExtraImage
import com.intershop.gradle.icm.docker.tasks.RemoveContainerByName
import org.gradle.api.Plugin
import org.gradle.api.Project

class ICMDockerREADMEPushPlugin : Plugin<Project> {

    /**
     * Main method of a plugin.
     *
     * @param project target project
     */
    override fun apply(project: Project) {
        with(project) {
            if (project.rootProject == this) {
                logger.info("ICM Docker README Push plugin for projects will be initialized")

                val extension = extensions.findByType(
                    IntershopDockerExtension::class.java
                ) ?: extensions.create("intershop_docker", IntershopDockerExtension::class.java, project)

                val pullImg = project.tasks.register("pullReadmePush", PullExtraImage::class.java) { task ->
                    task.group = "icm container readme push"
                    task.description = "Pull image from registry for pushing readme"
                    task.image.set(extension.readmePush.toolImage)
                }

                project.tasks.register("createReadmePush", DockerCreateContainer::class.java) { task ->
                    task.group = "icm container readme push"
                    task.description = "Create image for pushing readme"

                    task.dependsOn(pullImg)

                    task.targetImageId(project.provider { pullImg.get().image.get() })
                    task.image.set(pullImg.get().image)

                    task.attachStderr.set(true)
                    task.attachStdout.set(true)

                    task.hostConfig.autoRemove.set(true)
                    task.hostConfig.binds.set( mapOf( projectDir.absolutePath to "/myvol") )

                    task.containerName.set("${extension.containerPrefix}-pushReadme")

                    task.cmd.addAll(
                        listOf("--file", "/myvol/README-containers.md", "--debug", "intershophub/icm-webadapter"))
                }

                project.tasks.register("removeReadmePush", RemoveContainerByName::class.java) { task ->
                    task.group = "icm container readme push"
                    task.description = "Remove container for pushing readme from Docker"

                    //task.containerName.set("${extension.containerPrefix}-${containerExt}")
                }
            }
        }
    }
}
