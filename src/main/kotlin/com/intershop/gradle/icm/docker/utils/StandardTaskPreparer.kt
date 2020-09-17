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

import com.intershop.gradle.icm.docker.tasks.PullExtraImage
import com.intershop.gradle.icm.docker.tasks.PullImage
import com.intershop.gradle.icm.docker.tasks.RemoveContainerByName
import com.intershop.gradle.icm.docker.tasks.StopExtraContainerTask
import org.gradle.api.Project
import org.gradle.api.UnknownTaskException
import org.gradle.api.provider.Provider

class StandardTaskPreparer(private val project: Project) {

    fun createBaseTasks(taskext: String, containerext: String,
                        imageProvider: Provider<String>, isAppSrv: Boolean = false) {
        val pullImgTask = if(isAppSrv) PullImage::class.java else PullExtraImage::class.java

        project.tasks.register( "pull${taskext}", pullImgTask ) { task ->
            task.group = "icm container $containerext"
            task.description = "Pull image from registry"
            task.image.set(imageProvider)
        }

        project.tasks.register( "stop${taskext}", StopExtraContainerTask::class.java ) { task ->
            task.group = "icm container $containerext"
            task.description = "Stop running container"
            task.containerName.set(getContainerName(containerext))
        }

        val removeTask = project.tasks.register( "remove${taskext}", RemoveContainerByName::class.java ) { task ->
            task.group = "icm container $containerext"
            task.description = "Remove container from Docker"

            task.containerName.set(getContainerName(containerext))
        }

        try {
            project.tasks.named("clean").configure { task ->
                task.dependsOn(removeTask)
            }
        } catch( ex: UnknownTaskException) {
            project.logger.info("Task clean is not available.")
        }

        project.tasks.named("containerClean").configure { task ->
            task.dependsOn(removeTask)
        }
    }

    fun getContainerName(ext: String) = "${project.name.toLowerCase()}-${ext}"
}
