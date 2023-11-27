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

import com.intershop.gradle.icm.docker.utils.IPFinder
import org.apache.solr.client.solrj.SolrClient
import org.apache.solr.client.solrj.impl.CloudSolrClient
import org.gradle.api.DefaultTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.options.Option
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
            getClient(solrConfiguration.get())
        } else {
            val defaultConStr = "${IPFinder.getSystemIP().first}:2181"
            project.logger.quiet("\n!!! Use default connect string '${defaultConStr}' for the client! \n")
            getClient(defaultConStr)
        }

    }

    private fun getClient(connectStr: String): CloudSolrClient {
        val pathList = connectStr.split("/")

        val path = if (pathList.size > 1) {
            java.util.Optional.of("/${pathList[1].trim()}")
        } else {
            java.util.Optional.empty()
        }
        val zkHosts = pathList[0].split(";")

        return CloudSolrClient.Builder(zkHosts, path)
            .withZkConnectTimeout(connectionTimeout.get(), TimeUnit.MILLISECONDS)
            .build()
    }
}
