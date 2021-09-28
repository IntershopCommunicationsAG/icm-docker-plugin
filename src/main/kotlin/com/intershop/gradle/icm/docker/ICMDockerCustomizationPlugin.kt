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
package com.intershop.gradle.icm.docker

import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.utils.CustomizationImageBuildPreparer
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Main plugin class of the customization plugin.
 *
 * Configures the build to create customization images. That's in detail:
 *
 * - set the following fields to values matching the actual customization
 *   - [com.intershop.gradle.icm.docker.extension.image.build.Images.mainImage.nameExtension]
 *   - [com.intershop.gradle.icm.docker.extension.image.build.Images.mainImage.description]
 *   - [com.intershop.gradle.icm.docker.extension.image.build.Images.testImage.nameExtension]
 *   - [com.intershop.gradle.icm.docker.extension.image.build.Images.testImage.description]
 * - configure the tasks referenced by
 *   - [com.intershop.gradle.icm.docker.extension.image.build.Images.mainImage.pkgTaskName]
 *   - [com.intershop.gradle.icm.docker.extension.image.build.Images.testImage.pkgTaskName]
 *
 * @see CustomizationImageBuildPreparer
 */
open class ICMDockerCustomizationPlugin : Plugin<Project> {

    companion object {
        const val INTERSHOP_EXTENSION_NAME = "intershop"
    }

    /**
     * Main method of a plugin.
     *
     * @param project target project
     */
    override fun apply(project: Project) {
        with(project) {
            if (project.rootProject == this) {
                logger.info("ICM Docker build plugin for customizations will be initialized")
                plugins.apply(ICMDockerPlugin::class.java)

                val dockerExtension = extensions.findByType(
                    IntershopDockerExtension::class.java
                ) ?: extensions.create("intershop_docker", IntershopDockerExtension::class.java, project)

                val customizationName = project.getCustomizationName()
                with(dockerExtension.imageBuild.images) {
                    mainImage.nameExtension.set("")
                    mainImage.description.set("customization $customizationName")
                    testImage.nameExtension.set("test")
                    testImage.description.set("test for customization $customizationName")
                }

                extensions.findByName(INTERSHOP_EXTENSION_NAME)
                    ?: throw GradleException("This plugin requires the plugin 'com.intershop.gradle.icm.project'!")


                CustomizationImageBuildPreparer(this, dockerExtension.images, dockerExtension.imageBuild.images).prepareImageBuilds()
            }
        }
    }

    /**
     * Determines the name of the customization that is associated the project this plugin is applied to.
     *
     * __Attention__: extends the class [Project] by this function
     */
    open fun Project.getCustomizationName(): String = name

}
