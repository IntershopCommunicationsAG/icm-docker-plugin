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

import com.bmuschko.gradle.docker.domain.ExecProbe
import com.bmuschko.gradle.docker.internal.IOUtils
import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import com.github.dockerjava.api.command.InspectExecResponse
import com.intershop.gradle.icm.docker.ICMDockerProjectPlugin.Companion.ISHUNIT_REGISTRY
import com.intershop.gradle.icm.docker.utils.ISHUnitTestRegistry
import com.intershop.gradle.icm.docker.tasks.utils.ISHUnitCallback
import com.intershop.gradle.icm.docker.tasks.utils.ISHUnitTestResult
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceRegistry
import org.gradle.api.services.internal.BuildServiceRegistryInternal
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.internal.resources.ResourceLock
import java.util.*
import java.util.concurrent.TimeUnit


/**
 * Task to run ishunit tests on a running container.
 */
open class ISHUnitTask : AbstractDockerRemoteApiTask() {

    init {
        group = "icm docker project"
    }

    /**
     * The ID or name of container used to perform operation.
     * The container for the provided ID has to be created first.
     */
    @get:Input
    val containerId: Property<String> = project.objects.property(String::class.java)

    @get:Input
    val testCartridge: Property<String> = project.objects.property(String::class.java)

    @get:Input
    val testSuite: Property<String> = project.objects.property(String::class.java)

    @Internal
    override fun getSharedResources(): List<ResourceLock> {
        val locks = ArrayList(super.getSharedResources())
        val serviceRegistry = services.get(BuildServiceRegistryInternal::class.java)
        val testResourceProvider: Provider<ISHUnitTestRegistry> = getBuildService(serviceRegistry, ISHUNIT_REGISTRY)
        val resource = serviceRegistry.forService(testResourceProvider)
        locks.add(resource.getResourceLock(1))

        return Collections.unmodifiableList(locks)
    }

    private fun <T: BuildService<*>> getBuildService(registry: BuildServiceRegistry, name: String): Provider<T> {
        val registration = registry.registrations.findByName(name)
                ?: throw GradleException ("Unable to find build service with name '$name'.")

        return registration.getService() as Provider<T>
    }

    /**
     * Executes the remote Docker command.
     */
    override fun runRemoteCommand() {
        val execCallback = createCallback()

        val cartridgeProject = project.rootProject.project(testCartridge.get())
        val buildDirName = cartridgeProject.buildDir.name

        val execCmd = dockerClient.execCreateCmd(containerId.get())
        execCmd.withAttachStderr(true)
        execCmd.withAttachStdout(true)

        execCmd.withCmd(*listOf("/intershop/bin/ishunitrunner.sh",
                buildDirName,
                testCartridge.get(),
                "-s=${testSuite.get()}").toTypedArray())
        val localExecId = execCmd.exec().id

        dockerClient.execStartCmd(localExecId).withDetach(false).exec(execCallback).awaitCompletion()

        // create progressLogger for pretty printing of terminal log progression.
        val progressLogger = IOUtils.getProgressLogger(project, this.javaClass)
        progressLogger.started()

        // if no livenessProbe defined then create a default
        val localProbe = ExecProbe(6000000, 50000)

        var localPollTime = localProbe.pollTime
        var pollTimes = 0
        var isRunning = true

        // 3.) poll for some amount of time until container is in a non-running state.
        var lastExecResponse: InspectExecResponse = dockerClient.inspectExecCmd(localExecId).exec()

        while (isRunning && localPollTime > 0) {
            pollTimes += 1

            lastExecResponse = dockerClient.inspectExecCmd(localExecId).exec()
            isRunning = lastExecResponse.isRunning

            if (isRunning) {
                val totalMillis = pollTimes * localProbe.pollInterval
                val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(totalMillis)

                progressLogger.progress("Executing for ${totalMinutes}m...")
                try {
                    localPollTime -= localProbe.pollInterval
                    Thread.sleep(localProbe.pollInterval)
                } catch (e: Exception) {
                    throw e
                }
            } else {
                break
            }
        }
        progressLogger.completed()

        val exitMsg = when (lastExecResponse.exitCodeLong) {
            0L -> ISHUnitTestResult(0L,
                    "ISHUnit ${testCartridge.get()} with ${testSuite.get()} finished successfully")
            1L -> ISHUnitTestResult(1L,
                    "ISHUnit ${testCartridge.get()} with ${testSuite.get()} run failed with failures." +
                            "Please check files in " + project.layout.buildDirectory.dir("ishunitrunner"))
            2L -> ISHUnitTestResult(2L,
                    "ISHUnit ${testCartridge.get()} with ${testSuite.get()} run failed. " +
                            "Please check your test configuration")
            else -> ISHUnitTestResult(100L,
                    "ISHUnit ${testCartridge.get()} with ${testSuite.get()} run failed with unknown result code." +
                            "Please check your test configuration")
        }
        project.logger.info(exitMsg.message)
        if(exitMsg.returnValue > 0L) {
            throw GradleException(exitMsg.message)
        }
    }

    private fun createCallback(): ISHUnitCallback {
        return ISHUnitCallback(System.out, System.err)
    }
}
