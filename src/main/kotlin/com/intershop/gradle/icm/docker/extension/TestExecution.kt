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

import org.gradle.api.provider.SetProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

open class TestExecution @Inject constructor(objectFactory: ObjectFactory) {

    companion object {
        const val HTML_ANT_TESTREPORT_CONFIG = "junitXmlToHtml"
    }

    val testConfigSet: SetProperty<Suite> = objectFactory.setProperty(Suite::class.java)

    fun test(suite: Suite) {
        testConfigSet.add(suite)
    }

    fun test(cartrige: String, suite: String) {
        testConfigSet.add(Suite(cartrige, suite))
    }

    val failFast: Property<Boolean> = objectFactory.property(Boolean::class.java)

    init {
        failFast.set(false)
    }
}
