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
import com.github.dockerjava.api.model.PushResponseItem
import com.github.dockerjava.core.command.PushImageResultCallback
import com.intershop.gradle.icm.docker.ICMDockerPlugin
import com.intershop.gradle.icm.docker.utils.BuildImageRegistry
import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceRegistry
import org.gradle.api.services.internal.BuildServiceRegistryInternal
import org.gradle.util.ConfigureUtil
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
     * Configures the target Docker registry credentials for use with a task.
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
        ConfigureUtil.configure(c, registryCredentials)
    }

    init {
        group = "icm image build"
        description = "Push all available images to registry."
        onlyIf {
            project.hasProperty("runOnCI") &&
                    project.property("runOnCI") == "true"
        }
    }

    override fun runRemoteCommand() {

        val serviceRegistry = services.get(BuildServiceRegistryInternal::class.java)
        val buildImgResourceProvider: Provider<BuildImageRegistry> = getBuildService(serviceRegistry,
                ICMDockerPlugin.BUILD_IMG_REGISTRY
        )

        buildImgResourceProvider.get().images.forEach { image ->

            logger.quiet("Pushing image '${image}'.")
            val pushImageCmd = dockerClient.pushImageCmd(image)
            val authConfig = registryAuthLocator.lookupAuthConfig(image, registryCredentials)
            pushImageCmd.withAuthConfig(authConfig)
            val callback = createCallback(nextHandler)
            pushImageCmd.exec(callback).awaitCompletion()
        }
    }

    private fun createCallback(nextHandler: Action<in PushResponseItem>?): PushImageResultCallback {
        return object : PushImageResultCallback() {
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

    private fun <T: BuildService<*>> getBuildService(registry: BuildServiceRegistry, name: String): Provider<T> {
        val registration = registry.registrations.findByName(name)
                ?: throw GradleException ("Unable to find build service with name '$name'.")

        return registration.getService() as Provider<T>
    }
}
