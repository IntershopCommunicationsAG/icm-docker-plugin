/*
 * Copyright 2022 Intershop Communications AG.
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
package com.intershop.gradle.icm.docker.utils.appsrv

import com.intershop.gradle.icm.docker.tasks.PrepareNetwork
import com.intershop.gradle.icm.docker.tasks.StartServerContainer
import com.intershop.gradle.icm.docker.utils.Configuration
import org.gradle.api.Project
import org.gradle.api.provider.Provider

class WaitingAsTaskPreparer(
        project: Project,
        networkTask: Provider<PrepareNetwork>,
) : AbstractASTaskPreparer(project, networkTask) {

    companion object {
        const val EXT_NAME: String = "WaitingAs"
    }

    override fun getImage(): Provider<String> {
        if(dockerExtension.developmentConfig.getConfigProperty(
                Configuration.AS_USE_TESTIMAGE,
                Configuration.AS_USE_TESTIMAGE_VALUE
            ).toBoolean()) {
            return icmExtension.projectConfig.base.testImage
        }
        return icmExtension.projectConfig.base.image
    }

    override fun getExtensionName(): String = EXT_NAME

    init {
        val volumes = project.provider { getServerVolumes() }

        val createTask = registerCreateASContainerTask(findTask, volumes)
        createTask.configure { task ->
            task.withPortMappings(*getPortMappings().toTypedArray())
            task.forCustomization(dockerExtension.containerPrefix)
            task.entrypoint.set(listOf("/intershop/bin/startAndWait.sh"))
        }

        registerStartContainerTask(createTask, StartServerContainer::class.java).configure { task ->
            task.description = """
                Starts a container without any special command (wait/sleep)
                ATTENTION: task ${
                taskNameOf("create")
            } is executed in preparation of this task and supports parameters that you may expect to get supported by this task.
            """.trimIndent()
        }


        /* FIXME SKR  project.tasks.register("start${this.getExtensionName()}", StartServerContainer::class.java) { task ->
          configureContainerTask(task)

            task.description = "Start container without any special command (sleep)"

            task.targetImageId(project.provider { pullTask.get().image.get() })
            task.image.set(pullTask.get().image)

            task.entrypoint.set(listOf("/intershop/bin/startAndWait.sh"))

            task.hostConfig.binds.set(project.provider {
                getServerVolumes(task, true).apply {
                    project.logger.info("Using the following volume binds for container startup in task {}: {}",
                        task.name, this)
                }
            })
            task.withPortMappings(*getPortMappings().toTypedArray())
            task.hostConfig.network.set(networkId)

            val devConfig = project.extensions.getByType<IntershopDockerExtension>().developmentConfig

            val solrCloudHostList = devConfig.getConfigProperty(Configuration.SOLR_CLOUD_HOSTLIST)
            val solrCloudIndexPrefix = devConfig.getConfigProperty(Configuration.SOLR_CLOUD_INDEXPREFIX)

            val mailPort = devConfig.getConfigProperty(Configuration.MAIL_SMTP_PORT)
            val mailHost = devConfig.getConfigProperty(Configuration.MAIL_SMTP_HOST)

            if(zkTaskProvider != null && solrCloudHostList.isEmpty()) {
                task.solrCloudZookeeperHostList = project.provider {
                    val containerPort = zkTaskProvider!!.get().getPortMappings().stream()
                        .filter { it.name == ZKPreparer.CONTAINER_PORTMAPPING }
                        .findFirst().get().containerPort
                    "${zkTaskProvider!!.get().containerName.get()}:${containerPort}"
                }
            } else if (solrCloudHostList.isNotEmpty()){
                task.solrCloudZookeeperHostList = project.provider {
                    solrCloudHostList
                }

                if(solrCloudIndexPrefix.isNotEmpty()) {
                    task.withEnvironment(
                        ICMContainerEnvironmentBuilder().
                    withAdditionalEnvironment("SOLR_CLUSTERINDEXPREFIX", solrCloudIndexPrefix).build())
                }
            }

            if(mailServerTaskProvider != null && mailPort.isEmpty() && mailHost.isEmpty()) {
                task.mailServer = project.provider {
                    HostAndPort(
                        mailServerTaskProvider!!.get().containerName.get(),
                        mailServerTaskProvider!!.get().getPrimaryPortMapping().get().containerPort
                    )
                }
            } else if (mailPort.isNotEmpty() && mailHost.isNotEmpty()){
                task.mailServer = project.provider {
                    HostAndPort(mailHost, mailPort.toInt())
                }
            }

        }*/
    }

}
