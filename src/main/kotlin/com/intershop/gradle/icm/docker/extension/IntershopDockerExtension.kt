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

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.util.ConfigureUtil
import javax.inject.Inject

open class IntershopDockerExtension @Inject constructor(objectFactory: ObjectFactory) {

    val images: Images = objectFactory.newInstance(Images::class.java)

    @Suppress("unused")
    fun images(closure: Closure<Any>) {
        ConfigureUtil.configure(closure, images)
    }

    fun images(action: Action<in Images>) {
        action.execute(images)
    }

    val ishUnitTest: TestExecution = objectFactory.newInstance(TestExecution::class.java)

    @Suppress("unused")
    fun ishUnitTest(closure: Closure<Any>) {
        ConfigureUtil.configure(closure, ishUnitTest)
    }

    fun ishUnitTest(action: Action<in TestExecution>) {
        action.execute(ishUnitTest)
    }
}
