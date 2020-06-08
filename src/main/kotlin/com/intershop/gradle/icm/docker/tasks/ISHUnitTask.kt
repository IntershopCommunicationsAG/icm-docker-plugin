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
import com.intershop.gradle.icm.docker.extension.Suite
import com.intershop.gradle.icm.docker.tasks.utils.ISHUnitCallback
import com.intershop.gradle.icm.docker.tasks.utils.ISHUnitTestResult
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.options.Option
import org.gradle.internal.logging.progress.ProgressLogger
import java.util.concurrent.TimeUnit

/**
 * Task to run ishunit tests on a running container.
 */
open class ISHUnitTask : AbstractDockerRemoteApiTask() {

    /**
     * The ID or name of container used to perform operation.
     * The container for the provided ID has to be created first.
     */
    @get:Input
    val containerId: Property<String> = project.objects.property(String::class.java)

    @get:Option(option= "testCartridge", description = "Cartridge with test suite for ISHUnit Tests")
    @get:Optional
    @get:Input
    val testCartridge: Property<String> = project.objects.property(String::class.java)

    @get:Option(option= "testSuite", description = "Test suite in cartridge for ISHUnit Tests")
    @get:Optional
    @get:Input
    val testSuite: Property<String> = project.objects.property(String::class.java)

    @get:Input
    val testConfigSet: SetProperty<Suite> = project.objects.setProperty(Suite::class.java)

    @get:Option(option = "failFast", description = "Test should fail testafter first failure.")
    @get:Input
    val failFast: Property<Boolean> = project.objects.property(Boolean::class.java)

    init {
        failFast.set(false)
    }

    /**
     * Executes the remote Docker command.
     */
    override fun runRemoteCommand() {
        val execCallback = createCallback()

        val testResults = mutableListOf<ISHUnitTestResult>()

        if(testCartridge.isPresent && testSuite.isPresent) {
            logger.quiet("Start from command line: test {} for {}", testSuite.get(), testCartridge.get())
            testResults.add(execTest(execCallback, Suite(testCartridge.get(), testSuite.get())))
        } else {
            testConfigSet.get().forEach {
                logger.quiet("Start test {} for {}", it.testSuite, it.cartridge)
                val result = execTest(execCallback, it)
                logger.quiet("Test {} for {} finished with {}",  it.testSuite, it.cartridge, result.message)
                testResults.add(result)
            }
        }

        val failures = testResults.filter { it.returnValue > 0L }
        if(failures.isNotEmpty()) {
            val message: StringBuffer = StringBuffer("ISHUnitRun fails with following exceptions:").append("\n")
            for (failure in failures) {
                message.append(failure.message).append("\n")
            }
            throw GradleException(message.toString())
        }
    }

    private fun execTest(callback: ISHUnitCallback,
                         suite: Suite): ISHUnitTestResult {

        val execCmd = dockerClient.execCreateCmd(containerId.get())

        val cartridgeProject = project.rootProject.project(suite.cartridge)
        val buildDirName = cartridgeProject.buildDir.name

        execCmd.withAttachStderr(true)
        execCmd.withAttachStdout(true)
        execCmd.withCmd(*listOf("/intershop/bin/ishunitrunner.sh",
                buildDirName,
                suite.cartridge,
                "-s=${suite.testSuite}").toTypedArray())

        val localExecId = execCmd.exec().id
        dockerClient.execStartCmd(localExecId).withDetach(false).exec(callback)

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

            if (isRunning || pollTimes == 1) {
                val totalMillis = pollTimes * localProbe.pollInterval
                val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(totalMillis)

                logger.quiet("Executing ${suite.cartridge} with ${suite.testSuite} for ${totalMinutes}m...")

                progressLogger.progress(
                        "Executing ${suite.cartridge} with ${suite.testSuite} for ${totalMinutes}m...")

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

        // if still running then throw an exception otherwise check the exitCode
        if (isRunning) {
            throw GradleException(
                    "ISHUnit ${suite.cartridge} with ${suite.testSuite} " +
                            "did not finish in a timely fashion: $localProbe")
        }

        val exitMsg = when (lastExecResponse.exitCodeLong) {
            0L -> ISHUnitTestResult(0L,
                    "ISHUnit ${suite.cartridge} with ${suite.testSuite} finished successfully")
            1L -> ISHUnitTestResult(1L,
                    "ISHUnit ${suite.cartridge} with ${suite.testSuite} run failed with failures." +
                            "Please check files in " + project.layout.buildDirectory.dir("ishunitrunner"))
            2L -> ISHUnitTestResult(2L,
                    "ISHUnit ${suite.cartridge} with ${suite.testSuite} run failed. " +
                            "Please check your test configuration")
            else -> ISHUnitTestResult(100L,
                    "ISHUnit ${suite.cartridge} with ${suite.testSuite} run failed with unknown result code." +
                            "Please check your test configuration")
        }

        if(failFast.get()) {
            throw GradleException(exitMsg.message)
        } else {
            return exitMsg
        }
    }

    private fun createCallback(): ISHUnitCallback {
        return ISHUnitCallback(System.out, System.err)
    }
}
