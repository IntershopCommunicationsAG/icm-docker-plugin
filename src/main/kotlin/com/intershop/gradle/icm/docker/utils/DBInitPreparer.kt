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

import com.bmuschko.gradle.docker.tasks.container.DockerExecContainer
import com.bmuschko.gradle.docker.tasks.container.DockerInspectExecContainer
import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer
import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
import com.github.dockerjava.api.command.InspectExecResponse
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task

class DBInitPreparer(val project: Project,
                     val startContainer: DockerStartContainer,
                     val removeContainer: DockerRemoveContainer) {

    companion object {
        const val TASK_EXEC = "dbinit"
        const val TASK_INSPECT = "inspectDBInit"
    }

    fun getExec(): DockerExecContainer {
        return with(project) {
            tasks.maybeCreate(TASK_EXEC, DockerExecContainer::class.java).apply {
                attachStderr.set(true)
                attachStdout.set(true)

                targetContainerId(startContainer.containerId)
                dependsOn(startContainer)

                withCommand(listOf("/intershop/bin/intershop.sh", "dbinit", "-classic", "--clean-db=yes"))

                onNext { message ->
                    // Each log message from the container will be passed as it's made available
                    logger.quiet(" dbinit: " + message.toString())
                }
            }
        }
    }

    fun inspectExec(): DockerInspectExecContainer {
        return with(project) {
            tasks.maybeCreate(TASK_INSPECT, DockerInspectExecContainer::class.java).apply {
                finalizedBy(removeContainer)
                dependsOn(getExec())

                targetExecId("test") //getExec().execIds)

                onNext { r ->
                    if(r is InspectExecResponse && r != null) {
                        if (r.getExitCodeLong() > 0) {
                            throw GradleException("DBInit run failed with failures. Please check files in "
                                        + project.layout.buildDirectory.dir("server/logs"))
                        }
                    }
                }
            }
        }
    }
}
