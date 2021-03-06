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
import com.intershop.gradle.icm.docker.utils.AbstractTaskPreparer
import com.intershop.gradle.icm.docker.utils.IPFinder
import org.gradle.api.Project
import org.gradle.api.provider.Provider

class SolrPreparer(project: Project,
                   networkTask: Provider<PrepareNetwork>): AbstractTaskPreparer(project, networkTask) {

    companion object {
        const val extName: String = "Solr"
    }

    override val image: Provider<String> = extension.images.solr
    override val extensionName: String = extName
    override val containerExt: String = extensionName.toLowerCase()

    init {
        initBaseTasks()

        pullTask.configure {
            it.group = "icm container solrcloud"
        }
        stopTask.configure {
            it.group = "icm container solrcloud"
        }
        removeTask.configure {
            it.group = "icm container solrcloud"
        }

        project.tasks.register("start${extensionName}", StartExtraContainer::class.java) { task ->
            configureContainerTask(task)
            task.group = "icm container solrcloud"
            task.description = "Start Solr component of SolrCloud"

            task.targetImageId(project.provider { pullTask.get().image.get() })
            task.image.set(pullTask.get().image)

            task.hostConfig.portBindings.set(
                listOf("8983:8983")
            )

            task.envVars.set(
                mutableMapOf(
                    "SOLR_PORT" to "8983",
                    "ZK_HOST" to "${extension.containerPrefix}-${ZKPreparer.extName.toLowerCase()}:2181",
                    "SOLR_HOST" to "${ IPFinder.getSystemIP()}",
                    "SOLR_OPTS" to "-Dsolr.disableConfigSetsCreateAuthChecks=true"
                )
            )

            task.hostConfig.network.set(networkId)

            task.dependsOn(pullTask, networkTask)
        }
    }
}
