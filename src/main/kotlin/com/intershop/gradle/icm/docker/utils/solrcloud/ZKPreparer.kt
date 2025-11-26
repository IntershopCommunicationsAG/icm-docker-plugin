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
import com.intershop.gradle.icm.docker.tasks.utils.ContainerEnvironment
import com.intershop.gradle.icm.docker.utils.AbstractTaskPreparer
import com.intershop.gradle.icm.docker.utils.Configuration
import com.intershop.gradle.icm.docker.utils.PortMapping
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import java.io.File

class ZKPreparer(
        project: Project,
        networkTask: Provider<PrepareNetwork>,
) : AbstractTaskPreparer(project, networkTask) {

    companion object {
        const val EXT_NAME: String = "ZK"
        const val CONTAINER_PORT = 2181
        const val CONTAINER_METRICS_PORT = 7000
        const val CONTAINER_PORTMAPPING = "CONTAINER"
    }

    override fun getExtensionName(): String = EXT_NAME
    override fun getImage(): Provider<String> = dockerExtension.images.zookeeper
    override fun getUseHostUserConfigProperty(): String = Configuration.ZOOKEEPER_USE_HOST_USER
    override fun getAutoRemoveContainerConfigProperty() : String = Configuration.ZOOKEEPER_AUTOREMOVE_CONTAINER
    override fun getTaskGroupExt(): String = "solrcloud"

    init {
        initBaseTasks()

        val metricsPortMapping = devConfig.getPortMapping(
                "METRICS",
                Configuration.ZOOKEEPER_METRICS_HOST_PORT,
                Configuration.ZOOKEEPER_METRICS_HOST_PORT_VALUE,
                CONTAINER_METRICS_PORT,
        )
        val portMapping = getPortMapping()

        val env = ContainerEnvironment().addAll(
                "ZOO_MY_ID" to "1",
                "ZOO_PORT" to portMapping.containerPort.toString(),
                "ZOO_SERVERS" to "server.1=${getZKServers()}",
                "ZOO_4LW_COMMANDS_WHITELIST" to "mntr, conf, ruok",
                "ZOO_AUTOPURGE_PURGEINTERVAL" to "1",
                "ZOO_CFG_EXTRA" to """
                metricsProvider.className=org.apache.zookeeper.metrics.prometheus.PrometheusMetricsProvider
                metricsProvider.httpPort=${metricsPortMapping.containerPort}
                metricsProvider.exportJvmInfo=true
            """.trimIndent()
        )
        val dataDir: File? = getLocalDataDir()
        val volumes = if (dataDir != null) {
            mapOf("${dataDir.absolutePath}/zk/data" to "/data", "${dataDir.absolutePath}/zk/logs" to "/datalog")
        } else {
            mapOf()
        }

        val createTask = registerCreateContainerTask(findTask, volumes, env)
        createTask.configure { task ->
            task.withPortMappings(portMapping)
        }

        registerStartContainerTask(createTask).configure { task ->
            task.doLast {
                project.logger.quiet(
                        "The ZK for SolrCloud can be connected with {}:{}",
                        getContainerName(),
                        portMapping.containerPort
                )
            }
        }
    }

    private fun getZKServers(): String = "${getContainerName()}:2888:3888;${getPortMapping().containerPort}"
    private fun getPortMapping(): PortMapping =
            devConfig.getPortMapping(
                    CONTAINER_PORTMAPPING,
                    Configuration.ZOOKEEPER_HOST_PORT,
                    Configuration.ZOOKEEPER_HOST_PORT_VALUE,
                    CONTAINER_PORT,
                    true
        )


    fun getRenderedHostPort(): String = "${getContainerName()}:${getPortMapping().containerPort}"

    private fun getLocalDataDir(): File? {
        val dataPath = devConfig.getConfigProperty(Configuration.SOLR_DATA_FOLDER_PATH, "")
        if (dataPath.isBlank()) {
            return null
        }
        return File(dataPath)
    }

}
