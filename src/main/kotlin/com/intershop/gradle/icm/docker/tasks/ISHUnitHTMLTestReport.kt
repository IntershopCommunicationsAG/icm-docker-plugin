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

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.withGroovyBuilder
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/**
 * Generates a HTML report from JUnit xml report files.
 */
@DisableCachingByDefault(because = "Aggregates ISHUnit results produced by a task that itself is not cacheable, so a cache hit would report stale results")
abstract class ISHUnitHTMLTestReport @Inject constructor(
        projectLayout: ProjectLayout,
        objectFactory: ObjectFactory,
) : DefaultTask() {
    companion object {
        const val TASK_NAME = "ishUnitTestReport"
        const val HTML_ANT_TESTREPORT_CONFIG = "junitXmlToHtml"
    }

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val testResultDirectory: DirectoryProperty = objectFactory.directoryProperty()

    @get:OutputDirectory
    val outputDirectory: DirectoryProperty = objectFactory.directoryProperty()

    // @Classpath is the correct normalization for a classpath: order matters, but file paths and
    // irrelevant jar-entry metadata do not.
    // NOTE: this used to be a 'by lazy' FileCollection built from project.files()/project.configurations.
    // That is resolved while Gradle snapshots the task inputs, i.e. at execution time, where accessing
    // Task.project is deprecated in Gradle 9 and fails in Gradle 10. It is now wired by the plugin at
    // configuration time instead.
    @get:Classpath
    val taskClassPath: ConfigurableFileCollection = objectFactory.fileCollection()

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

            "ishUnitReport"(
                    "todir" to outputDirectory.get().asFile.absolutePath,
                    "tofile" to "ishunit-results.xml"
            ) {
                "fileset"("dir" to testResultDirectory.get().asFile.absolutePath) {
                    "include"("name" to "**/*.xml")
                }

                "report"("format" to "frames", "todir" to outputDirectory.get().asFile.absolutePath)
            }
        }
    }
}
