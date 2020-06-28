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

import com.intershop.gradle.icm.docker.tasks.APullImage
import com.intershop.gradle.icm.docker.tasks.PullExtraImage
import com.intershop.gradle.icm.docker.tasks.PullImage
import com.intershop.gradle.icm.docker.tasks.RemoveContainerByName
import com.intershop.gradle.icm.docker.tasks.StopExtraContainerTask
import org.gradle.api.Project
import org.gradle.api.provider.Provider

class StandardTaskPreparer(val project: Project) {

    fun getPullTask(taskname: String, imageProvider: Provider<String>): APullImage{
        return with(project) {
            tasks.maybeCreate( taskname, PullExtraImage::class.java ).apply {
                this.image.set(imageProvider)

                this.onlyIf { imageProvider.isPresent }
            }
        }
    }

    fun getBasePullTask(taskname: String, imageProvider: Provider<String>): APullImage{
        return with(project) {
            tasks.maybeCreate( taskname, PullImage::class.java ).apply {
                this.image.set(imageProvider)

                this.onlyIf { imageProvider.isPresent }
            }
        }
    }

    fun getStopTask(taskname: String,
                         containerext: String,
                         imageProvider: Provider<String>): StopExtraContainerTask {
        return with(project) {
            tasks.maybeCreate( taskname, StopExtraContainerTask::class.java ).apply {
                group = "icm docker project"
                containerName.set("${project.name.toLowerCase()}-${containerext}")

                this.onlyIf { imageProvider.isPresent }
            }
        }
    }


    fun getRemoveTask(taskname: String, containerext: String): RemoveContainerByName {
        with(project) {
            val cleanTask = tasks.findByName("clean")

            return tasks.maybeCreate( taskname, RemoveContainerByName::class.java ).apply {
                group = "icm docker project"
                containerName.set("${project.name.toLowerCase()}-${containerext}")

                if(cleanTask != null) {
                    cleanTask.dependsOn(this)
                }
            }
        }
    }
}
