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

package com.intershop.gradle.icm.docker.extension.geb

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

open class GebConfiguration @Inject constructor(objectFactory: ObjectFactory) {

    val gebEnvironment: Property<String> = objectFactory.property(String::class.java)

    val localDriver: NamedDomainObjectContainer<GebLocalDriver>
            = objectFactory.domainObjectContainer(GebLocalDriver::class.java)

    init {
        gebEnvironment.set( "firefoxContainer" )
    }
}