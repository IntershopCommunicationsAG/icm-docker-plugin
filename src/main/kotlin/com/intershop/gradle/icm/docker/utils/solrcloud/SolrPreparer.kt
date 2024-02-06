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
) : AbstractTaskPreparer(project, networkTask) {

    companion object {
        const val EXT_NAME: String = "Solr"
        const val CONTAINER_PORT = 8983
        const val GROUP_NAME = "icm container solrcloud"
    }

    override fun getExtensionName(): String = EXT_NAME
    override fun getImage(): Provider<String> = dockerExtension.images.solr
    override fun getUseHostUserConfigProperty(): String = Configuration.SOLR_USE_HOST_USER
    override fun getTaskGroupExt(): String = "solrcloud"

    init {
        initBaseTasks()
        val portMapping = dockerExtension.developmentConfig.getPortMapping(
                "SOLR",
                Configuration.SOLR_CLOUD_HOST_PORT,
                Configuration.SOLR_CLOUD_HOST_PORT_VALUE,
                CONTAINER_PORT,
                true)
        val env = ContainerEnvironment().addAll(
                "SOLR_PORT" to portMapping.containerPort.toString(),
                "ZK_HOST" to zkPreparer.getRenderedHostPort(),
                "SOLR_HOST" to "${IPFinder.getSystemIP().first}",
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
                        "The Solr server can be connected with {}:{}",
                        getContainerName(),
                        portMapping.containerPort
                )
            }
            task.dependsOn(zkPreparer.startTask)
        }

    }

    private fun getLocalDataDir(): File? {
        val dataPath = devConfig.getConfigProperty(Configuration.SOLR_DATA_FOLDER_PATH, "")
        if (dataPath.isBlank()) {
            return null
        }
        return File(dataPath)
    }

}
