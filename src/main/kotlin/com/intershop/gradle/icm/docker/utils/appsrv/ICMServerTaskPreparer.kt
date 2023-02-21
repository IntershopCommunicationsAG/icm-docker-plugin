/*
 * Copyright 2022 Intershop Communications AG.
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
package com.intershop.gradle.icm.docker.utils.appsrv

import com.intershop.gradle.icm.docker.ICMDockerPlugin
import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.tasks.BuildImage
import com.intershop.gradle.icm.docker.tasks.PrepareNetwork
import com.intershop.gradle.icm.docker.tasks.StartServerContainer
import com.intershop.gradle.icm.docker.tasks.utils.ICMContainerEnvironmentBuilder
import com.intershop.gradle.icm.docker.utils.Configuration
import com.intershop.gradle.icm.docker.utils.HostAndPort
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType
import java.net.URI


class ICMServerTaskPreparer(
    project: Project,
    val networkTask: Provider<PrepareNetwork>) : AbstractASTaskPreparer(project, networkTask) {

    companion object {
        const val extName: String = "AsTestContainer"
    }

    override fun getExtensionName(): String = extName

    override fun getImage(): Provider<String> {
        val buildTestImageTask = project.tasks.named(ICMDockerPlugin.BUILD_TEST_IMAGE, BuildImage::class.java)
        val imageProvider = project.provider { buildTestImageTask.get().images.get() }
        return imageProvider.map { it.first() }
    }

    init{
        project.tasks.register("start${this.getExtensionName()}", StartServerContainer::class.java) { task ->
            configureContainerTask(task)

            task.description = "Starts Production Application Server in a container - only for use in icm-as"

            task.targetImageId(project.provider { pullTask.get().image.get() })
            task.image.set(pullTask.get().image)

            task.hostConfig.binds.set(project.provider {
                getServerVolumes(task, false).apply {
                    project.logger.info(
                        "Using the following volume binds for container startup in task {}: {}",
                        task.name, this
                    )
                }
            })

            task.withPortMappings(*getPortMappings().toTypedArray())

            task.hostConfig.network.set(networkId)
            task.withEnvironment(
                ICMContainerEnvironmentBuilder()
                    .withClasspathLayout(
                        setOf(
                            ICMContainerEnvironmentBuilder.ClasspathLayout.RELEASE,
                            ICMContainerEnvironmentBuilder.ClasspathLayout.SOURCE
                        )
                    )
                    .withContainerName(getContainerName())
                    .build()
            )

            val devConfig = project.extensions.getByType<IntershopDockerExtension>().developmentConfig
            task.withHttpProbe(
                URI.create(
                    StartServerContainer.PATTERN_READINESS_PROBE_URL.format(
                        devConfig.asPortConfiguration.serviceConnector.get().hostPort
                    )
                ),
                devConfig.getDurationProperty(
                    Configuration.AS_READINESS_PROBE_INTERVAL,
                    Configuration.AS_READINESS_PROBE_INTERVAL_VALUE
                ),
                devConfig.getDurationProperty(
                    Configuration.AS_READINESS_PROBE_TIMEOUT,
                    Configuration.AS_READINESS_PROBE_TIMEOUT_VALUE
                )
            )

            if(mailServerTaskProvider != null) {
                task.mailServer = project.provider {
                    HostAndPort(
                        mailServerTaskProvider!!.get().containerName.get(),
                        mailServerTaskProvider!!.get().getPrimaryPortMapping().get().containerPort
                    )
                }
            }
        }
    }
}
