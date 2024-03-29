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
package com.intershop.gradle.icm.docker.tasks

import com.bmuschko.gradle.docker.DockerRegistryCredentials
import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import com.bmuschko.gradle.docker.tasks.RegistryCredentialsAware
import com.github.dockerjava.api.command.PullImageResultCallback
import com.github.dockerjava.api.model.PullResponseItem
import com.intershop.gradle.icm.docker.tasks.utils.TaskAuthLocatorHelper
import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.options.Option
import java.util.Locale
import javax.inject.Inject

abstract class AbstractPullImage
    @Inject constructor(objectFactory: ObjectFactory) : AbstractDockerRemoteApiTask(), RegistryCredentialsAware {

    private val registryCredentials: DockerRegistryCredentials =
            objectFactory.newInstance(DockerRegistryCredentials::class.java)

    @get:Input
    abstract val image: Property<String>

    /**
     * The target Docker registry credentials for usage with a task.
     */
    override fun getRegistryCredentials(): DockerRegistryCredentials {
        return registryCredentials
    }

    /**
     * Configures the target Docker registry credentials for use with a task.
     *
     * action
     * 6.0.0
     */
    override fun registryCredentials(action: Action<in DockerRegistryCredentials>?) {
        action!!.execute(registryCredentials)
    }

    /**
     * Set the credentials for the task.
     *
     * @param c closure with Docker registry credentials.
     */
    fun registryCredentials(c: Closure<DockerRegistryCredentials>) {
        project.configure(registryCredentials, c)
    }

    @get:Option(option = "forcePull", description = "Call pull always also if the image is available.")
    @get:Input
    val force: Property<Boolean> = objectFactory.property(Boolean::class.java).convention(false)

    /**
     * Executes the remote Docker command.
     */
    override fun runRemoteCommand() {
        with(project) {
            logger.quiet("Check for image '${image.get()}'")

            val imageString = image.get().lowercase(Locale.getDefault())

            if(imageString.contains("SNAPSHOT")) {
                logger.quiet("Please not the local available image is used. " +
                        "If you want update the existing please use the 'forcePull' flag.")
            }
            var pull = true

            if(! force.get()) {
                val listImagesCmd = dockerClient.listImagesCmd()
                listImagesCmd.filters?.set("reference", listOf(image.get()))
                val images = listImagesCmd.exec()
                pull = images.size < 1
            }

            if(pull) {
                logger.quiet("Pulling image '${image.get()}' - the image is locally not available")

                val pullImageCmd = dockerClient.pullImageCmd(image.get())
                val regAuthLocator = TaskAuthLocatorHelper.getLocator(project, registryAuthLocator)

                val authConfig = regAuthLocator.lookupAuthConfig(image.get(), registryCredentials)
                pullImageCmd.withAuthConfig(authConfig)
                val callback = createCallback(nextHandler)
                pullImageCmd.exec(callback).awaitCompletion()
            } else {
                logger.quiet("Image '${image.get()}' will be not pulled, because it is available.")
            }
        }
    }

    private fun createCallback(action: Action<in PullResponseItem>?): PullImageResultCallback {
        return object: PullImageResultCallback() {
            override fun onNext(item: PullResponseItem) {
                if (action != null) {
                    try {
                        action.execute(item)
                    } catch ( e: Exception) {
                        logger.error("Failed to handle pull response", e)
                        return
                    }
                }
                super.onNext(item)
            }
        }
    }
}
