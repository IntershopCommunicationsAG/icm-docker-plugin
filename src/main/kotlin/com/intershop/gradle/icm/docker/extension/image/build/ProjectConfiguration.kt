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
package com.intershop.gradle.icm.docker.extension.image.build

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Extension to configure the image labels.
 */
open class ProjectConfiguration @Inject constructor(val project: Project, objectFactory: ObjectFactory) {

    /**
     * License provider of all images.
     */
    val licenseProvider: Provider<String>
        get() = this.license

    /**
     * Product id of the project.
     */
    val license: Property<String> = objectFactory.property(String::class.java)

    /**
     * Maintainer of all images.
     */
    val maintainerProvider: Provider<String>
        get() = maintainer

    /**
     * Maintainer of the images.
     */
    val maintainer: Property<String> = objectFactory.property(String::class.java)

    /**
     * Provider for base description of images in one project.
     */
    val baseDescriptionProvider: Provider<String>
        get() = baseDescription

    /**
     * Base description of images in one project.
     */
    val baseDescription: Property<String> = objectFactory.property(String::class.java)

    /**
     * Provider for the version information of images in one project.
     */
    val versionProvider: Provider<String>
        get() = version

    /**
     * Version information of images in one project.
     */
    val version: Property<String> = objectFactory.property(String::class.java)

    /**
     * Provider for the creation time of images in one project.
     */
    val createdProvider: Provider<String>
        get() = created

    /**
     * Creation time of images in one project.
     */
    val created: Property<String> = objectFactory.property(String::class.java)

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
        license.convention("Intershop Communications AG")
        maintainer.convention("Intershop Communications AG \"www.intershop.de\"")
        baseDescription.convention("Intershop Commerce Management")
        created.convention(ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT))

        baseImageName.convention("server/intershop/icm")
    }
}
