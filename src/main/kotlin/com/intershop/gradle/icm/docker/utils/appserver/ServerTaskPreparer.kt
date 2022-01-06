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

package com.intershop.gradle.icm.docker.utils.appserver

import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.tasks.PrepareNetwork
import com.intershop.gradle.icm.docker.tasks.StartServerContainer
import com.intershop.gradle.icm.docker.tasks.utils.ICMContainerEnvironmentBuilder
import com.intershop.gradle.icm.docker.tasks.utils.ICMContainerEnvironmentBuilder.ClasspathLayout.RELEASE
import com.intershop.gradle.icm.docker.tasks.utils.ICMContainerEnvironmentBuilder.ClasspathLayout.SOURCE
import com.intershop.gradle.icm.docker.utils.Configuration
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType
import java.net.URI

class ServerTaskPreparer(
        project: Project,
        networkTask: Provider<PrepareNetwork>,
) : AbstractTaskPreparer(project, networkTask) {

    companion object {
        const val extName: String = "AS"
    }

    override fun getExtensionName(): String = extName

    init {
        initAppTasks()

        project.tasks.register("start${this.getExtensionName()}", StartServerContainer::class.java) { task ->
            configureContainerTask(task)
            task.description = "Start Application Server inside a container"

            task.targetImageId(project.provider { pullTask.get().image.get() })
            task.image.set(pullTask.get().image)

            task.hostConfig.binds.set(getServerVolumes().apply {
                project.logger.info("Using the following volume binds for container startup in task {}: {}",
                        task.name,this)
            })

            task.withPortMappings(*getPortMappings().toTypedArray())

            task.hostConfig.network.set(networkId)
            task.withEnvironment(
                ICMContainerEnvironmentBuilder()
                    .withClasspathLayout(setOf(RELEASE, SOURCE))
                    .withContainerName(getContainerName())
                    .build()
            )

            val devConfig = project.extensions.getByType<IntershopDockerExtension>().developmentConfig
            task.withHttpProbe(
                    URI.create(
                            StartServerContainer.PATTERN_READINESS_PROBE_URL.format(
                                    devConfig.asPortConfiguration.servletEngine.get().hostPort
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

            task.dependsOn(prepareServer, pullTask, networkTask)
        }
    }

}
