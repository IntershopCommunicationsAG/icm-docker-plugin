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
package com.intershop.gradle.icm.docker.extension.readme.push

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import javax.inject.Inject

open class ReadmePushConfiguration @Inject constructor(val project: Project, objectFactory: ObjectFactory) {

    /**
     * Provider for the base image of the push task.
     */
    val toolImageProvider: Provider<String>
        get() = toolImage


    val toolImage: Property<String> = objectFactory.property(String::class.java)

    /**
     * Provider for the base image of the project project.
     */
    val baseImageNameProvider: Provider<String>
        get() = baseImageName

    /**
     * Creation time of images in one project.
     */
    val baseImageName: Property<String> = objectFactory.property(String::class.java)

    val images: Images = objectFactory.newInstance(Images::class.java, project)

    /**
     * Configures images configuration from a closure.
     *
     * @param closure   closure with an image configuration.
     */
    @Suppress("unused")
    fun images(closure: Closure<Images>) {
        project.configure(images, closure)
    }

    /**
     * Configures images configuration from an action.
     *
     * @param action   action with an image configuration.
     */
    fun images(action: Action<in Images>) {
        action.execute(images)
    }

    init {
        toolImage.convention("chko/docker-pushrm:1")
        baseImageName.convention("intershophub/icm")
    }
}
