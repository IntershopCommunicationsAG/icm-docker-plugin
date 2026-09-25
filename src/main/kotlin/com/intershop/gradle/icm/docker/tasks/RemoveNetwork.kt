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

import org.gradle.api.model.ObjectFactory
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/**
 * Removes the network for all containers of this project.
 */
@DisableCachingByDefault(because = "Interacts with a live Docker daemon - container, image, network and volume state is external to the build and must never be taken from the build cache")
abstract class RemoveNetwork @Inject constructor(objectFactory: ObjectFactory): AbstractNetworkTask(objectFactory) {

    override fun runRemoteCommand() {
        val id = networkIDData()
        if( id != "") {
            try {
                dockerClient.removeNetworkCmd(id).exec()
                logger.quiet("Network '{}' with id '{}' removed.", networkName.get(), id)
            } catch(ex: Exception) {
                logger.error("It was not possible to remove network '" + networkName.get() + "'.(" + ex.message + ")")
            }
        } else {
            logger.quiet("Network '{}' is not available.", networkName.get())
        }
    }
}
