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
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Tar
import com.intershop.gradle.icm.docker.extension.Images as BaseImages
import com.intershop.gradle.icm.docker.extension.image.build.Images as BuildImages

class CustomizationImageBuildPreparer(private val project: Project,
                                      private val images: BaseImages,
                                      private val buildImages: BuildImages) {

    companion object {
        const val NAME_DOCKERFILE = "Dockerfile"
        const val DIR_DOCKERFILE = "dockerfile"
        const val RESOURCE_DOCKERFILE_MAIN = "customization/main/$NAME_DOCKERFILE"
        const val RESOURCE_DOCKERFILE_TEST = "customization/test/$NAME_DOCKERFILE"
        const val ARG_BASE_IMAGE = "BASE_IMAGE"
        const val ARG_NAME = "NAME"
    }

    fun prepareImageBuilds() {

        val provideMainDockerfileTask = provideDockerfileTaskOf("main", RESOURCE_DOCKERFILE_MAIN, "${DIR_DOCKERFILE}/main/${NAME_DOCKERFILE}")
        val mainPkgTaskName = buildImages.mainImage.pkgTaskName.getOrElse("createMainPkg")
        val mainPkgTask = project.tasks.named(mainPkgTaskName, Tar::class.java)
        val mainBuildImageTask = project.tasks.named(BUILD_MAIN_IMAGE, BuildImage::class.java)

        mainBuildImageTask.configure { task ->
            task.description = "Creates the ${project.name} customization main image"

            task.dependsOn(mainPkgTask, provideMainDockerfileTask)
            task.srcFiles.from(mainPkgTask)
            task.buildArgs.put(ARG_BASE_IMAGE, images.icmcustomizationbase)
            task.buildArgs.put(ARG_NAME, project.name)
            task.dockerfile.set(project.objects.fileProperty().fileProvider(project.provider{ provideMainDockerfileTask.get().outputs.files.first() }))
        }


        val provideTestDockerfileTask = provideDockerfileTaskOf("test", RESOURCE_DOCKERFILE_TEST, "${DIR_DOCKERFILE}/test/${NAME_DOCKERFILE}")
        val testPkgTaskName = buildImages.testImage.pkgTaskName.getOrElse("createTestPkg")
        val testPkgTask = project.tasks.named(testPkgTaskName, Tar::class.java)
        val testBuildImageTask = project.tasks.named(BUILD_TEST_IMAGE, BuildImage::class.java)

        testBuildImageTask.configure { task ->
            task.description = "Creates the ${project.name} customization test image"

            task.dependsOn(testPkgTask, provideTestDockerfileTask)

            task.srcFiles.from(testPkgTask)
            task.buildArgs.put(ARG_BASE_IMAGE, images.icmcustomizationbase)
            task.buildArgs.put(ARG_NAME, project.name)
            task.dockerfile.set(project.objects.fileProperty().fileProvider(project.provider{ provideTestDockerfileTask.get().outputs.files.first() }))
        }
    }

    private fun provideDockerfileTaskOf(subject: String,  resourceName : String, targetPath: String) : TaskProvider<ProvideResourceFromClasspath> {
        val provideMainDockerfileTask: TaskProvider<ProvideResourceFromClasspath> = project.tasks.register("provide${subject} Dockerfile", ProvideResourceFromClasspath::class.java) { task ->
            task.group = "icm image build"
            task.description = "Provides the $subject Dockerfile to be used by dependent tasks"
            task.resourceName.set(resourceName)
            task.targetLocation.set(project.layout.buildDirectory.file(targetPath))
        }
        return provideMainDockerfileTask
    }
}
