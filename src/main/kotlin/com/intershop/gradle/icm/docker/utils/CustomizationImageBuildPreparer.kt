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

package com.intershop.gradle.icm.docker.utils

import com.intershop.gradle.icm.docker.ICMDockerPlugin.Companion.BUILD_MAIN_IMAGE
import com.intershop.gradle.icm.docker.ICMDockerPlugin.Companion.BUILD_TEST_IMAGE
import com.intershop.gradle.icm.docker.tasks.BuildImage
import com.intershop.gradle.icm.docker.tasks.ProvideResourceFromClasspath
import com.intershop.gradle.icm.extension.ProjectConfiguration
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Tar
import com.intershop.gradle.icm.docker.extension.Images as BaseImages
import com.intershop.gradle.icm.docker.extension.image.build.Images as BuildImages

class CustomizationImageBuildPreparer(private val project: Project,
                                      private val images: BaseImages,
                                      private val buildImages: BuildImages,
                                      private val projectConfig: ProjectConfiguration) {

    companion object {
        const val NAME_DOCKERFILE = "Dockerfile"
        const val DIR_DOCKERFILE = "dockerfile"
        const val RESOURCE_DOCKERFILE = "customization/$NAME_DOCKERFILE"
        const val ARG_BASE_IMAGE = "BASE_IMAGE"
        const val ARG_PACKAGE_FILE = "PACKAGE_FILE"
        const val ARG_NAME = "NAME"
    }

    fun prepareImageBuilds() {

        val provideDockerfileTask =
                project.tasks.register("provideDockerfile", ProvideResourceFromClasspath::class.java) { task ->
                    task.group = "icm image build"
                    task.description = "Provides the Dockerfile to be used by dependent tasks"
                    task.resourceName.set(RESOURCE_DOCKERFILE)
                    task.targetLocation.set(project.layout.buildDirectory.file("${DIR_DOCKERFILE}/${NAME_DOCKERFILE}"))
                }
        val mainPkgTaskName = buildImages.mainImage.pkgTaskName.getOrElse("createMainPkg")
        val testPkgTaskName = buildImages.testImage.pkgTaskName.getOrElse("createTestPkg")
        val dockerfileProvider = project.provider { provideDockerfileTask.get().outputs.files.singleFile }

        val mainPkgTask = project.tasks.named(mainPkgTaskName, Tar::class.java)
        val mainBuildImageTask = project.tasks.named(BUILD_MAIN_IMAGE, BuildImage::class.java)

        mainBuildImageTask.configure { task ->
            task.description = "Creates the ${project.name} customization main image"

            task.dependsOn(mainPkgTask, provideDockerfileTask)
            task.srcFiles.from(mainPkgTask)
            task.buildArgs.put(ARG_BASE_IMAGE, images.icmcustomizationbase)
            task.buildArgs.put(ARG_PACKAGE_FILE, project.provider { mainPkgTask.get().outputs.files.singleFile.name })
            task.buildArgs.put(ARG_NAME, project.name)
            task.dockerfile.set(project.objects.fileProperty().fileProvider(dockerfileProvider))

            // add labels for flux deployment and OPS support
            val buildWith = mutableSetOf<String>()
            val versions = mutableMapOf<String, String>()

            addImage(projectConfig.base.image.get(), buildWith, versions)

            projectConfig.modules.all {
                addImage(it.image.get(), buildWith, versions)
            }

            task.labels.put("build.with",buildWith.joinToString(","))
            versions.forEach {
                task.labels.put(it.key, it.value)
            }
        }

        val testPkgTask = project.tasks.named(testPkgTaskName, Tar::class.java)
        val testBuildImageTask = project.tasks.named(BUILD_TEST_IMAGE, BuildImage::class.java)

        testBuildImageTask.configure { task ->
            task.description = "Creates the ${project.name} customization test image"

            task.dependsOn(testPkgTask, provideDockerfileTask)

            task.srcFiles.from(testPkgTask)
            task.buildArgs.put(ARG_BASE_IMAGE, project.provider { mainBuildImageTask.get().images.get().first() })
            task.buildArgs.put(ARG_PACKAGE_FILE, project.provider { testPkgTask.get().outputs.files.singleFile.name })
            task.buildArgs.put(ARG_NAME, project.name)
            task.dockerfile.set(project.objects.fileProperty().fileProvider(dockerfileProvider))
        }
    }

    private fun addImage(imagePath: String,
                         buildWith: MutableSet<String>,
                         versions: MutableMap<String, String>) {
        val imageNameVersion = imagePath.split("/").last()
        val list = imageNameVersion.split(":")

        if(list.size < 2) {
            throw GradleException("There is no image name / version available. Please check the config of $imagePath")
        }

        buildWith.add(list[0])
        versions["${list[0]}.version"] = list[1]
    }
}
