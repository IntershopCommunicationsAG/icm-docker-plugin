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

import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import com.intershop.gradle.icm.docker.ICMDockerPlugin.Companion.BUILD_INIT_IMAGE
import com.intershop.gradle.icm.docker.ICMDockerPlugin.Companion.BUILD_INIT_TEST_IMAGE
import com.intershop.gradle.icm.docker.ICMDockerPlugin.Companion.BUILD_MAIN_IMAGE
import com.intershop.gradle.icm.docker.ICMDockerPlugin.Companion.BUILD_TEST_IMAGE
import com.intershop.gradle.icm.docker.extension.image.build.ImageConfiguration
import com.intershop.gradle.icm.docker.tasks.BuildImage
import com.intershop.gradle.icm.docker.extension.Images as BaseImages
import com.intershop.gradle.icm.docker.extension.image.build.Images as BuildImages

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Tar

class ProjectImageBuildPreparer(private val project: Project,
                                private val images: BaseImages,
                                private val buildImages: BuildImages) {

    companion object {
        const val PRJ_DIR = "intershop-prj"

        const val PREBUILDSTAGE = "PREBUILD"
        const val BUILDSTAGE = "BUILD"
        const val USERSET = "--chown=intershop:intershop"

        const val MAIN_ICM_DIR = "/intershop"
        const val INIT_ICM_DIR = "/intershop-init"

        const val DIFFDIRSCRIPT = "/${PRJ_DIR}/diffdir.sh"
        const val WORKDIR = "${MAIN_ICM_DIR}/${PRJ_DIR}"

        const val DOCKERFILE_MAIN = "dockerfileMain"
        const val DOCKERFILE_TEST = "dockerfileTest"
        const val DOCKERFILE_INIT = "dockerfileInit"
        const val DOCKERFILE_INITTEST = "dockerfileInitTest"

        const val DOCKERFILE_MAIN_DIR = "dockerfile/main"
        const val DOCKERFILE_TEST_DIR = "dockerfile/test"
        const val DOCKERFILE_INIT_DIR = "dockerfile/init"
        const val DOCKERFILE_INITTEST_DIR = "dockerfile/inittest"
    }

    fun prepareImageBuilds() {
        val mainPkgTaskName = buildImages.mainImage.pkgTaskName.getOrElse("createMainPkg")
        val mainPkgTask = project.tasks.named(mainPkgTaskName, Tar::class.java)
        val mainBuildImageTask = project.tasks.named(BUILD_MAIN_IMAGE, BuildImage::class.java )

        val mainDockerFile = getBaseDockerfileTask(
                DOCKERFILE_MAIN, DOCKERFILE_MAIN_DIR, images.icmbase,
                MAIN_ICM_DIR, mainPkgTask, buildImages.mainImage)

        mainBuildImageTask.configure { task ->
            task.dockerfile.set(getDockerfile(buildImages.mainImage.dockerfile, mainDockerFile))
            task.srcFiles.from(mainPkgTask)
            task.dependsOn(mainDockerFile)
        }

        val initPkgTaskName = buildImages.initImage.pkgTaskName.getOrElse("createInitPkg")
        val initPkgTask = project.tasks.named(initPkgTaskName, Tar::class.java)
        val initBuildImageTask = project.tasks.named(BUILD_INIT_IMAGE, BuildImage::class.java )

        val initDockerFile = getBaseDockerfileTask(
                DOCKERFILE_INIT, DOCKERFILE_INIT_DIR, images.icminit,
                INIT_ICM_DIR, initPkgTask, buildImages.initImage)

        initBuildImageTask.configure { task ->
            task.dockerfile.set( getDockerfile(buildImages.initImage.dockerfile, initDockerFile) )
            task.srcFiles.from(initPkgTask)
            task.dependsOn(initDockerFile)
        }

        val testPkgTaskName = buildImages.testImage.pkgTaskName.getOrElse("createTestPkg")
        val testPkgTask = project.tasks.named(testPkgTaskName, Tar::class.java)
        val testBuildImageTask = project.tasks.named(BUILD_TEST_IMAGE, BuildImage::class.java )

        val testDockerFile = getBaseDockerfileTask(
                DOCKERFILE_TEST, DOCKERFILE_TEST_DIR, project.provider { "BASE_IMAGE" },
                MAIN_ICM_DIR, testPkgTask, buildImages.testImage)

        testBuildImageTask.configure { task ->
            task.dockerfile.set( getDockerfile(buildImages.testImage.dockerfile, testDockerFile) )
            task.srcFiles.from(mainPkgTask)
            task.dependsOn(testDockerFile)
        }

        val initTestPkgTaskName = buildImages.initImage.pkgTaskName.getOrElse("createInitTestPkg")
        val initTestPkgTask = project.tasks.named(initTestPkgTaskName, Tar::class.java)
        val initTestBuildImageTask = project.tasks.named(BUILD_INIT_TEST_IMAGE, BuildImage::class.java )

        val initTestDockerFile = getBaseDockerfileTask(
                DOCKERFILE_INITTEST, DOCKERFILE_INITTEST_DIR, project.provider { "BASE_IMAGE" },
                INIT_ICM_DIR, initTestPkgTask, buildImages.initTestImage)

        initTestBuildImageTask.configure { task ->
            task.dockerfile.set( getDockerfile(buildImages.initTestImage.dockerfile, initTestDockerFile) )
            task.srcFiles.from(initTestPkgTask)
            task.dependsOn(initTestDockerFile)
        }
    }

    private fun getBaseDockerfileTask(taskname: String,
                                      dirname: String,
                                      image: Provider<String>,
                                      icmdir: String, pkg: TaskProvider<Tar>,
                                      imgConfiguration: ImageConfiguration): TaskProvider<Dockerfile> =
            project.tasks.register(taskname, Dockerfile::class.java) { task ->
                task.arg("SETUP_IMAGE")
                task.arg("BASE_IMAGE")

                task.from(Dockerfile.From("\${SETUP_IMAGE}").withStage(PREBUILDSTAGE))
                task.runCommand("mkdir -p /${PRJ_DIR}/org")

                task.addFile(project.provider {
                    Dockerfile.File(pkg.get().outputs.files.first().name, "/${PRJ_DIR}/org")
                } )

                task.runCommand("echo '#!/bin/sh' > $DIFFDIRSCRIPT")
                task.runCommand("echo 'for i in `ls -1 ${WORKDIR}/org`' >> $DIFFDIRSCRIPT")
                task.runCommand("echo '  do' >> $DIFFDIRSCRIPT")
                task.runCommand("echo '    dirdiff -srcdir ${WORKDIR}/org/\$i -targetdir ${icmdir}/\$i" +
                        " -diffdir ${WORKDIR}/diff/' >> $DIFFDIRSCRIPT")
                task.runCommand("echo '  done' >> $DIFFDIRSCRIPT")

                task.runCommand("chmod a+x $DIFFDIRSCRIPT")

                task.from( project.provider {
                    if(image.getOrElse("").isEmpty()) {
                        throw GradleException("It is necessary to provide an ICM base oder init image!")
                    }
                    Dockerfile.From(image.getOrElse("")).withStage(BUILDSTAGE) } )
                task.runCommand("mkdir -p ${WORKDIR}/diff")
                task.instruction("COPY --from=${PREBUILDSTAGE} $USERSET /${PRJ_DIR}/ ${WORKDIR}/")
                task.runCommand("${MAIN_ICM_DIR}${DIFFDIRSCRIPT}")

                task.from( project.provider {
                    if(image.getOrElse("").isEmpty()) {
                        throw GradleException("It is necessary to provide an ICM base oder init image!")
                    }
                    Dockerfile.From(image.getOrElse(""))
                } )
                task.instruction("COPY --from=${BUILDSTAGE} $USERSET ${WORKDIR}/diff ${icmdir}/")

                task.destFile.set(project.layout.buildDirectory.file("${dirname}/Dockerfile"))

                task.onlyIf { checkDockerFile(imgConfiguration.dockerfile) }
            }

    fun checkDockerFile(dockerfile: RegularFileProperty): Boolean =
            ! dockerfile.isPresent || ! dockerfile.asFile.get().exists()

    fun getDockerfile(dockerfile: RegularFileProperty,
                      dockerfileTask: TaskProvider<Dockerfile>): Provider<RegularFile> =
            project.provider {
                if(dockerfile.orNull != null && dockerfile.asFile.get().exists()) {
                    dockerfile.get()
                } else {
                    dockerfileTask.get().destFile.get()
                }
            }
}
