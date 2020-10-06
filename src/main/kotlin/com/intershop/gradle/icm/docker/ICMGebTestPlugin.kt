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

package com.intershop.gradle.icm.docker

import com.intershop.gradle.icm.docker.ICMDockerProjectPlugin.Companion.TASK_START_SERVER
import com.intershop.gradle.icm.docker.extension.geb.GebConfiguration
import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.tasks.geb.GebDriverDownload
import com.intershop.gradle.icm.docker.tasks.geb.GebTest
import com.intershop.gradle.icm.docker.tasks.PrepareNetwork
import com.intershop.gradle.icm.docker.tasks.StartExtraContainer
import com.intershop.gradle.icm.docker.utils.Configuration
import com.intershop.gradle.icm.docker.utils.Configuration.GEB_LOCAL_DRIVER
import com.intershop.gradle.icm.docker.utils.Configuration.GEB_LOCAL_ENVIRONMENT
import com.intershop.gradle.icm.docker.utils.OS
import com.intershop.gradle.icm.docker.utils.webserver.WATaskPreparer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.UnknownTaskException
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.kotlin.dsl.getByType
import com.intershop.gradle.icm.docker.utils.network.TaskPreparer as NetworkPreparer


class ICMGebTestPlugin : Plugin<Project> {

    /**
     * Main method of a plugin.
     *
     * @param project target project
     */
    override fun apply(project: Project) {
        with(project) {
            val extension = rootProject.project.extensions.getByType<IntershopDockerExtension>()

            val gebExtension = extensions.findByType(GebConfiguration::class.java) ?:
                    extensions.create("gebConfiguration", GebConfiguration::class.java)

            plugins.apply(GroovyPlugin::class.java)

            val sourceSets = project.convention.getPlugin(
                JavaPluginConvention::class.java
            ).sourceSets

            val sourcesets = sourceSets.create("gebTest") {
                it.java.srcDirs("src/gebTest/groovy")
                it.resources.srcDirs("src/gebTest/resources")
                it.compileClasspath = sourceSets.named("main").get().output +
                        configurations.getByName(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME)
                it.runtimeClasspath = it.output + it.compileClasspath
            }

            val startWebSrv = rootProject.tasks.named(
                "start" + WATaskPreparer.extName,
                StartExtraContainer::class.java
            )
            val startSrv = rootProject.tasks.named(TASK_START_SERVER)
            val networkTask = rootProject.tasks.named(NetworkPreparer.PREPARE_NETWORK, PrepareNetwork::class.java)

            val os = getOS()
            val localDriverConfig = extension.developmentConfig.getConfigProperty(GEB_LOCAL_DRIVER, "")
            val localEnvironmentConfig = extension.developmentConfig.getConfigProperty(GEB_LOCAL_ENVIRONMENT, "")

            val httpContainerPort = extension.developmentConfig.getConfigProperty(
                Configuration.WS_CONTAINER_HTTP_PORT,
                Configuration.WS_CONTAINER_HTTP_PORT_VALUE
            )

            val gebTest = tasks.register("gebTest", GebTest::class.java) {
                it.testClassesDirs = sourcesets.output.classesDirs
                it.classpath = sourcesets.runtimeClasspath

                it.containerNetwork.set(networkTask.get().networkName)
                it.remoteHost.set(startWebSrv.get().containerName)
                it.remotePort.set(httpContainerPort)
                if(localEnvironmentConfig.isNotBlank()) {
                    logger.quiet("Setting from config is used for Geb environment: {}", localEnvironmentConfig)
                    it.gebEnvironment.set(localEnvironmentConfig)
                } else {
                    it.gebEnvironment.set(gebExtension.gebEnvironment)
                }

                it.dependsOn(startSrv)
                it.mustRunAfter(startWebSrv)
            }

            try {
                tasks.named("check").configure {
                    it.dependsOn(gebTest)
                }
            } catch(ex: UnknownTaskException) {
                logger.quiet("There is no check task available.")
            }

            if(localDriverConfig.isNotBlank()) {

                val httpPort = extension.developmentConfig.getConfigProperty(
                    Configuration.WS_HTTP_PORT,
                    Configuration.WS_HTTP_PORT_VALUE)

                val hostName = extension.developmentConfig.getConfigProperty(
                    Configuration.LOCAL_CONNECTOR_HOST,
                    Configuration.LOCAL_CONNECTOR_HOST_VALUE)

                if( os != null) {
                    gebExtension.localDriver.all { localDriver ->
                        localDriver.osPackages.all { driverDownLoad ->
                            if (driverDownLoad.name == os.value && localDriverConfig == localDriver.name) {
                                val download =
                                    tasks.register("downloadDriver", GebDriverDownload::class.java) { task ->
                                        task.extension.set(driverDownLoad.archiveType)
                                        task.url.set(driverDownLoad.url)
                                    }

                                gebTest.configure {
                                    it.dependsOn(download)

                                    it.browserExecutableName.set(driverDownLoad.webDriverExecName)
                                    it.browserExecutableDir.set(download.get().driverDir)

                                    it.remoteHost.set(hostName)
                                    it.remotePort.set(httpPort)
                                }

                            }
                        }
                    }
                }
            }
        }
    }

    private fun getOS(): OS? {
        val os = System.getProperty("os.name").toLowerCase()

        return when {
            os.contains(OS.WINDOWS.value) -> { OS.WINDOWS }
            os.contains("nix") || os.contains("nux") || os.contains("aix") -> { OS.LINUX }
            os.contains(OS.MAC.value) -> { OS.MAC }
            else -> null
        }
    }
}
