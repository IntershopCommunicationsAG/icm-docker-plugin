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
package com.intershop.gradle.icm.docker.extension

import com.intershop.gradle.icm.docker.extension.image.build.ProjectConfiguration
import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.util.ConfigureUtil
import javax.inject.Inject

/**
 * Main extension to configure Docker related tasks.
 */
open class IntershopDockerExtension @Inject constructor(objectFactory: ObjectFactory) {

    val developmentConfig: DevelopmentConfiguration = objectFactory.newInstance(DevelopmentConfiguration::class.java)

    /**
     * Configures the development information configuration.
     *
     * @param closure closure with project information configuration
     */
    @Suppress("unused")
    fun developmentConfig(closure: Closure<Any>) {
        ConfigureUtil.configure(closure, developmentConfig)
    }

    /**
     * Configures the project information configuration.
     *
     * @param action action with project information configuration
     */
    fun developmentConfig(action: Action<in DevelopmentConfiguration>) {
        action.execute(developmentConfig)
    }

    val images: Images = objectFactory.newInstance(Images::class.java)

    /**
     * Configures images configuration from a closure.
     *
     * @param closure   closure with an image configuration.
     */
    @Suppress("unused")
    fun images(closure: Closure<Images>) {
        ConfigureUtil.configure(closure, images)
    }

    /**
     * Configures images configuration from an action.
     *
     * @param action   action with an image configuration.
     */
    fun images(action: Action<in Images>) {
        action.execute(images)
    }

    val imageBuild: ProjectConfiguration = objectFactory.newInstance(ProjectConfiguration::class.java)

    /**
     * Configures images configuration from a closure.
     *
     * @param closure   closure with an image configuration.
     */
    @Suppress("unused")
    fun imageBuild(closure: Closure<ProjectConfiguration>) {
        ConfigureUtil.configure(closure, imageBuild)
    }

    /**
     * Configures images configuration from an action.
     *
     * @param action   action with an image configuration.
     */
    fun imageBuild(action: Action<in ProjectConfiguration>) {
        action.execute(imageBuild)
    }

    val ishUnitTests: NamedDomainObjectContainer<Suite> =
            objectFactory.domainObjectContainer(Suite::class.java)
}
