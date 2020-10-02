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
import com.intershop.gradle.icm.docker.utils.Configuration
import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.util.ConfigureUtil
import org.slf4j.LoggerFactory
import javax.inject.Inject

/**
 * Main extension to configure Docker related tasks.
 */
open class IntershopDockerExtension @Inject constructor(project: Project,
                                                        objectFactory: ObjectFactory) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Prefix for all containers, networks and volumes of this project.
     */
    val containerProjectPrefix: Property<String> = objectFactory.property(String::class.java)

    val developmentConfig: DevelopmentConfiguration = objectFactory.newInstance(DevelopmentConfiguration::class.java)

    /**
     * Configures the development information configuration.
     *
     * @param closure closure with project information configuration
     */
    @Suppress("unused")
    fun developmentConfig(closure: Closure<DevelopmentConfiguration>) {
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

    val containerPrefix : String by lazy {
        val containerPrefix = StringBuilder()

        with(developmentConfig) {
            val addPrefix = getConfigProperty(Configuration.ADDITIONAL_CONTAINER_PREFIX, "")
            val addPrefixTrim = trimString(addPrefix)
            if(addPrefixTrim!= "") {
                if(addPrefix != addPrefixTrim) {
                    project.logger.quiet("Additional containerprefix {} is used.", addPrefixTrim)
                }
                containerPrefix.append(addPrefixTrim)
                containerPrefix.append("-")
            }
        }
        val prefixConfig = containerProjectPrefix.getOrElse("")
        if(prefixConfig != "") {
            val prefixConfigTrim = trimString(prefixConfig)
            if(prefixConfig != prefixConfigTrim) {
                project.logger.quiet("Configured prefix {} is used for all containers.", prefixConfigTrim)
            }
            containerPrefix.append(prefixConfigTrim)
        } else {
            val projectPrefix = trimString(project.name)
            project.logger.quiet("Default project prefix {} is used for all containers.", projectPrefix)
            containerPrefix.append(projectPrefix)
        }
        containerPrefix.toString()
    }

    private fun trimString(s: String): String
            = s.replace("\\s+".toRegex(), "").replace("_", "-").toLowerCase()

}
