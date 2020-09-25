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
package com.intershop.gradle.icm.docker.utils.network

import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.tasks.PrepareNetwork
import com.intershop.gradle.icm.docker.tasks.RemoveNetwork
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

class TaskPreparer(val project: Project,
                   val extension: IntershopDockerExtension) {

    companion object {
        const val PREPARE_NETWORK = "prepareNetwork"
        const val REMOVE_NETWORK = "removeNetwork"
    }

    init {
        project.tasks.register(PREPARE_NETWORK, PrepareNetwork::class.java) { task ->
            task.networkName.set("${extension.containerPrefix}-network")
            task.group = "icm container"
        }

        project.tasks.register(REMOVE_NETWORK, RemoveNetwork::class.java) { task ->
            task.networkName.set("${extension.containerPrefix}-network")
            task.group = "icm container"
        }
    }

    val createNetworkTask : TaskProvider<PrepareNetwork> by lazy {
            project.tasks.named(PREPARE_NETWORK, PrepareNetwork::class.java)
        }

    val removeNetworkTask : TaskProvider<RemoveNetwork> by lazy {
        project.tasks.named(REMOVE_NETWORK, RemoveNetwork::class.java)
    }

}
