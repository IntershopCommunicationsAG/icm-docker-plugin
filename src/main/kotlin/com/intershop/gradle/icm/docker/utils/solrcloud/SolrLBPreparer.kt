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
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import java.io.File

/**
 * Prepares a plain nginx container that load balances requests across all configured Solr nodes.
 * Only used when more than one Solr node is configured (see [Configuration.SOLR_NODES_COUNT]).
 */
class SolrLBPreparer(
        project: Project,
        networkTask: Provider<PrepareNetwork>,
        private val solrPreparers: List<SolrPreparer>,
) : AbstractTaskPreparer(project, networkTask) {

    companion object {
        const val EXT_NAME: String = "SolrLB"
        const val CONTAINER_PORT = 80
    }

    override fun getExtensionName(): String = EXT_NAME
    override fun getContainerExt(): String = "solr-lb"
    override fun getImage(): Provider<String> = dockerExtension.images.solrLoadbalancer
    override fun getUseHostUserConfigProperty(): String = Configuration.SOLR_LB_USE_HOST_USER
    override fun getAutoRemoveContainerConfigProperty(): String = Configuration.SOLR_LB_AUTOREMOVE_CONTAINER
    override fun getTaskGroupExt(): String = "solrcloud"

    init {
        initBaseTasks()

        val portMapping = dockerExtension.developmentConfig.getPortMapping(
                "HTTP",
                Configuration.SOLR_LB_HOST_PORT,
                Configuration.SOLR_LB_HOST_PORT_VALUE,
                CONTAINER_PORT,
                true)

        val confDir = project.layout.buildDirectory.dir("solrcloud/solr-lb/conf.d").get().asFile
        val volumes = mapOf(confDir.absolutePath to "/etc/nginx/conf.d")

        val createTask = registerCreateContainerTask(findTask, volumes, ContainerEnvironment())
        createTask.configure { task ->
            task.withPortMappings(portMapping)
            task.doFirst {
                confDir.mkdirs()
                File(confDir, "solr-lb.conf").writeText(renderNginxConf())
            }
        }

        registerStartContainerTask(createTask).configure { task ->
            task.doLast {
                task.logger.quiet(
                        "The Solr load balancer can be connected with {}:{}",
                        getContainerName(),
                        portMapping.containerPort
                )
            }
            task.dependsOn(solrPreparers.map { it.startTask })
        }
    }

    private fun renderNginxConf(): String {
        val upstreamServers = solrPreparers.joinToString(separator = "\n") { "        server ${it.getRenderedHostPort()};" }
        return """
            upstream solr_backend {
        $upstreamServers
            }
            server {
                listen $CONTAINER_PORT;
                location / {
                    proxy_pass http://solr_backend;
                }
            }
        """.trimIndent()
    }

}
