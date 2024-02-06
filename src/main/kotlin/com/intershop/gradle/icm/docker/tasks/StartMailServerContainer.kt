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

import com.intershop.gradle.icm.docker.utils.HostAndPort
import com.intershop.gradle.icm.docker.utils.PortMapping
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import javax.inject.Inject

abstract class StartMailServerContainer @Inject constructor(objectFactory: ObjectFactory) :
        StartExtraContainer(objectFactory) {
    private val primaryPortMapping: Property<PortMapping> = objectFactory.property(PortMapping::class.java)

    fun withPrimaryPortMapping(portMapping: Provider<PortMapping>) {
        primaryPortMapping.set(portMapping)
    }

    @Internal
    fun getPrimaryHostAndPort(): HostAndPort {
        return HostAndPort(container.get().getContainerName(), primaryPortMapping.get().containerPort)
    }
}