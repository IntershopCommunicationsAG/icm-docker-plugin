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
import com.github.dockerjava.api.model.Identifier
import com.github.dockerjava.api.model.PushResponseItem
import com.intershop.gradle.icm.docker.tasks.utils.PushImageCallback
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import javax.inject.Inject

open class PushImages
        @Inject constructor(objectFactory: ObjectFactory):
        AbstractDockerRemoteApiTask(), RegistryCredentialsAware {

    private val registryCredentials: DockerRegistryCredentials =
            objectFactory.newInstance(DockerRegistryCredentials::class.java)

    /**
     * The target Docker registry credentials for usage with a task.
     */
    override fun getRegistryCredentials(): DockerRegistryCredentials {
        return registryCredentials
    }

    /**
     * The images including repository, image name and tag used e.g. {@code vieux/apache:2.0}.
     */
    @get:Input
    val images: SetProperty<String> = project.objects.setProperty(String::class.java)

    /**
     * Configures the target Docker registry credentials for use with a task.
     */
    override fun registryCredentials(action: Action<in DockerRegistryCredentials>?) {
        action!!.execute(registryCredentials)
    }

    init {
        group = "icm image build"
        description = "Push all available images to registry."
    }

    override fun runRemoteCommand() {

        val imageIDs = mutableMapOf<String,String>()
        val availableimages = dockerClient.listImagesCmd().withShowAll(true).exec()
        availableimages.forEach { img ->
            images.get().forEach { imgName ->
                if(img.repoTags.contains(imgName)) {
                    imageIDs.put(img.id.split(":").get(1).substring(0,12), imgName)
                }
            }
        }

        images.get().forEach {
            if(! imageIDs.values.contains(it)) {
                logger.warn("Image {} is not available! Please call buildImage before.", it)
            }
        }

        imageIDs.forEach { id, name ->
            logger.quiet("Pushing image '{}' with ID '{}'.", name, id)
            val pushImageCmd = dockerClient.pushImageCmd(name)
            val authConfig = registryAuthLocator.lookupAuthConfig(name, registryCredentials)

            if(authConfig != null) {
                pushImageCmd.withAuthConfig(authConfig)
                println("authconfig ...... " + authConfig.username + " ... for " + authConfig.registryAddress)
            } else {
                pushImageCmd.withAuthConfig(dockerClient.authConfig())
                val authConf = dockerClient.authConfig()
                println("dockerclient ...... " + authConf.username + " ... for " + authConf.registryAddress)
            }

            val callback = createCallback(nextHandler)
            pushImageCmd.exec(callback).awaitCompletion()
        }
    }

    private fun createCallback(nextHandler: Action<in PushResponseItem>?): PushImageCallback {
        return object : PushImageCallback() {
            override fun onNext(item: PushResponseItem) {
                if(nextHandler != null) {
                    try {
                        nextHandler.execute(item)
                    } catch ( e: Exception) {
                        logger.error("Failed to handle push response", e)
                        return
                    }
                }
                super.onNext(item)
            }
        }
    }
}
