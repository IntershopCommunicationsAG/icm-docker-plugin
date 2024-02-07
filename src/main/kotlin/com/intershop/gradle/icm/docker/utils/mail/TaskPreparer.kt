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

import com.intershop.gradle.icm.docker.tasks.PrepareNetwork
import com.intershop.gradle.icm.docker.tasks.StartMailServerContainer
import com.intershop.gradle.icm.docker.tasks.utils.ContainerEnvironment
import com.intershop.gradle.icm.docker.utils.AbstractTaskPreparer
import com.intershop.gradle.icm.docker.utils.Configuration
import org.gradle.api.Project
import org.gradle.api.provider.Provider


class TaskPreparer(project: Project,
                   networkTask: Provider<PrepareNetwork>) : AbstractTaskPreparer(project, networkTask) {

    companion object {
        const val EXT_NAME: String = "MailSrv"
        const val SMTP_CONTAINER_PORT = 1025
        const val ADMIN_CONTAINER_PORT = 8025
    }

    override fun getExtensionName(): String = EXT_NAME
    override fun getImage(): Provider<String> = dockerExtension.images.mailsrv
    override fun getUseHostUserConfigProperty(): String = Configuration.MAIL_USE_HOST_USER

    init {
        initBaseTasks()

        val mailOutputDir = project.layout.buildDirectory.dir("mailoutput").get().asFile

        val volumes = mapOf(mailOutputDir.absolutePath to "/maildir")
        val env = ContainerEnvironment().addAll("MH_STORAGE" to "maildir", "MH_MAILDIR_PATH" to "/maildir")
        val devConfig = dockerExtension.developmentConfig
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

        val createTask = registerCreateContainerTask(findTask, volumes, env)
        createTask.configure { task ->
            task.withPortMappings(smtpPortMapping, adminPortMapping)
        }

        registerStartContainerTask(createTask, StartMailServerContainer::class.java).configure { task ->
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
            task.withPrimaryPortMapping(createTask.get().getPrimaryPortMapping())
        }
    }
}
