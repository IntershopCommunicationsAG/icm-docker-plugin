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
package com.intershop.gradle.icm.docker.extension.image.build

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.util.ConfigureUtil
import javax.inject.Inject

open class Images @Inject constructor(objectFactory: ObjectFactory) {

    val mainImage: ImageConfiguration = objectFactory.newInstance(ImageConfiguration::class.java)

    /**
     * Configures the main image configuration from a closure.
     *
     * @param closure   closure with an image configuration.
     */
    @Suppress("unused")
    fun mainImage(closure: Closure<ImageConfiguration>) {
        ConfigureUtil.configure(closure, mainImage)
    }

    /**
     * Configures the main image configuration from an action.
     *
     * @param action   action with an image configuration.
     */
    fun mainImage(action: Action<in ImageConfiguration>) {
        action.execute(mainImage)
    }

    val testImage: ImageConfiguration = objectFactory.newInstance(ImageConfiguration::class.java)

    /**
     * Configures the test image configuration from a closure.
     *
     * @param closure   closure with an image configuration.
     */
    @Suppress("unused")
    fun testImage(closure: Closure<ImageConfiguration>) {
        ConfigureUtil.configure(closure, testImage)
    }

    /**
     * Configures the test image configuration from an action.
     *
     * @param action   action with an image configuration.
     */
    fun testImage(action: Action<in ImageConfiguration>) {
        action.execute(testImage)
    }

    val initImage: ImageConfiguration = objectFactory.newInstance(ImageConfiguration::class.java)

    /**
     * Configures the init image configuration from a closure.
     *
     * @param closure   closure with an image configuration.
     */
    @Suppress("unused")
    fun initImage(closure: Closure<ImageConfiguration>) {
        ConfigureUtil.configure(closure, initImage)
    }

    /**
     * Configures the init image configuration from an action.
     *
     * @param action   action with an image configuration.
     */
    fun initImage(action: Action<in ImageConfiguration>) {
        action.execute(initImage)
    }

    val initTestImage: ImageConfiguration = objectFactory.newInstance(ImageConfiguration::class.java)

    /**
     * Configures the test init image configuration from a closure.
     *
     * @param closure   closure with an image configuration.
     */
    @Suppress("unused")
    fun initTestImage(closure: Closure<ImageConfiguration>) {
        ConfigureUtil.configure(closure, initTestImage)
    }

    /**
     * Configures the test init image configuration from an action.
     *
     * @param action   action with an image configuration.
     */
    fun initTestImage(action: Action<in ImageConfiguration>) {
        action.execute(initTestImage)
    }

    init {
        mainImage.nameExtension.set("as")
        mainImage.description.set("Appserver")

        initImage.nameExtension.set("as-init")
        initImage.description.set("Appserver Initialization")

        testImage.nameExtension.set("as-test")
        testImage.description.set("Appserver Test")

        initTestImage.nameExtension.set("as-test-init")
        initTestImage.description.set("Appserver Test Initialization")
    }
}
