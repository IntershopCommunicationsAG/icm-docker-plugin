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

import com.intershop.gradle.icm.docker.extension.geb.GebConfiguration
import com.intershop.gradle.icm.docker.tasks.PrepareNetwork
import com.intershop.gradle.icm.docker.tasks.StartExtraContainer
import com.intershop.gradle.icm.docker.tasks.StartServerContainer
import com.intershop.gradle.icm.docker.tasks.WaitForServer
import com.intershop.gradle.icm.docker.tasks.geb.GebDriverDownload
import com.intershop.gradle.icm.docker.tasks.geb.GebTest
import com.intershop.gradle.icm.docker.utils.Configuration
import com.intershop.gradle.icm.docker.utils.Configuration.GEB_LOCAL_DRIVER
import com.intershop.gradle.icm.docker.utils.Configuration.GEB_LOCAL_ENVIRONMENT
import com.intershop.gradle.icm.docker.utils.OS
import com.intershop.gradle.icm.docker.utils.appsrv.ICMServerTaskPreparer
import com.intershop.gradle.icm.docker.utils.appsrv.ServerTaskPreparer
import com.intershop.gradle.icm.docker.utils.webserver.WATaskPreparer
import com.intershop.gradle.icm.extension.IntershopExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.UnknownTaskException
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByType
import com.intershop.gradle.icm.docker.utils.network.TaskPreparer as NetworkPreparer


class ICMGebTestPlugin : Plugin<Project> {

    companion object {
        const val USEBUILDCNR = "useBuildContainer"
        const val USELOCALSRV = "useLocalServer"
        const val SRVSTARTTASK = "serverStartTaskName"
    }
    /**
     * Main method of a plugin.
     *
     * @param project target project
     */
    override fun apply(project: Project) {
        with(project) {
            val extension = rootProject.project.extensions.getByType<IntershopExtension>()

            val waitForServer = project.tasks.register("waitForServer", WaitForServer::class.java)

            initWebServer(rootProject, waitForServer)
            initASServer(project, waitForServer)

            val gebExtension = extensions.findByType(GebConfiguration::class.java) ?:
                    extensions.create("gebConfiguration", GebConfiguration::class.java)

            plugins.apply(GroovyPlugin::class.java)

            val sourceSets = extensions.getByType(JavaPluginExtension::class.java).sourceSets

            val sourcesets = sourceSets.create("gebTest") {
                it.java.srcDirs("src/gebTest/groovy")
                it.compileClasspath = sourceSets.named("main").get().output +
                        configurations.getByName(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME)
                it.runtimeClasspath = it.output + it.compileClasspath
            }

            val networkTask = rootProject.tasks.named(NetworkPreparer.PREPARE_NETWORK, PrepareNetwork::class.java)

            val os = OS.bySystem()
            val localDriverConfig = extension.developmentConfig.getConfigProperty(GEB_LOCAL_DRIVER, "")
            val localEnvironmentConfig = extension.developmentConfig.getConfigProperty(GEB_LOCAL_ENVIRONMENT, "")

            val baseUrlConfig = extension.developmentConfig.getConfigProperty(
                Configuration.WS_SECURE_URL,
                Configuration.WS_SECURE_URL_VALUE
            )

            val gebTest = tasks.register("gebTest", GebTest::class.java) {
                it.testClassesDirs = sourcesets.output.classesDirs
                it.classpath = sourcesets.runtimeClasspath

                it.containerNetwork.set(networkTask.get().networkName)
                it.baseUrl.set(baseUrlConfig)
                if(localEnvironmentConfig.isNotBlank()) {
                    logger.quiet("Setting from config is used for Geb environment: {}", localEnvironmentConfig)
                    it.gebEnvironment.set(localEnvironmentConfig)
                } else {
                    it.gebEnvironment.set(gebExtension.gebEnvironment)
                }

                it.dependsOn(tasks.named("waitForServer"))
            }

            try {
                tasks.named("check").configure {
                    it.dependsOn(gebTest)
                }
            } catch(ex: UnknownTaskException) {
                logger.quiet("There is no check task available.")
            }

            if(localDriverConfig.isNotBlank() && os != null) {
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

                                it.baseUrl.set(baseUrlConfig)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun initWebServer(project: Project, wfs: TaskProvider<WaitForServer>) {
        with(project) {
            try {
                val startWebSrv = tasks.named(
                    "start" + WATaskPreparer.extName,
                    StartExtraContainer::class.java
                )
                wfs.configure {
                    it.probes.addAll(provider { startWebSrv.get().probes.get() })
                    it.mustRunAfter(startWebSrv)
                }
            } catch (ex: UnknownTaskException) {
               logger.info("No start task for web server found.")
            }
        }
    }

    private fun initASServer(project: Project, wfs: TaskProvider<WaitForServer>) {
        with(project) {
            try {
                val useBuildContainer = hasProperty(USEBUILDCNR) && property(USEBUILDCNR) == "true"

                val useLocalServer = hasProperty(USELOCALSRV) && property(USELOCALSRV) == "true"

                val startTaskName = if (hasProperty(SRVSTARTTASK)) {
                    property(SRVSTARTTASK).toString()
                } else {
                    "start${ServerTaskPreparer.extName}"
                }

                if (useLocalServer == true && !useBuildContainer) {

                    val startASServer = rootProject.tasks.named(startTaskName)
                    wfs.configure {
                        it.mustRunAfter(startASServer)
                    }
                } else {
                    val serverTaskName = if (useBuildContainer) {
                        "start${ICMServerTaskPreparer.extName}"
                    } else {
                        startTaskName
                    }
                    val startASServer = rootProject.tasks.named(serverTaskName, StartServerContainer::class.java)
                    wfs.configure {
                        it.probes.addAll(provider { startASServer.get().probes.get() })
                        it.mustRunAfter(startASServer)
                    }
                }
            } catch (ex: UnknownTaskException) {
                project.logger.info("No start task for appserver found.")
            }
        }
    }
}
