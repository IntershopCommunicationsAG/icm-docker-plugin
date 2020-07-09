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

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import javax.inject.Inject

/**
 * Extension to configure a special image build.
 */
open class ImageConfiguration @Inject constructor(objectFactory: ObjectFactory) {

    /**
     * Provider for image name extension.
     */
    val nameExtensionProvider: Provider<String>
        get() = this.nameExtension

    /**
     * Image name extension of the special image.
     */
    val nameExtension: Property<String> = objectFactory.property(String::class.java)

    /**
     * Provider for description extension.
     */
    val descriptionProvider: Provider<String>
        get() = this.description

    /**
     * Description extension of the special image.
     */
    val description: Property<String> = objectFactory.property(String::class.java)

    /**
     * File collection for the build of the image.
     */
    val srcFiles: ConfigurableFileCollection = objectFactory.fileCollection()

    fun addFiles(srcfiles: FileCollection) {
        srcFiles.from(srcfiles)
    }

    val pkgTaskNameProvider: Provider<String>
        get() = this.pkgTaskNameProvider

    val pkgTaskName: Property<String> = objectFactory.property(String::class.java)

    val dockerfileProvider: Provider<RegularFile>
        get() = this.dockerfile

    val dockerfile: RegularFileProperty = objectFactory.fileProperty()

    /**
     * Provider for description extension.
     */
    val dockerBuildDirProvider: Provider<String>
        get() = this.dockerBuildDir

    /**
     * Description extension of the special image.
     */
    val dockerBuildDir: Property<String> = objectFactory.property(String::class.java)

    val enabledProvider: Provider<Boolean>
        get() = this.enabled

    val enabled: Property<Boolean> = objectFactory.property(Boolean::class.java)
}
