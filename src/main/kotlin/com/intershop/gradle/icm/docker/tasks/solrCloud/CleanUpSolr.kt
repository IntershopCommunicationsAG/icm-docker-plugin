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
import org.gradle.api.GradleException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

open class CleanUpSolr @Inject constructor(objectFactory: ObjectFactory) : AbstractSolrAdminTask(objectFactory) {

    @TaskAction
    fun removeSolrCollectionConfig() {
        val solrClient = getSolrClient()
        val collectionsList = CollectionAdminRequest.List.listCollections(solrClient)
        var collections = 0

        collectionsList.forEach { col ->
            if (col.startsWith(solrClusterPrefixProperty.get(), true)) {
                project.logger.info("Remove collection {} found for {}", col, solrClusterPrefixProperty.get())

                val deleteColl = CollectionAdminRequest.Delete.deleteCollection(col)
                val deleteCollResponse = deleteColl.process(solrClient)
                if (!deleteCollResponse.isSuccess) {
                    throw GradleException("It was not possible to drop all collections for " +
                            solrClusterPrefixProperty.get()
                    )
                }

                ++collections
            }
        }

        val request: ConfigSetAdminRequest.List  = ConfigSetAdminRequest.List()
        val response: ConfigSetAdminResponse.List  = request.process(solrClient)
        val actualConfigSets = response.configSets
        var configs = 0

        actualConfigSets.forEach { conf ->
            if (conf.startsWith(solrClusterPrefixProperty.get(), true)) {
                project.logger.info("Remove configuration set {} found for {}", conf, solrClusterPrefixProperty.get())

                val deleteConfRequest = ConfigSetAdminRequest.Delete()
                deleteConfRequest.configSetName = conf
                val deleteConfResponse = deleteConfRequest.process(solrClient)
                if(deleteConfResponse.errorMessages != null && deleteConfResponse.errorMessages.size() > 0) {
                    deleteConfResponse.errorMessages.forEach { err ->
                        project.logger.error("${err.key}:${err.value}")
                    }
                    throw GradleException("It was not possible to drop all configsets " +
                            "for ${solrClusterPrefixProperty.get()}")
                }

                ++configs
            }
        }

        solrClient.close()

        project.logger.quiet("{} collections and {} coniguration sets for {} deleted.",
            collections, configs, solrClusterPrefixProperty.get())
    }
}
