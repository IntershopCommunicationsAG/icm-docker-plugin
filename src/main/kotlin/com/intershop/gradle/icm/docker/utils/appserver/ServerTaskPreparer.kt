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

package com.intershop.gradle.icm.docker.utils.appserver

import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.tasks.PrepareNetwork
import com.intershop.gradle.icm.docker.tasks.StartExtraContainer
import com.intershop.gradle.icm.docker.tasks.StartServerContainer
import com.intershop.gradle.icm.docker.tasks.utils.ICMContainerEnvironmentBuilder
import com.intershop.gradle.icm.docker.tasks.utils.ICMContainerEnvironmentBuilder.ClasspathLayout.RELEASE
import com.intershop.gradle.icm.docker.tasks.utils.ICMContainerEnvironmentBuilder.ClasspathLayout.SOURCE
import com.intershop.gradle.icm.docker.utils.Configuration
import com.intershop.gradle.icm.docker.utils.HostAndPort
import com.intershop.gradle.icm.docker.utils.solrcloud.StartSolrCloudTask
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType
import java.net.URI

class ServerTaskPreparer(
        project: Project,
        networkTask: Provider<PrepareNetwork>,
        startSolrCloudTask : Provider<StartSolrCloudTask>,
        mailServerTask : Provider<StartExtraContainer>
) : AbstractServerTaskPreparer(project, networkTask, startSolrCloudTask, mailServerTask) {

    companion object {
        const val extName: String = "AS"
    }

    override fun getExtensionName(): String = extName

    init {
        initAppTasks()

        project.tasks.register("start${this.getExtensionName()}", StartServerContainer::class.java) { task ->

            val customization = true
            val taskDescription = "Starts Application Server in a container - only for project use (e.g. solrcloud, responsive)"

            initServer(task, taskDescription, customization)

            task.dependsOn(prepareServer, pullTask, networkTask)
        }
    }

}
