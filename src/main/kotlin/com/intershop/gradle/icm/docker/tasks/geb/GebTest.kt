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

package com.intershop.gradle.icm.docker.tasks.geb

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

@DisableCachingByDefault(because = "Runs UI tests against a running server - the outcome depends on external state and must never be taken from the build cache")
abstract class GebTest @Inject constructor(
    @get:Internal
    val projectLayout: ProjectLayout
) : Test () {

    // injected instead of using project.layout: accessing Task.project from a task action is
    // deprecated in Gradle 9 and fails in Gradle 10 (configuration cache incompatible)


    @get:Input
    val containerNetwork: Property<String> = objectFactory.property(String::class.java)

    @get:Input
    val baseUrl: Property<String> = objectFactory.property(String::class.java)

    @get:Input
    @get:Optional
    val browserExecutableName: Property<String> = objectFactory.property(String::class.java)

    // the *absolute* location of the browser installation is what gets passed to the driver as a
    // system property, so a relocated directory really is a different input
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    val browserExecutableDir: DirectoryProperty = objectFactory.directoryProperty()

    @get:Input
    val gebEnvironment: Property<String> = objectFactory.property(String::class.java)

    init {
        containerNetwork.convention("")
        baseUrl.convention("https://testhost.domain.com:8443")
        gebEnvironment.convention("firefoxContainer")

        description = "Runs the geb tests for an running ICM Server."
        group = "verification"
    }

    @TaskAction
    override fun executeTests() {
        if(browserExecutableName.isPresent && browserExecutableDir.isPresent) {
            val file = browserExecutableDir.file(browserExecutableName.get()).get().asFile
            systemProperty("webdriverExec", file.absolutePath)
            logger.quiet("Setting from config is used for Geb executable: {}", file.absolutePath)
        }

        systemProperty("geb.env", gebEnvironment.get())
        systemProperty("geb.build.reportsDir", projectLayout.buildDirectory.dir("geb-reports/${gebEnvironment.get()}").get().asFile.absolutePath)

        useJUnitPlatform()

        systemProperty("container.network", containerNetwork.get())
        systemProperty("geb.build.baseUrl", baseUrl.get())

        super.executeTests()
    }
}
