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

import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.utils.Configuration
import com.intershop.gradle.icm.docker.utils.solrcloud.SolrConnectionResolver
import org.apache.solr.client.solrj.SolrClient
import org.apache.solr.client.solrj.impl.CloudHttp2SolrClient
import org.apache.solr.client.solrj.impl.Http2SolrClient
import org.gradle.api.DefaultTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.options.Option
import org.gradle.kotlin.dsl.getByType
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Inject

abstract class AbstractSolrAdminTask @Inject constructor(objectFactory: ObjectFactory) : DefaultTask() {

    @get:Input
    val solrConfiguration: Property<String> = objectFactory.property(String::class.java)

    @Internal
    val solrClusterPrefixProperty: Property<String> = objectFactory.property(String::class.java)

    @set:Option(
        option = "solrPrefix",
        description = "Specifies the special SolrCluster Prefix. See 'solr.clusterIndexPrefix'"
    )
    @get:Input
    var solrClusterPrefix: String
        get() = solrClusterPrefixProperty.get()
        set(value) = solrClusterPrefixProperty.set(value)

    @get:Input
    val connectionTimeout: Property<Int> = objectFactory.property(Int::class.java)

    init {
        connectionTimeout.convention(10000)
    }

    @Internal
    protected fun getSolrClient(): SolrClient {
        return if (solrConfiguration.isPresent && solrConfiguration.get().isNotEmpty()) {
            logger.quiet("\nUsing '${solrConfiguration.get()}' to connect to Solr.\n")
            getClient(solrConfiguration.get())
        } else {
            val dockerExtension = project.extensions.getByType<IntershopDockerExtension>()
            val devConfig = dockerExtension.developmentConfig
            val nodeCount = devConfig.getIntProperty(
                    Configuration.SOLR_NODES_COUNT,
                    Configuration.SOLR_NODES_COUNT_VALUE
            )
            val defaultConnection = SolrConnectionResolver.resolve(devConfig, nodeCount)
            logger.quiet("\nUse default Solr connection '${defaultConnection.value}' for the client.\n")
            getClient(defaultConnection.value)
        }

    }

    private fun getClient(connectStr: String): SolrClient {
        val values = connectStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (values.isEmpty()) {
            throw IllegalArgumentException("Solr connection configuration must not be empty")
        }

        if (values.first().startsWith("http://") || values.first().startsWith("https://")) {
            if (values.any { !it.startsWith("http://") && !it.startsWith("https://") }) {
                throw IllegalArgumentException("Solr URL configuration must contain only HTTP(S) URLs: '$connectStr'")
            }
            values.forEach { URI.create(it) }
                return CloudHttp2SolrClient.Builder(values)
                    .withHttpClientBuilder(Http2SolrClient.Builder().useHttp1_1(true))
                    .build()
        }

        if (values.any { it.startsWith("http://") || it.startsWith("https://") }) {
            throw IllegalArgumentException("Solr connection configuration cannot mix URLs and ZooKeeper hosts: '$connectStr'")
        }

        val separator = connectStr.indexOf('/')
        val zkHosts = if (separator >= 0) connectStr.substring(0, separator) else connectStr
        val path = if (separator >= 0 && separator < connectStr.length - 1) {
            java.util.Optional.of("/${connectStr.substring(separator + 1).trim('/')}")
        } else {
            java.util.Optional.empty()
        }

        return CloudHttp2SolrClient.Builder(zkHosts.split(',', ';'), path)
            .withHttpClientBuilder(Http2SolrClient.Builder().useHttp1_1(true))
                .withZkConnectTimeout(connectionTimeout.get(), TimeUnit.MILLISECONDS)
                .build()
    }
}
