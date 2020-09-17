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

import com.bmuschko.gradle.docker.DockerRegistryCredentials
import com.bmuschko.gradle.docker.internal.OutputCollector
import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import com.bmuschko.gradle.docker.tasks.RegistryCredentialsAware
import com.github.dockerjava.api.command.BuildImageResultCallback
import com.github.dockerjava.api.exception.DockerException
import com.github.dockerjava.api.model.BuildResponseItem
import com.intershop.gradle.icm.docker.ICMDockerPlugin.Companion.BUILD_IMG_REGISTRY
import com.intershop.gradle.icm.docker.utils.BuildImageRegistry
import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.services.BuildServiceRegistry
import org.gradle.api.services.internal.BuildServiceRegistryInternal
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.util.ConfigureUtil
import java.io.File
import java.io.IOException
import javax.inject.Inject

open class BuildImage
        @Inject constructor(objectFactory: ObjectFactory,
                            @Internal var projectLayout: ProjectLayout,
                            @Internal var fsOps: FileSystemOperations):
        AbstractDockerRemoteApiTask(), RegistryCredentialsAware {

    private val registryCredentials: DockerRegistryCredentials =
            objectFactory.newInstance(DockerRegistryCredentials::class.java)

    /**
     * The target Docker registry credentials for usage with a task.
     */
    override fun getRegistryCredentials(): DockerRegistryCredentials {
        return registryCredentials
    }

    /**
     * Configures the target Docker registry credentials for use with a task.
     */
    override fun registryCredentials(action: Action<in DockerRegistryCredentials>?) {
        action!!.execute(registryCredentials)
    }

    /**
     * Set the credentials for the task.
     *
     * @param c closure with Docker registry credentials.
     */
    fun registryCredentials(c: Closure<DockerRegistryCredentials>) {
        ConfigureUtil.configure(c, registryCredentials)
    }

    /**
     * Additional files to build the image.
     */
    @get:InputFiles
    val srcFiles: ConfigurableFileCollection = objectFactory.fileCollection()

    /**
     * The Dockerfile to use to build the image.  If null, will use 'Dockerfile' in the
     * build context, i.e. "Dockerfile" in the srcfiles.
     */
    @get:InputFile
    @get:Optional
    val dockerfile: RegularFileProperty = project.objects.fileProperty()

    /**
     * The images including repository, image name and tag used e.g. {@code vieux/apache:2.0}.
     */
    @get:Input
    @get:Optional
    val images: SetProperty<String> = project.objects.setProperty(String::class.java)

    /**
     * When {@code true}, do not use docker cache when building the image.
     */
    @get:Input
    @get:Optional
    val noCache:Property<Boolean>  = project.objects.property(Boolean::class.java)

    /**
     * When {@code true}, remove intermediate containers after a successful build.
     */
    @get:Input
    @get:Optional
    val remove:Property<Boolean> = project.objects.property(Boolean::class.java)

    /**
     * When {@code true}, suppress the build output and print image ID on success.
     */
    @get:Input
    @get:Optional
    val quiet:Property<Boolean> = project.objects.property(Boolean::class.java)

    /**
     * When {@code true}, always attempt to pull a newer version of the image.
     */
    @get:Input
    @get:Optional
    val pull:Property<Boolean>  = project.objects.property(Boolean::class.java)

    /**
     * Labels to attach as metadata for to the image.
     */
    @get:Input
    @get:Optional
    val labels: MapProperty<String, String> = project.objects.mapProperty(String::class.java, String::class.java)

    /**
     * Networking mode for the RUN instructions during build.
     */
    @get:Input
    @get:Optional
    val network:Property<String> = project.objects.property(String::class.java)

    /**
     * Build-time variables to pass to the image build.
     */
    @get:Input
    @get:Optional
    val buildArgs:MapProperty<String, String> = project.objects.mapProperty(String::class.java, String::class.java)

    /**
     * Images to consider as cache sources.
     */
    @get:Input
    @get:Optional
    val cacheFrom:SetProperty<String> = project.objects.setProperty(String::class.java)

    /**
     * Size of {@code /dev/shm} in bytes.
     * The size must be greater than 0.
     * If omitted the system uses 64MB.
     */
    @get:Input
    @get:Optional
    val shmSize:Property<Long> = project.objects.property(Long::class.java)

    /**
     * With this parameter it is possible to build a special stage in a multi-stage Docker file.
     * <p>
     * This feature is only available for use with Docker 17.05 and higher.
     */
    @get:Input
    @get:Optional
    val target:Property<String> = project.objects.property(String::class.java)

    /**
     * Build-time additional host list to pass to the image build in the format {@code host:ip}.
     */
    @get:Input
    @get:Optional
    val extraHosts:SetProperty<String> = project.objects.setProperty(String::class.java)
    

    /**
     * Output file containing the image ID of the built image.
     * Defaults to "$buildDir/.docker/$taskpath-imageId.txt".
     * If path contains ':' it will be replaced by '_'.
     */
    @get:OutputFile
    val imageIdFile:RegularFileProperty = project.objects.fileProperty()

    /**
     * The id of the image built.
     */
    @Internal
    val imageId:Property<String> = project.objects.property(String::class.java)

    @get:Input
    val dirname: Property<String> = objectFactory.property(String::class.java)

    @get:Internal
    val enabled: Property<Boolean> = objectFactory.property(Boolean::class.java)

    init {
        images.empty()
        noCache.set(false)
        remove.set(true)
        quiet.set(false)
        pull.set(false)
        cacheFrom.empty()
        val safeTaskPath = this.path.replaceFirst("^:", "").replace(":", "_")
        imageIdFile.set(project.layout.buildDirectory.file(".docker/${safeTaskPath}-imageId.txt"))
        buildArgs.empty()

        outputs.upToDateWhen {
            val file = imageIdFile.get().asFile
            val returnValue =
                    if(file.exists()) {
                        try {
                            val fileImageId = file.readText()
                            dockerClient.inspectImageCmd(fileImageId).exec()
                            imageId.set(fileImageId)

                            true
                        } catch (e: DockerException) {
                            e.printStackTrace()
                            false
                        }
                    } else {
                        false
                    }
            returnValue
        }

        onlyIf {
            val returnValue = enabled.getOrElse(false)
            if(! returnValue) {
                project.logger.quiet("Task {} skipped, because it is not enabled.")
            }
            returnValue
        }
    }

    override fun runRemoteCommand() {
        val finalDir = dirname.getOrElse("docker")
        val imgBuildDir = projectLayout.buildDirectory.dir("buildimage/${finalDir}").get().asFile
        val defaultDockerFile = File(imgBuildDir, "Dockerfile")

        imgBuildDir.mkdirs()
        logger.quiet("Building image using context '{}'.", imgBuildDir.absolutePath)


        fsOps.copy {
            if (dockerfile.orNull != null) {
                logger.quiet("Dockerfile '{}' will be copied to working dir: {}", dockerfile.get().asFile, imgBuildDir)
                it.from(dockerfile)
            }
            it.from(srcFiles)
            it.into(imgBuildDir)
        }

        val buildImageCmd = dockerClient.buildImageCmd().withBaseDirectory(imgBuildDir)
        buildImageCmd.withDockerfile(defaultDockerFile)

        val serviceRegistry = services.get(BuildServiceRegistryInternal::class.java)
        val buildImgRegistry = getBuildService(serviceRegistry, BUILD_IMG_REGISTRY)

        if (images.orNull != null) {
            val tagListString = images.get().joinToString(separator = ",") { "'${it}'" }
            logger.quiet("Using images {}.", tagListString)
            if(buildImgRegistry != null) {
                buildImgRegistry.addImages(images.get().toList())
            } else {
                throw GradleException("Buildservice registry is not correct configured.")
            }
            buildImageCmd.withTags(images.get())
        }

        buildImageCmd.withNoCache(noCache.get())
        buildImageCmd.withRemove(remove.get())
        buildImageCmd.withQuiet(quiet.get())
        buildImageCmd.withPull(pull.get())

        if(network.isPresent && network.getOrElse("").isNotEmpty()) {
            buildImageCmd.withNetworkMode(network.get())
        }

        if(labels.get().isNotEmpty()) {
            buildImageCmd.withLabels(labels.get())
        }

        if(shmSize.isPresent) { // 0 is valid input
            buildImageCmd.withShmsize(shmSize.get())
        }

        if(target.isPresent) {
            buildImageCmd.withTarget(target.get())
        }

        val authConfigurations = registryAuthLocator.lookupAllAuthConfigs(registryCredentials)
        buildImageCmd.withBuildAuthConfigs(authConfigurations)

        if (buildArgs.get().isNotEmpty()) {
            buildArgs.get().forEach { (key, value) ->
                buildImageCmd.withBuildArg(key, value)
            }
        }

        if (cacheFrom.get().isNotEmpty()) {
            buildImageCmd.withCacheFrom(cacheFrom.get())
        }

        if (extraHosts.get().isNotEmpty()) {
            buildImageCmd.withExtraHosts(extraHosts.get())
        }

        val createdImageId = buildImageCmd.exec(createCallback(nextHandler)).awaitImageId()
        imageId.set(createdImageId)
        imageIdFile.get().asFile.writeText(createdImageId)
        logger.quiet("Created image with ID '{}'.", createdImageId)
    }

    private fun createCallback(nextHandler: Action<in BuildResponseItem>?): BuildImageResultCallback {
        if (nextHandler != null) {
            return object : BuildImageResultCallback() {
                override fun onNext(item: BuildResponseItem) {
                    try {
                        nextHandler.execute(item)
                    } catch (e: Exception) {
                        logger.error("Failed to handle build response", e)
                        return
                    }
                    super.onNext(item)
                }
            }
        }

        return object : BuildImageResultCallback() {
            val collector = OutputCollector { s -> logger.quiet(s) }

            override fun onNext(item: BuildResponseItem) {
                try {
                    val possibleStream = item.stream
                    if (possibleStream != null) {
                        collector.accept(possibleStream)
                    }
                } catch(e: Exception) {
                    logger.error("Failed to handle build response", e)
                    return
                }
                super.onNext(item)
            }

            @Throws(IOException::class)
            override fun close() {
                collector.close()
                super.close()
            }
        }
    }

    private fun getBuildService(registry: BuildServiceRegistry, name: String): BuildImageRegistry? {
        val registration = registry.registrations.findByName(name)
            ?: throw GradleException ("Unable to find build service with name '$name'.")

        val buildservice = registration.getService().get()
        return if(buildservice is BuildImageRegistry) {
            buildservice
        } else {
            null
        }
    }
}
