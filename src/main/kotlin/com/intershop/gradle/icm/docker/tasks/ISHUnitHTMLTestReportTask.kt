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
package com.intershop.gradle.icm.docker.tasks

import com.intershop.gradle.icm.docker.ICMDockerProjectPlugin.Companion.HTML_ANT_TESTREPORT_CONFIG
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.withGroovyBuilder
import javax.inject.Inject

/**
 * Generates a HTML report from JUnit xml report files.
 */
open class ISHUnitHTMLTestReportTask @Inject constructor(projectLayout: ProjectLayout,
                                                    objectFactory: ObjectFactory) : DefaultTask() {

    init {
        group = "icm docker project"
    }

    @get:InputDirectory
    val testResultDirectory: DirectoryProperty = objectFactory.directoryProperty()

    @get:OutputDirectory
    val outputDirectory: DirectoryProperty = objectFactory.directoryProperty()

    @get:InputFiles
    val taskClassPath : FileCollection by lazy {
        val returnFiles = project.files()
        // find files of original JASPER and Eclipse compiler
        returnFiles.from(project.configurations.findByName(HTML_ANT_TESTREPORT_CONFIG))
        returnFiles
    }

    init {
        outputDirectory.set(projectLayout.buildDirectory.dir("ishunitrunner/report"))
        testResultDirectory.set(projectLayout.buildDirectory.dir("ishunitrunner/output"))
    }

    /**
     * Executes the generation of the test report.
     */
    @TaskAction
    fun createReport() {
        ant.withGroovyBuilder {
            "taskdef"(
                "name" to "ishUnitReport",
                "classname" to "org.apache.tools.ant.taskdefs.optional.junit.XMLResultAggregator",
                "classpath" to taskClassPath.asPath)

            "ishUnitReport" (
                "todir"  to outputDirectory.get().asFile.absolutePath,
                "tofile" to "ishunit-results.xml"
            ) {
                "fileset"( "dir" to testResultDirectory.get().asFile.absolutePath) {
                        "include"("name" to "**/*.xml")
                }

                "report"("format" to "frames", "todir" to outputDirectory.get().asFile.absolutePath)
            }
        }
    }
}
