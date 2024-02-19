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

import com.intershop.gradle.icm.docker.tasks.CreateASContainer
import com.intershop.gradle.icm.docker.tasks.PrepareNetwork
import com.intershop.gradle.icm.docker.tasks.StartServerContainer
import com.intershop.gradle.icm.docker.utils.Configuration
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import java.net.URI

class ASTaskPreparer(
        project: Project,
        val networkTask: Provider<PrepareNetwork>,
) : AbstractASTaskPreparer(project, networkTask) {

    companion object {
        const val extName: String = "AS"
    }

    override fun getExtensionName(): String = extName

    init {
        val volumes = project.provider { getServerVolumes() }

        val createTask = registerCreateASContainerTask(findTask, volumes)
        createTask.configure { task ->
            task.withPortMappings(*getPortMappings().toTypedArray())
            task.forCustomization(dockerExtension.containerPrefix)
        }

        registerStartContainerTask(createTask, StartServerContainer::class.java).configure { task ->
            task.description = """
                Starts Production Application Server in a container.
                ATTENTION: task ${
                taskNameOf("create")
            } is executed in preparation of this task and supports parameters that you may expect to get supported by this task.
            """.trimIndent()
            task.withHttpProbe(
                    URI.create(
                            CreateASContainer.PATTERN_READINESS_PROBE_URL.format(
                                    devConfig.asPortConfiguration.managementConnector.get().hostPort
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
        }

    }
}

