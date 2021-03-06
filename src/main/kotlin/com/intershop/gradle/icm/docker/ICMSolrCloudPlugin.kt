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
package com.intershop.gradle.icm.docker

import com.intershop.gradle.icm.docker.ICMDockerProjectPlugin.Companion.TASK_START_SERVER
import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.tasks.WaitForServer
import com.intershop.gradle.icm.docker.tasks.solrCloud.CleanUpSolr
import com.intershop.gradle.icm.docker.tasks.solrCloud.ListSolr
import com.intershop.gradle.icm.docker.tasks.solrCloud.RebuildSolrSearchIndex
import com.intershop.gradle.icm.docker.utils.Configuration.AS_ADMIN_USER_NAME
import com.intershop.gradle.icm.docker.utils.Configuration.AS_ADMIN_USER_NAME_VALUE
import com.intershop.gradle.icm.docker.utils.Configuration.AS_ADMIN_USER_PASSWORD
import com.intershop.gradle.icm.docker.utils.Configuration.AS_CONNECTOR_CONTAINER_PORT
import com.intershop.gradle.icm.docker.utils.Configuration.AS_CONNECTOR_CONTAINER_PORT_VALUE
import com.intershop.gradle.icm.docker.utils.Configuration.SSL_VERIFICATION
import com.intershop.gradle.icm.docker.utils.Configuration.LOCAL_CONNECTOR_HOST
import com.intershop.gradle.icm.docker.utils.Configuration.LOCAL_CONNECTOR_HOST_VALUE
import com.intershop.gradle.icm.docker.utils.Configuration.SOLR_CLOUD_HOSTLIST
import com.intershop.gradle.icm.docker.utils.Configuration.SOLR_CLOUD_INDEXPREFIX
import com.intershop.gradle.icm.docker.utils.Configuration.WS_HTTPS_PORT
import com.intershop.gradle.icm.docker.utils.Configuration.WS_HTTPS_PORT_VALUE
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.UnknownTaskException


class ICMSolrCloudPlugin : Plugin<Project> {

    /**
     * Main method of a plugin.
     *
     * @param project target project
     */
    override fun apply(project: Project) {
        with(project.rootProject) {
            logger.info("ICM SolrCloud Helper plugin will be initialized")

            val extension = extensions.findByType(
                IntershopDockerExtension::class.java
            ) ?: extensions.create("intershop_docker", IntershopDockerExtension::class.java)

            with(extension.developmentConfig) {
                val host = getConfigProperty(LOCAL_CONNECTOR_HOST, LOCAL_CONNECTOR_HOST_VALUE)
                val websrvport = getConfigProperty(WS_HTTPS_PORT, WS_HTTPS_PORT_VALUE)
                val assrvport = getConfigProperty(AS_CONNECTOR_CONTAINER_PORT, AS_CONNECTOR_CONTAINER_PORT_VALUE)

                val wfsTask = project.tasks.register("waitForServer", WaitForServer::class.java ) { wfs ->
                    wfs.webServerPort.set(websrvport)
                    wfs.appServerPort.set(assrvport)
                    wfs.hostAddress.set(host)
                }

                val solrCleanUp = project.tasks.register("cleanUpSolr", CleanUpSolr::class.java ) { cus ->
                    cus.group = "Solr Cloud Support"
                    cus.description = "Removes all collections and configuration for the specified prefix"

                    cus.solrConfiguration.set(getConfigProperty(SOLR_CLOUD_HOSTLIST))
                    cus.solrClusterPrefixProperty.convention(getConfigProperty(SOLR_CLOUD_INDEXPREFIX, ""))
                }

                val solrList = project.tasks.register("listSolr", ListSolr::class.java ) { lst ->
                    lst.group = "Solr Cloud Support"
                    lst.description = "List all collections and configuration for the specified prefix"

                    lst.solrConfiguration.set(getConfigProperty(SOLR_CLOUD_HOSTLIST))
                    lst.solrClusterPrefixProperty.convention(getConfigProperty(SOLR_CLOUD_INDEXPREFIX))
                }

                val rebuildIndex = project.tasks.register( "rebuildSearchIndex",
                    RebuildSolrSearchIndex::class.java ) { rsi ->
                    rsi.group = "Solr Cloud Support"
                    rsi.description = "Rebuilds the search index for the specified server."

                    rsi.webServerPort.set(websrvport)
                    rsi.hostAddress.set(host)
                    rsi.userName.set(getConfigProperty(AS_ADMIN_USER_NAME, AS_ADMIN_USER_NAME_VALUE))
                    rsi.userPassword.set(getConfigProperty(AS_ADMIN_USER_PASSWORD))

                    rsi.sslVerification.set(
                        getConfigProperty(SSL_VERIFICATION, "false").toLowerCase() == "true")
                }

                rebuildIndex.get().dependsOn(solrCleanUp, wfsTask)
                solrList.get().mustRunAfter(rebuildIndex, solrCleanUp)

                try {
                    val startServer = tasks.named(TASK_START_SERVER)
                    wfsTask.get().mustRunAfter(startServer)
                } catch (ex: UnknownTaskException) {
                    project.logger.info("No startServer task found.")
                }
            }
        }
    }
}
