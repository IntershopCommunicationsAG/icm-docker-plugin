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
import com.intershop.gradle.icm.docker.tasks.utils.ContainerEnvironment
import com.intershop.gradle.icm.docker.utils.AbstractTaskPreparer
import org.gradle.api.Project
import org.gradle.api.provider.Provider

class CustomizationPreparer(project: Project,
                            networkTask: Provider<PrepareNetwork>,
                            private val customizationName: String,
                            private val imagePath: Provider<String>) : AbstractTaskPreparer(project, networkTask) {

    companion object {
        const val EXT_PREFIX: String = "Customization"
    }

    override fun getExtensionName(): String = "${customizationName}$EXT_PREFIX"
    override fun getImage(): Provider<String> = imagePath
    override fun getUseHostUserConfigProperty(): String = getExtensionName()+".useHostUser"
    override fun getAutoRemoveContainerConfigProperty() : String = getExtensionName()+".autoRemoveContainer"

    override fun useHostUserDefaultValue(): Boolean = false

    init {
        initBaseTasks()
        val volumes = mapOf("${dockerExtension.containerPrefix}-customizations" to "/customizations")
        val env = ContainerEnvironment() // empty

        val createTask = registerCreateContainerTask(findTask, volumes, env)
        registerStartContainerTask(createTask).configure { task ->
            task.description = "Starts customization '${customizationName}'"
        }
    }
}
