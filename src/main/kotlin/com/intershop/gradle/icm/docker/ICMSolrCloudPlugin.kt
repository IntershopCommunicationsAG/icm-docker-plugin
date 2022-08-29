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

import com.intershop.gradle.icm.docker.ICMDockerProjectPlugin.Companion.TASK_DBPREPARE
import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.tasks.StartExtraContainer
import com.intershop.gradle.icm.docker.tasks.StartServerContainer
import com.intershop.gradle.icm.docker.tasks.WaitForServer
import com.intershop.gradle.icm.docker.tasks.solrCloud.CleanUpSolr
import com.intershop.gradle.icm.docker.tasks.solrCloud.ListSolr
import com.intershop.gradle.icm.docker.tasks.solrCloud.RebuildSolrSearchIndex
import com.intershop.gradle.icm.docker.utils.Configuration
import com.intershop.gradle.icm.docker.utils.Configuration.AS_ADMIN_USER_NAME
import com.intershop.gradle.icm.docker.utils.Configuration.AS_ADMIN_USER_NAME_VALUE
import com.intershop.gradle.icm.docker.utils.Configuration.AS_ADMIN_USER_PASSWORD
import com.intershop.gradle.icm.docker.utils.Configuration.SOLR_CLOUD_HOSTLIST
import com.intershop.gradle.icm.docker.utils.Configuration.SOLR_CLOUD_INDEXPREFIX
import com.intershop.gradle.icm.docker.utils.Configuration.SSL_VERIFICATION
import com.intershop.gradle.icm.docker.utils.appserver.CustomServerTaskPreparer
import com.intershop.gradle.icm.docker.utils.webserver.WATaskPreparer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.UnknownTaskException
import java.net.URI

class ICMSolrCloudPlugin : Plugin<Project> {

    companion object {
        const val SOLR_GROUP = "Solr Cloud Support"
    }

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

            try {
                val startWAProvider = tasks.named(
                        "start${WATaskPreparer.extName}",
                        StartExtraContainer::class.java
                )
                val startASProvider = tasks.named(
                        "start${CustomServerTaskPreparer.extName}",
                        StartServerContainer::class.java
                )

                val startSolrCloud = tasks.named("startSolrCloud")
                val dbPrepareTask = tasks.named(TASK_DBPREPARE)

                with(extension.developmentConfig) {

                    val wfsTask = project.tasks.register("waitForServer", WaitForServer::class.java) { wfs ->
                        wfs.probes.addAll(provider { startWAProvider.get().probes.get() })
                        wfs.probes.addAll(provider { startASProvider.get().probes.get() })

                        wfs.mustRunAfter(startWAProvider, startASProvider)
                    }

                    val solrCloudHostList = getConfigProperty(SOLR_CLOUD_HOSTLIST, "localhost")
                    val solrCloudIndexPrefix = getConfigProperty(SOLR_CLOUD_INDEXPREFIX, "")
                    val solrCleanUp = project.tasks.register("cleanUpSolr", CleanUpSolr::class.java) { cus ->
                        cus.group = SOLR_GROUP
                        cus.description = "Removes all collections and configuration for the specified prefix"

                        cus.solrConfiguration.set(solrCloudHostList)
                        cus.solrClusterPrefixProperty.convention(solrCloudIndexPrefix)
                        cus.mustRunAfter(startSolrCloud, wfsTask)
                    }

                    val rebuildIndex = project.tasks.register("rebuildSearchIndex",
                            RebuildSolrSearchIndex::class.java) { rsi ->
                        rsi.group = SOLR_GROUP
                        rsi.description = "Rebuilds the search index for the specified server."

                        rsi.webServerUri.set(URI.create(getConfigProperty(
                                Configuration.WS_SECURE_URL,
                                Configuration.WS_SECURE_URL_VALUE
                        )))
                        rsi.userName.set(getConfigProperty(AS_ADMIN_USER_NAME, AS_ADMIN_USER_NAME_VALUE))
                        rsi.userPassword.set(getConfigProperty(AS_ADMIN_USER_PASSWORD))

                        rsi.sslVerification.set(
                                getConfigProperty(SSL_VERIFICATION, "false").lowercase() == "true"
                        )

                        rsi.dependsOn(solrCleanUp, wfsTask)
                        rsi.mustRunAfter(dbPrepareTask)
                    }

                    project.tasks.register("listSolr", ListSolr::class.java) { lst ->
                        lst.group = "Solr Cloud Support"
                        lst.description = "List all collections and configuration for the specified prefix"

                        lst.solrConfiguration.set(solrCloudHostList)
                        lst.solrClusterPrefixProperty.convention(solrCloudIndexPrefix)

                        lst.mustRunAfter(rebuildIndex, solrCleanUp)
                    }
                }
            } catch (ex: UnknownTaskException) {
                project.logger.info("No startServer task found.")
            }
        }
    }
}
