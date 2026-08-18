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

import com.intershop.gradle.icm.docker.extension.DevelopmentConfiguration
import com.intershop.gradle.icm.docker.utils.Configuration
import com.intershop.gradle.icm.docker.utils.IPFinder

enum class SolrConnectionType {
    URL,
    ZOOKEEPER,
}

data class SolrConnection(val value: String, val type: SolrConnectionType, val isDefault: Boolean = false)

object SolrConnectionResolver {

        fun resolve(
            devConfig: DevelopmentConfiguration,
            nodeCount: Int,
            defaultHost: String? = null,
            defaultPort: Int? = null,
        ): SolrConnection {
        val urls = devConfig.getConfigProperty(Configuration.SOLR_CLOUD_SERVER_URLS).trim()
        if (urls.isNotEmpty()) {
            return SolrConnection(urls, SolrConnectionType.URL)
        }

        val zookeeper = devConfig.getConfigProperty(Configuration.SOLR_CLOUD_HOSTLIST).trim()
        if (zookeeper.isNotEmpty()) {
            return SolrConnection(zookeeper, SolrConnectionType.ZOOKEEPER)
        }

        val host = defaultHost ?: IPFinder.getSystemIP().first ?: "localhost"
        return if (nodeCount > 1) {
            SolrConnection(
                "http://$host:${defaultPort ?: devConfig.getIntProperty(
                    Configuration.SOLR_LB_HOST_PORT,
                    Configuration.SOLR_LB_HOST_PORT_VALUE
                )}/solr",
                    SolrConnectionType.URL,
                    true
            )
        } else {
            SolrConnection(
                    "http://$host:${devConfig.getIntProperty(
                            Configuration.SOLR_CLOUD_HOST_PORT,
                            Configuration.SOLR_CLOUD_HOST_PORT_VALUE
                    )}/solr",
                    SolrConnectionType.URL,
                    true
            )
        }
    }
}