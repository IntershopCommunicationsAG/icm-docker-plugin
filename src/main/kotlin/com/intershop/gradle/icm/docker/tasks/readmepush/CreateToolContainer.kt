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
package com.intershop.gradle.icm.docker.tasks.readmepush

import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

open class CreateToolContainer
    @Inject constructor(objectFactory: ObjectFactory) : DockerCreateContainer(objectFactory) {

    init {
        group = "icm container readme push"
        description = "Create image for pushing readme"

        attachStderr.set(true)
        attachStdout.set(true)

        hostConfig.autoRemove.set(true)
        
    }


}
