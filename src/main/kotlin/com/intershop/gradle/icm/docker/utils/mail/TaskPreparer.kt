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
package com.intershop.gradle.icm.docker.utils.mail

import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.tasks.PrepareNetwork
import com.intershop.gradle.icm.docker.tasks.StartExtraContainer
import com.intershop.gradle.icm.docker.utils.AbstractTaskPreparer
import com.intershop.gradle.icm.docker.utils.Configuration
import com.intershop.gradle.icm.docker.utils.ContainerUtils
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

class TaskPreparer(project: Project,
                   networkTask: Provider<PrepareNetwork>) : AbstractTaskPreparer(project, networkTask) {

    companion object {
        const val extName: String = "MailSrv"
        const val SMTP_CONTAINER_PORT = 1025
        const val ADMIN_CONTAINER_PORT = 8025
    }

    override fun getExtensionName(): String = extName
    override fun getImage(): Provider<String> = dockerExtension.images.mailsrv

    init {
        initBaseTasks()

        project.tasks.register ("start${getExtensionName()}", StartExtraContainer::class.java) { task ->
            configureContainerTask(task)
            task.description = "Starts an local mail server for testing"

            task.targetImageId( project.provider { pullTask.get().image.get() } )
            task.image.set(pullTask.get().image)

            task.envVars.set(mutableMapOf(
                "MH_STORAGE" to "maildir",
                "MH_MAILDIR_PATH" to "/maildir"))

            val devConfig = project.extensions.getByType<IntershopDockerExtension>().developmentConfig
            val smtpPortMapping = devConfig.getPortMapping(
                    "SMTP",
                    Configuration.MAIL_SMTP_HOST_PORT,
                    Configuration.MAIL_SMTP_HOST_PORT_VALUE,
                    SMTP_CONTAINER_PORT,
                    true
            )
            val adminPortMapping = devConfig.getPortMapping(
                    "ADMIN",
                    Configuration.MAIL_ADMIN_HOST_PORT,
                    Configuration.MAIL_ADMIN_HOST_PORT_VALUE,
                    ADMIN_CONTAINER_PORT,
                    false
            )
            task.withPortMappings(smtpPortMapping, adminPortMapping)
            task.withSocketProbe(
                    smtpPortMapping.hostPort,
                    devConfig.getDurationProperty(
                            Configuration.MAIL_READINESS_PROBE_INTERVAL,
                            Configuration.MAIL_READINESS_PROBE_INTERVAL_VALUE
                    ),
                    devConfig.getDurationProperty(
                            Configuration.MAIL_READINESS_PROBE_TIMEOUT,
                            Configuration.MAIL_READINESS_PROBE_TIMEOUT_VALUE
                    )
            )

            task.hostConfig.binds.set( project.provider {
                ContainerUtils.transformVolumes(
                    mutableMapOf(
                        project.layout.buildDirectory.dir("mailoutput").get().asFile.absolutePath
                                to "/maildir"
                    )
                )
            })

            task.hostConfig.network.set(networkId)

            task.dependsOn(pullTask, networkTask)
        }
    }


}
