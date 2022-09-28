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

import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.tasks.PrepareNetwork
import com.intershop.gradle.icm.docker.tasks.StartExtraContainer
import com.intershop.gradle.icm.docker.utils.appsrv.TestTaskPreparer
import com.intershop.gradle.icm.docker.utils.network.TaskPreparer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.UnknownTaskException

class ICMTestDockerPlugin: Plugin<Project> {

    /**
     * Main method of a plugin.
     *
     * @param project target project
     */
    override fun apply(project: Project) {
        with(project.rootProject) {
            logger.info("ICM Test Docker plugin will be initialized")
            extensions.findByType(
                IntershopDockerExtension::class.java
            ) ?: extensions.create("intershop_docker", IntershopDockerExtension::class.java)

            plugins.apply(ICMDockerPlugin::class.java)

            val createNetworkTask = project.tasks.named(TaskPreparer.PREPARE_NETWORK, PrepareNetwork::class.java)

            val mailSrvTask = tasks.named(
                "start${com.intershop.gradle.icm.docker.utils.mail.TaskPreparer.extName}",
                StartExtraContainer::class.java)


            val appSrvPreparer = TestTaskPreparer(project, createNetworkTask)

            appSrvPreparer.getICMServerTaskPreparer().startTask.configure {
                it.dependsOn(mailSrvTask)
            }

            try {
                tasks.named("containerClean").configure {
                    it.dependsOn( appSrvPreparer.getICMServerTaskPreparer().removeTask)
                }
            } catch (ex: UnknownTaskException) {
                logger.info("Task containerClean is not available.")
            }

        }
    }
}
