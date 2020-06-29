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

package com.intershop.gradle.icm.docker.utils

import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.tasks.APullImage
import com.intershop.gradle.icm.docker.tasks.StartExtraContainerTask
import org.gradle.api.Project

class SolrCloudPreparer(val project: Project,
                        private val dockerExtension: IntershopDockerExtension) {

    companion object {
        const val TASK_PULL_ZK = "pullZK"
        const val TASK_PULL_SOLR = "pullSolr"
        const val TASK_START_ZK = "startZK"
        const val TASK_START_SOLR = "startSolr"
        const val TASK_STOP_ZK = "stopZK"
        const val TASK_STOP_SOLR = "stopSolr"
        const val TASK_REMOVE_ZK = "removeZK"
        const val TASK_REMOVE_SOLR = "removeSolr"

        const val CONTAINER_EXTENSION_ZK = "zk"
        const val CONTAINER_EXTENSION_SOLR = "solr"
    }

    fun getZKStartTask(image: APullImage): StartExtraContainerTask {
        return with(project) {
            tasks.maybeCreate(
                    TASK_START_ZK,
                    StartExtraContainerTask::class.java).apply {
                dependsOn(image)

                group = "icm docker project"
                attachStderr.set(true)
                attachStdout.set(true)

                targetImageId(image.image)

                containerName.set("${project.name.toLowerCase()}-${CONTAINER_EXTENSION_ZK}")

                hostConfig.portBindings.set(
                        listOf("2181:2188"))
                hostConfig.autoRemove.set(true)

                envVars.set(mutableMapOf(
                            "ZOO_MY_ID" to "1",
                            "ZOO_PORT" to "2181" ,
                            "ZOO_SERVERS" to "server.1=zoo-1:2888:3888"))
            }
        }
    }

    fun getSolrStartTask(image: APullImage): StartExtraContainerTask {
        return with(project) {
            tasks.maybeCreate(
                    TASK_START_SOLR,
                    StartExtraContainerTask::class.java).apply {
                dependsOn(image)

                group = "icm docker project"
                attachStderr.set(true)
                attachStdout.set(true)

                targetImageId(image.image)

                containerName.set("${project.name.toLowerCase()}-${CONTAINER_EXTENSION_SOLR}")

                hostConfig.portBindings.set(
                        listOf("8983:8983"))
                hostConfig.autoRemove.set(true)

                envVars.set(mutableMapOf(
                        "SOLR_PORT" to "8983",
                        "ZK_HOST" to "${project.name.toLowerCase()}-${CONTAINER_EXTENSION_ZK}:2181" ,
                        "SOLR_HOST" to "${project.name.toLowerCase()}-${CONTAINER_EXTENSION_SOLR}"))
            }
        }
    }
}
