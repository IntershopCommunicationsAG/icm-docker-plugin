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

import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.tasks.APullImage
import com.intershop.gradle.icm.docker.tasks.StartExtraContainerTask
import org.gradle.api.Project

class TestTaskPreparer (private val project: Project,
                        private val dockerExtension: IntershopDockerExtension) {

    companion object {
        const val TASK_EXT_TESTMAIL = "TestMailSrv"
    }

    private val taskPreparer : StandardTaskPreparer by lazy {
        StandardTaskPreparer(project)
    }

    fun createTestMailServerTasks() {
        val containerExt = TASK_EXT_TESTMAIL.toLowerCase()
        taskPreparer.createBaseTasks(TASK_EXT_TESTMAIL, containerExt, dockerExtension.images.testmailsrv)
        val imageTask = project.tasks.named("pull${TASK_EXT_TESTMAIL}", APullImage::class.java)

        project.tasks.register ("start${TASK_EXT_TESTMAIL}", StartExtraContainerTask::class.java) { task ->
            configureContainerTask(task, containerExt)
            task.description = "Starts an special test mail server for ISHUnitTests"
            task.targetImageId( project.provider { imageTask.get().image.get() } )

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

            task.dependsOn(imageTask)
        }
    }

    private fun configureContainerTask(task: DockerCreateContainer, containerExt: String) {
        task.group = "icm container $containerExt"
        task.attachStderr.set(true)
        task.attachStdout.set(true)
        task.hostConfig.autoRemove.set(true)
        task.containerName.set(taskPreparer.getContainerName(containerExt))
    }
}
