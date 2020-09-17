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
package com.intershop.gradle.icm.docker.tasks.solrCloud

import org.apache.solr.client.solrj.request.CollectionAdminRequest
import org.apache.solr.client.solrj.request.ConfigSetAdminRequest
import org.apache.solr.client.solrj.response.ConfigSetAdminResponse
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject


open class ListSolrTask @Inject constructor(objectFactory: ObjectFactory) : AbstractSolrAdminTask(objectFactory) {

    @TaskAction
    fun listSolrCollectionConfig() {
        val solrClient = getSolrClient()
        val collectionsList = CollectionAdminRequest.List.listCollections(solrClient)
        collectionsList.forEach { col ->
            if (col.startsWith(solrClusterPrefixProperty.get(), true)) {
                project.logger.quiet("Collection {} found for {}",
                    col, solrClusterPrefixProperty.get())
            }
        }

        val request: ConfigSetAdminRequest.List  = ConfigSetAdminRequest.List()
        val response: ConfigSetAdminResponse.List  = request.process(solrClient)
        val actualConfigSets = response.configSets

        actualConfigSets.forEach { conf ->
            if (conf.startsWith(solrClusterPrefixProperty.get(), true)) {
                project.logger.quiet("Configuration set {} found for {}",
                    conf, solrClusterPrefixProperty.get())
            }
        }

        solrClient.close()
    }
}
