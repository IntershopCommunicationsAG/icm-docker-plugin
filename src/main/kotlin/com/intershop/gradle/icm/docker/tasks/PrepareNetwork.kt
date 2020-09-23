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

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import javax.inject.Inject

/**
 * Creates or delivers the network for all containers
 * of this project.
 */
open class PrepareNetwork @Inject constructor(objectFactory: ObjectFactory): AbstractNetworkTask(objectFactory) {

    /**
     * The id of the created network.
     */
    @Internal
    val networkId: Property<String> = objectFactory.property(String::class.java)

    override fun runRemoteCommand() {
        var id = networkIDData()
        if( id == "") {
            val networkCreated = dockerClient.createNetworkCmd().withName(networkName.get()).exec()
            id = networkCreated.id
            logger.quiet("Network '{}' with id '{}' created.", networkName.get(), id)
        } else {
            logger.quiet("Network '{}' with id '{}' is available.", networkName.get(), id)
        }

        networkId.set(id)
    }
}
