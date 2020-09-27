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
import com.intershop.gradle.icm.docker.tasks.StartExtraContainerTask
import com.intershop.gradle.icm.docker.utils.AbstractTaskPreparer
import com.intershop.gradle.icm.docker.utils.ContainerUtils
import org.gradle.api.Project
import org.gradle.api.provider.Provider

class TestTaskPreparer(project: Project,
                       networkTask: Provider<PrepareNetwork>) : AbstractTaskPreparer(project, networkTask) {

    companion object {
        const val extName: String = "TestMailSrv"
    }

    override val image: Provider<String> = extension.images.testmailsrv
    override val extensionName: String = extName
    override val containerExt: String = extensionName.toLowerCase()

    init {
        initBaseTasks()

        project.tasks.register ("start${extensionName}", StartExtraContainerTask::class.java) { task ->
            configureContainerTask(task)
            task.description = "Starts an special test mail server for ISHUnitTests"

            task.targetImageId( project.provider { pullTask.get().image.get() } )
            task.image.set(pullTask.get().image)

            task.entrypoint.set(listOf(
                "bash", "-c",
                "mkdir -p /data/test-mails; ln -sf /data/test-mails /var/tmp; java -jar " +
                        "-Xms64M -Xmx128M -Djava.security.egd=file:/dev/./urandom /app.jar " +
                        "--iste-mail.smtp-port=25 --spring.output.ansi.enabled=NEVER"
            ))

            task.hostConfig.portBindings.set(listOf("25:25"))
            task.hostConfig.binds.set( project.provider {
                ContainerUtils.transformVolumes(
                    mutableMapOf(
                        project.layout.buildDirectory.dir("testMails").get().asFile.absolutePath
                                to "/data/test-mails"
                    )
                )
            })

            task.hostConfig.network.set(networkId)

            task.dependsOn(pullTask, networkTask)
        }
    }
}
