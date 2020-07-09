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
import com.intershop.gradle.icm.docker.ICMDockerPlugin.Companion.BUILD_MAIN_IMAGE
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
    }

    fun prepareImageBuilds() {
        val mainPkgTaskName = buildImages.mainImage.pkgTaskName.getOrElse("createMainPkg")
        val mainPkgTask = project.tasks.getByName(mainPkgTaskName)
        val mainBuildImageTask = project.tasks.getByName(BUILD_MAIN_IMAGE)

        if(mainPkgTask is Tar) {
            if(mainBuildImageTask is BuildImage) {
                val mainDockerFile = getBaseDockerfileTask(
                        "dockerfileMain",
                        "maindockerfile",
                        images.icmbase,
                        MAIN_ICM_DIR,
                        mainPkgTask)
                mainDockerFile.onlyIf {
                    ! buildImages.mainImage.dockerfile.isPresent ||
                        ! buildImages.mainImage.dockerfile.asFile.get().exists()
                }

                mainBuildImageTask.dockerfile.set(
                        project.provider {
                            var rv: RegularFile? = null
                            if(buildImages.initImage.dockerfile.orNull != null &&
                                    buildImages.mainImage.dockerfile.asFile.get().exists()) {
                                rv = buildImages.mainImage.dockerfile.get()
                            } else {
                                rv = mainDockerFile.destFile.get()
                            }
                            rv
                        }
                )
                mainBuildImageTask.srcFiles.from(mainPkgTask)
                mainBuildImageTask.dependsOn(mainDockerFile)
            } else {
                throw GradleException("Build image task for main image must be a BuildImage task!")
            }
        } else {
            throw GradleException("Package task for main image must be a Tar task!")
        }

        val initPkgTaskName = buildImages.initImage.pkgTaskName.getOrElse("createInitPkg")
        val initPkgTask = project.tasks.getByName(initPkgTaskName)
        val initBuildImageTask = project.tasks.getByName(BUILD_INIT_IMAGE)

        if(initPkgTask is Tar) {
            if(initBuildImageTask is BuildImage) {

                val initDockerFile = getBaseDockerfileTask(
                        "dockerfileInit",
                        "initdockerfile",
                        images.icminit,
                        INIT_ICM_DIR,
                        initPkgTask)
                initDockerFile.onlyIf {
                    ! buildImages.initImage.dockerfile.isPresent ||
                            ! buildImages.initImage.dockerfile.asFile.get().exists()
                }

                initBuildImageTask.dockerfile.set(
                        project.provider {
                            var rv: RegularFile? = null
                            if(buildImages.initImage.dockerfile.orNull != null &&
                                    buildImages.initImage.dockerfile.asFile.get().exists()) {
                                rv = buildImages.initImage.dockerfile.get()
                            } else {
                                rv = initDockerFile.destFile.get()
                            }
                            rv
                        }
                )
                initBuildImageTask.srcFiles.from(initPkgTask)
                initBuildImageTask.dependsOn(initDockerFile)
            } else {
                throw GradleException("Build image task for init image must be a BuildImage task!")
            }
        } else {
            throw GradleException("Package task for init image must be a Tar task!")
        }
    }

    private fun getBaseDockerfileTask(taskname: String,
                                      dirname: String,
                                      image: Provider<String>,
                                      icmdir: String, pkg: Tar): Dockerfile {
        return with(project) {
            tasks.maybeCreate(taskname, Dockerfile::class.java).apply {
                arg("SETUP_IMAGE")

                from(Dockerfile.From("\${SETUP_IMAGE}").withStage(PREBUILDSTAGE))
                runCommand("mkdir -p /${PRJ_DIR}/org")

                addFile(project.provider {
                    Dockerfile.File(pkg.outputs.files.first().name, "/${PRJ_DIR}/org")
                } )

                runCommand("echo '#!/bin/sh' > ${DIFFDIRSCRIPT}")
                runCommand("echo 'for i in `ls -1 ${WORKDIR}/org`' >> ${DIFFDIRSCRIPT}")
                runCommand("echo '  do' >> ${DIFFDIRSCRIPT}")
                runCommand("echo '    dirdiff -srcdir ${WORKDIR}/org/\$i -targetdir ${icmdir}/\$i" +
                        " -diffdir ${WORKDIR}/diff/' >> ${DIFFDIRSCRIPT}")
                runCommand("echo '  done' >> ${DIFFDIRSCRIPT}")

                runCommand("chmod a+x ${DIFFDIRSCRIPT}")

                from( project.provider {
                    if(image.getOrElse("").isEmpty()) {
                        throw GradleException("It is necessary to provide an ICM base oder init image!")
                    }
                    Dockerfile.From(image.getOrElse("")).withStage(BUILDSTAGE) } )
                runCommand("mkdir -p ${WORKDIR}/diff")
                instruction("COPY --from=${PREBUILDSTAGE} ${USERSET} /${PRJ_DIR}/ ${WORKDIR}/")
                runCommand("${MAIN_ICM_DIR}${DIFFDIRSCRIPT}")

                from( project.provider {
                    if(image.getOrElse("").isEmpty()) {
                        throw GradleException("It is necessary to provide an ICM base oder init image!")
                    }
                    Dockerfile.From(image.getOrElse(""))
                } )
                instruction("COPY --from=${BUILDSTAGE} ${USERSET} ${WORKDIR}/diff ${icmdir}/")

                destFile.set(project.layout.buildDirectory.file("${dirname}/Dockerfile"))

            }
        }
    }
}
