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
import com.intershop.gradle.icm.docker.utils.IPFinder
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import java.io.File

class SolrPreparer(
        project: Project,
        networkTask: Provider<PrepareNetwork>,
        zkPreparer: ZKPreparer,
        private val nodeNr: Int = 1,
) : AbstractTaskPreparer(project, networkTask) {

    companion object {
        const val EXT_NAME: String = "Solr"
        const val GROUP_NAME = "icm container solrcloud"
    }

    // node 1 keeps the original naming/config keys for backward compatibility
    override fun getExtensionName(): String = if (nodeNr == 1) EXT_NAME else "$EXT_NAME$nodeNr"
    override fun getImage(): Provider<String> = dockerExtension.images.solr
    override fun getUseHostUserConfigProperty(): String = Configuration.SOLR_USE_HOST_USER
    override fun getAutoRemoveContainerConfigProperty() : String = Configuration.SOLR_AUTOREMOVE_CONTAINER
    override fun getTaskGroupExt(): String = "solrcloud"

    // host port and container port must be identical: Solr registers itself in ZooKeeper with
    // "SOLR_HOST:SOLR_PORT" and that address has to be reachable both from other containers and
    // from the host (e.g. by CleanUpSolr), so no docker port remapping is allowed here
    private val portMapping = dockerExtension.developmentConfig.getPortMapping(
            "SOLR",
            getHostPortConfigProperty(),
            Configuration.SOLR_CLOUD_HOST_PORT_VALUE + (nodeNr - 1),
            Configuration.SOLR_CLOUD_HOST_PORT_VALUE + (nodeNr - 1),
            true)

    init {
        initBaseTasks()
        val hostIP = "${IPFinder.getSystemIP().first}"
        val env = ContainerEnvironment().addAll(
                // Solr 9 image reads SOLR_PORT/SOLR_HOST, Solr 10 renamed them to
                // SOLR_PORT_LISTEN/SOLR_HOST_ADVERTISE (SOLR-15442) - set both so the node
                // advertises this reachable address in ZooKeeper instead of falling back to
                // its internal docker network IP
                "SOLR_PORT" to portMapping.containerPort.toString(),
                "SOLR_PORT_LISTEN" to portMapping.containerPort.toString(),
                "ZK_HOST" to zkPreparer.getRenderedHostPort(),
                "SOLR_HOST" to hostIP,
                "SOLR_HOST_ADVERTISE" to hostIP,
                "SOLR_SECURITY_MANAGER_ENABLED" to "false",
                "SOLR_OPTS" to "-Dsolr.disableConfigSetsCreateAuthChecks=true"
        )
        val dataDir: File? = getLocalDataDir()
        val volumes = if (dataDir != null) {
            mapOf(dataDir.absolutePath to "/var/solr")
        } else {
            mapOf()
        }

        val createTask = registerCreateContainerTask(findTask, volumes, env)
        createTask.configure { task ->
            task.withPortMappings(portMapping)
        }

        registerStartContainerTask(createTask).configure { task ->
            task.doLast {
                task.logger.quiet(
                        "The Solr server can be connected with http://{}:{}/solr",
                        getContainerName(),
                        portMapping.containerPort
                )
            }
            task.dependsOn(zkPreparer.startTask)
        }

    }

    // node 1 keeps using the original "solr.port" property, additional nodes get their own port property
    private fun getHostPortConfigProperty(): String =
            if (nodeNr == 1) Configuration.SOLR_CLOUD_HOST_PORT else "${Configuration.SOLR_CLOUD_HOST_PORT}.$nodeNr"

    // address used by other containers on the same docker network to reach this node
    fun getRenderedHostPort(): String = "${getContainerName()}:${portMapping.containerPort}"

    private fun getLocalDataDir(): File? {
        val dataPath = devConfig.getConfigProperty(Configuration.SOLR_DATA_FOLDER_PATH, "")
        if (dataPath.isBlank()) {
            return null
        }
        // node 1 keeps using the original data folder for backward compatibility
        return if (nodeNr == 1) File(dataPath) else File(dataPath, "node$nodeNr")
    }

}
