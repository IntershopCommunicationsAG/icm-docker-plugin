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

import com.intershop.gradle.icm.docker.ICMDockerPlugin
import com.intershop.gradle.icm.docker.tasks.BuildImage
import com.intershop.gradle.icm.docker.tasks.PrepareNetwork
import com.intershop.gradle.icm.docker.tasks.StartExtraContainer
import com.intershop.gradle.icm.docker.tasks.StartServerContainer
import org.gradle.api.Project
import org.gradle.api.provider.Provider

class IcmServerTaskPreparer(
    project: Project,
    networkTask: Provider<PrepareNetwork>,
    mailServerTask : Provider<StartExtraContainer>
) : AbstractServerTaskPreparer(project, networkTask, null, mailServerTask) {

    companion object {
        const val extName: String = "AsTestContainer"
    }

    override fun getExtensionName(): String = extName

    override fun getImage(): Provider<String> {
        val buildTestImageTask = project.tasks.named(ICMDockerPlugin.BUILD_TEST_IMAGE, BuildImage::class.java)
        val imageProvider = project.provider { buildTestImageTask.get().images.get() }
        return imageProvider.map { it.first() }
    }

    init {
        initAppTasks()

        project.tasks.register("start${this.getExtensionName()}", StartServerContainer::class.java) { task ->

            val customization = false
            val taskDescription = "Starts Production Application Server in a container - only for use in icm-as"

            initServer(task, taskDescription, customization)

            task.dependsOn(pullTask, networkTask)
        }
    }

}
