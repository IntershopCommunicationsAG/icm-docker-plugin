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

import com.avast.gradle.dockercompose.DockerComposePlugin
import com.bmuschko.gradle.docker.DockerRemoteApiPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Main plugin class of the project plugin.
 */
open class ICMDockerPlugin: Plugin<Project> {

    /**
     * Main method of a plugin.
     *
     * @param project target project
     */
    override fun apply(project: Project) {
        with(project) {
            logger.info("ICM Docker build plugin will be initialized")

            plugins.apply(DockerComposePlugin::class.java)
            plugins.apply(DockerRemoteApiPlugin::class.java)
        }
    }
}
