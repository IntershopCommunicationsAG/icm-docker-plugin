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

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import javax.inject.Inject

/**
 * Extension to configure a special image build.
 */
class ImageConfiguration @Inject constructor(objectFactory: ObjectFactory) {

    /**
     * Provider for image creation.
     */
    val createImageProvider: Provider<Boolean>
        get() = this.createImage

    /**
     * The image should be created if the value is true.
     */
    val createImage: Property<Boolean> = objectFactory.property(Boolean::class.java)

    /**
     * Provider for image name extension.
     */
    val imageExtensionProvider: Provider<String>
        get() = this.imageExtension

    /**
     * Image name extension of the special image.
     */
    val imageExtension: Property<String> = objectFactory.property(String::class.java)

    /**
     * Provider for description extension.
     */
    val descriptionProvider: Provider<String>
        get() = this.imageExtension

    /**
     * Description extension of the special image.
     */
    val description: Property<String> = objectFactory.property(String::class.java)

    init {
        createImage.convention(false)
        imageExtension.convention("")
    }
}