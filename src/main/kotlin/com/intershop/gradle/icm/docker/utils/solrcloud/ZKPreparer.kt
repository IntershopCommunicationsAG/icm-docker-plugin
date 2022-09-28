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
import com.intershop.gradle.icm.docker.utils.Configuration
import com.intershop.gradle.icm.docker.utils.PortMapping
import com.intershop.gradle.icm.docker.utils.solrcloud.SolrPreparer.Companion.GROUP_NAME
import org.gradle.api.Project
import org.gradle.api.provider.Provider

class ZKPreparer(
        project: Project,
        networkTask: Provider<PrepareNetwork>,
) : AbstractTaskPreparer(project, networkTask) {

    companion object {
        const val extName: String = "ZK"
        const val CONTAINER_PORT = 2181
        const val CONTAINER_METRICS_PORT = 7000
        const val CONTAINER_PORTMAPPING = "CONTAINER"
    }

    override fun getExtensionName(): String = extName
    override fun getImage(): Provider<String> = dockerExtension.images.zookeeper

    init {
        initBaseTasks()

        pullTask.configure {
            it.group = GROUP_NAME
        }
        stopTask.configure {
            it.group = GROUP_NAME
        }
        removeTask.configure {
            it.group = GROUP_NAME
        }

        val metricsPortMapping = dockerExtension.developmentConfig.getPortMapping(
                "METRICS",
                Configuration.ZOOKEEPER_METRICS_HOST_PORT,
                Configuration.ZOOKEEPER_METRICS_HOST_PORT_VALUE,
                CONTAINER_METRICS_PORT,
        )

        project.tasks.register("start${getExtensionName()}", StartExtraContainer::class.java) { task ->
            configureContainerTask(task)
            task.group = "icm container solrcloud"
            task.description = "Start Zookeeper component of SolrCloud"

            task.targetImageId(project.provider { pullTask.get().image.get() })
            task.image.set(pullTask.get().image)

            task.withPortMappings(portMapping, metricsPortMapping)

            val zooServers = "${getContainerName()}:2888:3888;${portMapping.containerPort}"
            task.envVars.set(
                    mutableMapOf(
                            "ZOO_MY_ID" to "1",
                            "ZOO_PORT" to portMapping.containerPort.toString(),
                            "ZOO_SERVERS" to "server.1=$zooServers",
                            "ZOO_4LW_COMMANDS_WHITELIST" to "mntr, conf, ruok",
                            "ZOO_CFG_EXTRA" to
                                    "metricsProvider.className=org.apache.zookeeper.metrics.prometheus." +
                                    "PrometheusMetricsProvider metricsProvider.httpPort=" +
                                    "${metricsPortMapping.containerPort} " +
                                    "metricsProvider.exportJvmInfo=true"
                    )
            )

            task.hostConfig.network.set(networkId)
            task.logger.quiet(
                    "The ZK for SolrCloud can be connected with {}:{}",
                    task.containerName.get(),
                    portMapping.containerPort
            )
            task.dependsOn(pullTask, networkTask)
        }
    }

    private val portMapping: PortMapping by lazy {
        dockerExtension.developmentConfig.getPortMapping(
            CONTAINER_PORTMAPPING,
            Configuration.ZOOKEEPER_HOST_PORT,
            Configuration.ZOOKEEPER_HOST_PORT_VALUE,
            CONTAINER_PORT,
            true
        )
    }

    val renderedHostPort : String by lazy {
        "${getContainerName()}:${portMapping.containerPort}"
    }

}
