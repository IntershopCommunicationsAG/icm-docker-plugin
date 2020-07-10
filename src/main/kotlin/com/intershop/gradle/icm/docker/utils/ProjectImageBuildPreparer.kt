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
import org.gradle.api.tasks.bundling.Tar

class ProjectImageBuildPreparer(val project: Project, val images: BaseImages, val buildImages: BuildImages) {

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

        const val ERROR_MSG = "Package task must be a Tar task and build image task must be a BuildImage task!"
    }

    fun prepareImageBuilds() {
        val mainPkgTaskName = buildImages.mainImage.pkgTaskName.getOrElse("createMainPkg")
        val mainPkgTask = project.tasks.getByName(mainPkgTaskName)
        val mainBuildImageTask = project.tasks.getByName(BUILD_MAIN_IMAGE)

        if(mainPkgTask is Tar && mainBuildImageTask is BuildImage) {
                val mainDockerFile = getBaseDockerfileTask(
                        DOCKERFILE_MAIN, DOCKERFILE_MAIN_DIR, images.icmbase,
                        MAIN_ICM_DIR, mainPkgTask, buildImages.mainImage)

                mainBuildImageTask.dockerfile.set( getDockerfile(buildImages.mainImage.dockerfile, mainDockerFile) )
                mainBuildImageTask.srcFiles.from(mainPkgTask)
                mainBuildImageTask.dependsOn(mainDockerFile)
        } else {
            throw GradleException("MainImage: $ERROR_MSG")
        }

        val initPkgTaskName = buildImages.initImage.pkgTaskName.getOrElse("createInitPkg")
        val initPkgTask = project.tasks.getByName(initPkgTaskName)
        val initBuildImageTask = project.tasks.getByName(BUILD_INIT_IMAGE)

        if(initPkgTask is Tar && initBuildImageTask is BuildImage) {
                val initDockerFile = getBaseDockerfileTask(
                        DOCKERFILE_INIT, DOCKERFILE_INIT_DIR, images.icminit,
                        INIT_ICM_DIR, initPkgTask, buildImages.initImage)

                initBuildImageTask.dockerfile.set( getDockerfile(buildImages.initImage.dockerfile, initDockerFile) )
                initBuildImageTask.srcFiles.from(initPkgTask)
                initBuildImageTask.dependsOn(initDockerFile)
        } else {
            throw GradleException("InitImage: $ERROR_MSG")
        }

        val testPkgTaskName = buildImages.testImage.pkgTaskName.getOrElse("createTestPkg")
        val testPkgTask = project.tasks.getByName(testPkgTaskName)
        val testBuildImageTask = project.tasks.getByName(BUILD_TEST_IMAGE)

        if(testPkgTask is Tar && testBuildImageTask is BuildImage) {
                val testDockerFile = getBaseDockerfileTask(
                        DOCKERFILE_TEST, DOCKERFILE_TEST_DIR, project.provider { "BASE_IMAGE" },
                        MAIN_ICM_DIR, testPkgTask, buildImages.testImage)

                testBuildImageTask.dockerfile.set( getDockerfile(buildImages.testImage.dockerfile, testDockerFile) )
                mainBuildImageTask.srcFiles.from(mainPkgTask)
                mainBuildImageTask.dependsOn(testDockerFile)
        } else {
            throw GradleException("TestImage: $ERROR_MSG")
        }

        val initTestPkgTaskName = buildImages.initImage.pkgTaskName.getOrElse("createInitTestPkg")
        val initTestPkgTask = project.tasks.getByName(initTestPkgTaskName)
        val initTestBuildImageTask = project.tasks.getByName(BUILD_INIT_TEST_IMAGE)

        if(initTestPkgTask is Tar && initTestBuildImageTask is BuildImage) {
                val initTestDockerFile = getBaseDockerfileTask(
                        DOCKERFILE_INITTEST, DOCKERFILE_INITTEST_DIR, project.provider { "BASE_IMAGE" },
                        INIT_ICM_DIR, initTestPkgTask, buildImages.initTestImage)

                initTestBuildImageTask.dockerfile.set( getDockerfile(buildImages.initTestImage.dockerfile,
                        initTestDockerFile) )
                initTestBuildImageTask.srcFiles.from(initTestPkgTask)
                initTestBuildImageTask.dependsOn(initTestDockerFile)
        } else {
            throw GradleException("InitTestImage: $ERROR_MSG")
        }
    }

    private fun getBaseDockerfileTask(taskname: String,
                                      dirname: String,
                                      image: Provider<String>,
                                      icmdir: String, pkg: Tar,
                                      imgConfiguration: ImageConfiguration): Dockerfile {
        return with(project) {
            tasks.maybeCreate(taskname, Dockerfile::class.java).apply {
                arg("SETUP_IMAGE")

                from(Dockerfile.From("\${SETUP_IMAGE}").withStage(PREBUILDSTAGE))
                runCommand("mkdir -p /${PRJ_DIR}/org")

                addFile(project.provider {
                    Dockerfile.File(pkg.outputs.files.first().name, "/${PRJ_DIR}/org")
                } )

                runCommand("echo '#!/bin/sh' > $DIFFDIRSCRIPT")
                runCommand("echo 'for i in `ls -1 ${WORKDIR}/org`' >> $DIFFDIRSCRIPT")
                runCommand("echo '  do' >> $DIFFDIRSCRIPT")
                runCommand("echo '    dirdiff -srcdir ${WORKDIR}/org/\$i -targetdir ${icmdir}/\$i" +
                        " -diffdir ${WORKDIR}/diff/' >> $DIFFDIRSCRIPT")
                runCommand("echo '  done' >> $DIFFDIRSCRIPT")

                runCommand("chmod a+x $DIFFDIRSCRIPT")

                from( project.provider {
                    if(image.getOrElse("").isEmpty()) {
                        throw GradleException("It is necessary to provide an ICM base oder init image!")
                    }
                    Dockerfile.From(image.getOrElse("")).withStage(BUILDSTAGE) } )
                runCommand("mkdir -p ${WORKDIR}/diff")
                instruction("COPY --from=${PREBUILDSTAGE} $USERSET /${PRJ_DIR}/ ${WORKDIR}/")
                runCommand("${MAIN_ICM_DIR}${DIFFDIRSCRIPT}")

                from( project.provider {
                    if(image.getOrElse("").isEmpty()) {
                        throw GradleException("It is necessary to provide an ICM base oder init image!")
                    }
                    Dockerfile.From(image.getOrElse(""))
                } )
                instruction("COPY --from=${BUILDSTAGE} $USERSET ${WORKDIR}/diff ${icmdir}/")

                destFile.set(project.layout.buildDirectory.file("${dirname}/Dockerfile"))

                onlyIf { checkDockerFile(imgConfiguration.dockerfile) }
            }
        }
    }

    fun checkDockerFile(dockerfile: RegularFileProperty): Boolean =
            ! dockerfile.isPresent || ! dockerfile.asFile.get().exists()

    fun getDockerfile(dockerfile: RegularFileProperty, dockerfileTask: Dockerfile): Provider<RegularFile> =
            project.provider {
                if(dockerfile.orNull != null && dockerfile.asFile.get().exists()) {
                    dockerfile.get()
                } else {
                    dockerfileTask.destFile.get()
                }
            }
}
