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
package com.intershop.gradle.icm.docker.utils.solrcloud

import com.intershop.gradle.icm.docker.tasks.PrepareNetwork
import com.intershop.gradle.icm.docker.tasks.StartExtraContainer
import com.intershop.gradle.icm.docker.utils.AbstractTaskPreparer
import org.gradle.api.Project
import org.gradle.api.provider.Provider

class ZKPreparer(project: Project,
                 networkTask: Provider<PrepareNetwork>): AbstractTaskPreparer(project, networkTask) {

    companion object {
        const val extName: String = "ZK"
    }

    override fun getExtensionName(): String = extName
    override fun getImage(): Provider<String> = extension.images.zookeeper

    init {
        initBaseTasks()

        pullTask.configure {
            it.group = "icm container solrcloud"
        }
        stopTask.configure {
            it.group = "icm container solrcloud"
        }
        removeTask.configure {
            it.group = "icm container solrcloud"
        }

        project.tasks.register("start${getExtensionName()}", StartExtraContainer::class.java) { task ->
            configureContainerTask(task)
            task.group = "icm container solrcloud"
            task.description = "Start Zookeeper component of SolrCloud"

            task.targetImageId(project.provider { pullTask.get().image.get() })
            task.image.set(pullTask.get().image)

            task.hostConfig.portBindings.set(
                listOf("2181:2181", "7000:7000")
            )

            task.envVars.set(
                mutableMapOf(
                    "ZOO_MY_ID" to "1",
                    "ZOO_PORT" to "2181",
                    "ZOO_SERVERS" to
                            "server.1=${extension.containerPrefix}-${getContainerExt()}:2888:3888;2181",
                    "ZOO_4LW_COMMANDS_WHITELIST" to "mntr, conf, ruok",
                    "ZOO_CFG_EXTRA" to
                            "metricsProvider.className=org.apache.zookeeper.metrics.prometheus." +
                            "PrometheusMetricsProvider metricsProvider.httpPort=7000 " +
                            "metricsProvider.exportJvmInfo=true"
                )
            )

            task.hostConfig.network.set(networkId)
            task.logger.quiet("The ZK for SolrCloud can be connected with {}:2181", task.containerName)
            task.dependsOn(pullTask, networkTask)
        }
    }
}
