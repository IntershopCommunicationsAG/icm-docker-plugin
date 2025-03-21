/*
 * Copyright 2022 Intershop Communications AG.
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
package com.intershop.gradle.icm.docker.utils.appsrv

import com.intershop.gradle.icm.docker.tasks.CreateASContainer
import com.intershop.gradle.icm.docker.tasks.CreateExtraContainer
import com.intershop.gradle.icm.docker.tasks.FindContainer
import com.intershop.gradle.icm.docker.tasks.PrepareNetwork
import com.intershop.gradle.icm.docker.tasks.StartMailServerContainer
import com.intershop.gradle.icm.docker.tasks.utils.ICMContainerEnvironmentBuilder
import com.intershop.gradle.icm.docker.utils.AbstractTaskPreparer
import com.intershop.gradle.icm.docker.utils.Configuration
import com.intershop.gradle.icm.docker.utils.Configuration.WA_AUTOREMOVE_CONTAINER
import com.intershop.gradle.icm.docker.utils.HostAndPort
import com.intershop.gradle.icm.docker.utils.OS
import com.intershop.gradle.icm.docker.utils.PortMapping
import com.intershop.gradle.icm.docker.utils.solrcloud.ZKPreparer
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.util.concurrent.TimeUnit

abstract class AbstractASTaskPreparer(
        project: Project,
        networkTask: Provider<PrepareNetwork>,
) : AbstractTaskPreparer(project, networkTask) {

    init {
        initBaseTasks()
    }

    /**
     * Calculates the image from [com.intershop.gradle.icm.docker.extension.DevelopmentConfiguration], property [Configuration.AS_USE_TESTIMAGE]
     */
    override fun getImage(): Provider<String> {
        if (dockerExtension.developmentConfig.getConfigProperty(
                        Configuration.AS_USE_TESTIMAGE,
                        Configuration.AS_USE_TESTIMAGE_VALUE
                ).toBoolean()) {
            return icmExtension.projectConfig.base.testImage
        }
        return icmExtension.projectConfig.base.image
    }

    override fun getUseHostUserConfigProperty(): String = Configuration.AS_USE_HOST_USER
    override fun getAutoRemoveContainerConfigProperty() : String = Configuration.AS_AUTOREMOVE_CONTAINER

    val prepareServer: TaskProvider<Task> by lazy {
        project.tasks.named(TaskPreparer.TASK_PREPARESERVER)
    }

    /**
     * Determines the port mappings using
     * [com.intershop.gradle.icm.docker.extension.DevelopmentConfiguration.asPortConfiguration]
     */
    protected fun getPortMappings(): Set<PortMapping> =
        with(dockerExtension.developmentConfig.asPortConfiguration){
            setOf( serviceConnector.get(), managementConnector.get(), debug.get(), jmx.get() )
        }

    /**
     * Registers the task that creates application server the container
     * @param findTask a [TaskProvider] pointing to the [FindContainer]-task
     * @param volumes a [Provider] for the volumes to be bound. Local directories are created on demand.
     * @return a [TaskProvider] pointing to the registered task
     * @see registerCreateContainerTask
     */
    protected fun registerCreateASContainerTask(
            findTask: TaskProvider<FindContainer>,
            volumes: Provider<Map<String, String>>,
    ): TaskProvider<CreateASContainer> {
        val env = project.provider { ICMContainerEnvironmentBuilder().withContainerName(getContainerName()).build() }
        val createTask = super.registerCreateContainerTask(findTask, CreateASContainer::class.java, volumes, env)
        createTask.configure { task ->
            task.doFirst {
                addDirectories.forEach { (_, path) ->
                    path.get().asFile.mkdirs()
                }

                prepareSitesFolder()
            }

            // ensure AS knows connection to mail server
            val mailPort = devConfig.getConfigProperty(Configuration.MAIL_SMTP_PORT)
            val mailHost = devConfig.getConfigProperty(Configuration.MAIL_SMTP_HOST)

            if (mailServerTaskProvider != null && mailPort.isEmpty() && mailHost.isEmpty()) {
                // either: take host+port form MailSrv-container
                task.withMailServer(project.provider {
                    mailServerTaskProvider!!.get().getPrimaryHostAndPort()
                })
            } else if (mailPort.isNotEmpty() && mailHost.isNotEmpty()) {
                // or: use configuration values
                task.withMailServer(project.provider {
                    HostAndPort(mailHost, mailPort.toInt())
                })
            }

            // ensure AS knows connection to Solr/ZK
            val solrCloudHostList = devConfig.getConfigProperty(Configuration.SOLR_CLOUD_HOSTLIST)
            val solrCloudIndexPrefix = devConfig.getConfigProperty(Configuration.SOLR_CLOUD_INDEXPREFIX)

            if (zkTaskProvider != null && solrCloudHostList.isEmpty()) {
                task.withSolrCloudZookeeperHostList(project.provider {
                    val containerPort = zkTaskProvider!!.get().getPortMappings().stream()
                            .filter { it.name == ZKPreparer.CONTAINER_PORTMAPPING }
                            .findFirst().get().containerPort
                    "${zkTaskProvider!!.get().containerName.get()}:${containerPort}"
                })
            } else if (solrCloudHostList.isNotEmpty()) {
                task.withSolrCloudZookeeperHostList(project.provider {
                    solrCloudHostList
                })

                if (solrCloudIndexPrefix.isNotEmpty()) {
                    task.withEnvironment(ICMContainerEnvironmentBuilder().withSolrClusterIndexPrefix(
                            project.provider { solrCloudIndexPrefix }).build())
                }
            }
        }
        return createTask
    }

    protected fun getServerVolumes(): Map<String, String> {
        val sitesFolderPath = dockerExtension.developmentConfig.getFileProperty(
            Configuration.SITES_FOLDER_PATH,
            project.layout.buildDirectory.dir("sites_folder").get().asFile).absolutePath
        project.logger.quiet("Sites folder: {}", sitesFolderPath)

        val volumes = mutableMapOf(
            sitesFolderPath
                    to "/intershop/sites",
            addDirectories.getValue(TaskPreparer.SERVERLOGS).get().asFile.absolutePath
                    to "/intershop/logs",
            addDirectories.getValue(TaskPreparer.ISHUNITOUT).get().asFile.absolutePath
                    to "/intershop/ishunitrunner/output",
            project.projectDir.absolutePath
                    to "/intershop/customizations/${dockerExtension.containerPrefix}/cartridges",
            "${dockerExtension.containerPrefix}-customizations"
                    to "/intershop/customizations"
        )
        return volumes
    }

    private val addDirectories: Map<String, Provider<Directory>> by lazy {
        mapOf(
            TaskPreparer.SERVERLOGS to project.layout.buildDirectory.dir(TaskPreparer.SERVERLOGS_PATH),
            TaskPreparer.ISHUNITOUT to project.layout.buildDirectory.dir(TaskPreparer.ISHUNITOUT_PATH)
        )
    }

    private fun prepareSitesFolder() {
        with(project) {
            val sitesFolderPath = dockerExtension.developmentConfig.getConfigProperty(
                Configuration.SITES_FOLDER_PATH, ""
            )
            val defaultSitesFolder =
                project.layout.buildDirectory.dir("sites_folder").get().asFile

            val sitesFolder : File
            if (sitesFolderPath.isEmpty()) {
                logger.warn(
                    "There is no configuration for the sites folder. Check '{}' in your icm.properties! \n" +
                            "The default '{}' value will be used!",
                    Configuration.SITES_FOLDER_PATH,
                    defaultSitesFolder.path
                )
                sitesFolder = defaultSitesFolder
            }
            else {
                sitesFolder = File(sitesFolderPath)
                logger.quiet("Using configured sites folder '{}'", sitesFolder.path)
            }

            if (sitesFolder.exists() && sitesFolder.canWrite()) {
                logger.quiet("The sites folder '{}' exists and can be used", sitesFolder.path)
            } else {
                if (sitesFolder.exists()){
                    if (!sitesFolder.canWrite()) {
                        throw GradleException("The sites folder '${sitesFolder.path}' exists, but is not writable!")
                    }
                } else {
                    logger.warn("The sites folder '{}' does not exist -> trying to create", sitesFolder.path)
                    if (!sitesFolder.mkdirs()) {
                        throw GradleException(
                            "The sites folder '${sitesFolder.path}' does not exist, but can not be " +
                                    "created!"
                        )
                    }
                    logger.quiet("Created sites folder '{}'", sitesFolder.path)
                }
            }

            // try to make sites folder accessible to other users especially 'intershop' (inside the container)
            val os = OS.bySystem()
            os?.run {
                if (this == OS.LINUX || this == OS.MAC){
                    // execute '/bin/sh -c chmod a+rwx <sitesFolder>', stdout/stderr are just redirected to gradle's
                    // stdout/stderr
                    val process = ProcessBuilder().command("/bin/sh", "-c", "chmod a+rwx '${sitesFolder.path}'").
                    inheritIO().start()
                    val isTimeout = !process.waitFor(5, TimeUnit.SECONDS)
                    if (isTimeout){
                        throw GradleException(
                            "Timed out while making the sites folder '${sitesFolder.path}' " +
                                    "accessible to everyone!"
                        )
                    }
                    val exitCode = process.exitValue()
                    if (exitCode != 0){
                        throw GradleException(
                            "Failed to make the sites folder '${sitesFolder.path}' accessible to " +
                                    "everyone (exitCode=$exitCode)!"
                        )
                    }
                }
            }
        }
    }

    private val mailServerTaskProvider: Provider<StartMailServerContainer>? by lazy {
        try {
            project.tasks.named(
                    "start${com.intershop.gradle.icm.docker.utils.mail.TaskPreparer.EXT_NAME}",
                    StartMailServerContainer::class.java
            )
        } catch (ex: UnknownTaskException) {
            project.logger.warn("MailSrv task not found")
            null
        }
    }

    private val zkTaskProvider: Provider<CreateExtraContainer>? by lazy {
        try {
            project.tasks.named(
                    "create${ZKPreparer.EXT_NAME}",
                    CreateExtraContainer::class.java
            )
        } catch (ex: UnknownTaskException) {
            project.logger.info("ZooKeeper tasks not found")
            null
        }
    }
}
