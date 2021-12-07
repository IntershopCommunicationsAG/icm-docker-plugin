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
import com.intershop.gradle.icm.docker.tasks.utils.ContainerEnvironment
import com.intershop.gradle.icm.docker.utils.AbstractTaskPreparer
import com.intershop.gradle.icm.docker.utils.Configuration
import com.intershop.gradle.icm.docker.utils.IPFinder
import org.gradle.api.Project
import org.gradle.api.provider.Provider

class SolrPreparer(project: Project,
                   networkTask: Provider<PrepareNetwork>,
                   zkPreparer: ZKPreparer
                  ): AbstractTaskPreparer(project, networkTask) {
    val requiredContainerEnvironment = ContainerEnvironment()

    companion object {
        const val extName: String = "Solr"
        const val CONTAINER_PORT = 8983
    }

    override fun getExtensionName(): String = extName
    override fun getImage(): Provider<String> = extension.images.solr

    init {
        initBaseTasks()

        pullTask.configure {
            it.group = "icm container solrcloud"
        }
        stopTask.configure {
            it.group = "icm container solrcloud"
            it.dependsOn(zkPreparer.stopTask)
        }
        removeTask.configure {
            it.group = "icm container solrcloud"
            it.dependsOn(zkPreparer.removeTask)
        }

        val hostList = zkPreparer.renderedHostList
        requiredContainerEnvironment.add(
                ContainerEnvironment.propertyNameToEnvName(Configuration.SOLR_CLOUD_HOSTLIST),
                hostList
        )

        project.tasks.register("start${getExtensionName()}", StartExtraContainer::class.java) { task ->
            configureContainerTask(task)
            task.group = "icm container solrcloud"
            task.description = "Start Solr component of SolrCloud"

            task.targetImageId(project.provider { pullTask.get().image.get() })
            task.image.set(pullTask.get().image)

            val portMapping = extension.developmentConfig.getPortMapping(
                    "SOLR",
                    CONTAINER_PORT,
                    Configuration.SOLR_CLOUD_HOST_PORT,
                    Configuration.SOLR_CLOUD_HOST_PORT_VALUE,
                    true)
            task.withPortMappings(portMapping)

            task.envVars.set(
                mutableMapOf(
                    "SOLR_PORT" to portMapping.containerPort.toString(),
                    "ZK_HOST" to hostList,
                    "SOLR_HOST" to "${ IPFinder.getSystemIP().first }",
                    "SOLR_OPTS" to "-Dsolr.disableConfigSetsCreateAuthChecks=true"
                )
            )

            task.hostConfig.network.set(networkId)
            task.logger.quiet(
                    "The Solr server can be connected with {}:{}",
                    task.containerName,
                    portMapping.containerPort
            )

            task.dependsOn(pullTask, networkTask, zkPreparer.startTask)
        }

    }

}
