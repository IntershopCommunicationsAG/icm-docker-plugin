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

import com.github.dockerjava.api.command.ExecCreateCmdResponse
import com.intershop.gradle.icm.docker.ICMDockerProjectPlugin.Companion.ISHUNIT_REGISTRY
import com.intershop.gradle.icm.docker.tasks.utils.ISHUnitTestResult
import com.intershop.gradle.icm.docker.tasks.utils.RedirectToLocalStreamsCallback
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceRegistry
import org.gradle.api.services.internal.BuildServiceRegistryInternal
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.internal.resources.ResourceLock
import java.util.Collections
import javax.inject.Inject


/**
 * Task to run ishunit tests on a running container.
 */
open class ISHUnitTest
@Inject constructor(project: Project) :
        AbstractICMASContainerTask<RedirectToLocalStreamsCallback, RedirectToLocalStreamsCallback, Long>(project) {

    companion object {
        const val COMMAND = "/intershop/bin/ishunitrunner.sh"
    }

    init {
        group = "icm container project"
    }

    /**
     * The name of the cartridge to be tested
     */
    @get:Input
    val testCartridge: Property<String> = project.objects.property(String::class.java)

    /**
     * The name of the test suite to be executed
     */
    @get:Input
    val testSuite: Property<String> = project.objects.property(String::class.java)

    @Internal
    override fun getSharedResources(): List<ResourceLock> {
        val locks = ArrayList(super.getSharedResources())
        val serviceRegistry = services.get(BuildServiceRegistryInternal::class.java)
        val testResourceProvider = getBuildService(serviceRegistry, ISHUNIT_REGISTRY)
        val resource = serviceRegistry.forService(testResourceProvider)
        locks.add(resource.getResourceLock(1))

        return Collections.unmodifiableList(locks)
    }

    private fun getBuildService(registry: BuildServiceRegistry, name: String): Provider<out BuildService<*>> {
        val registration = registry.registrations.findByName(name)
                           ?: throw GradleException("Unable to find build service with name '$name'.")

        return registration.service
    }

    override fun processExecutionResult(executionResult: Long) {
        super.processExecutionResult(executionResult)
        val exitMsg = when (executionResult) {
            0L -> ISHUnitTestResult(0L,
                    "ISHUnit ${testCartridge.get()} with ${testSuite.get()} finished successfully")
            1L -> ISHUnitTestResult(1L,
                    "ISHUnit ${testCartridge.get()} with ${testSuite.get()} run failed with failures. " +
                    "Please check files in " + project.layout.buildDirectory.dir("ishunitrunner").get().asFile)
            2L -> ISHUnitTestResult(2L,
                    "ISHUnit ${testCartridge.get()} with ${testSuite.get()} run failed. " +
                    "Please check your test configuration")
            else -> ISHUnitTestResult(100L,
                    "ISHUnit ${testCartridge.get()} with ${testSuite.get()} run failed with unknown result " +
                    "code. Please check your test configuration")
        }

        project.logger.info(exitMsg.message)
        if (exitMsg.returnValue > 0L) {
            throw GradleException(exitMsg.message)
        }
    }

    override fun createCartridgeList(): Provider<Set<String>> = project.provider {
        // use normal cartridge list plus testCartridge
        super.createCartridgeList().get().plus(testCartridge.get())
    }

    override fun getCommand(): List<String> {
        return listOf("/bin/sh", "-c", "$COMMAND ${testCartridge.get()} ${testSuite.get()}")
    }

    override fun createCallback(): RedirectToLocalStreamsCallback {
        return RedirectToLocalStreamsCallback(System.out, System.err)
    }

    override fun waitForCompletion(
            resultCallbackTemplate: RedirectToLocalStreamsCallback,
            execResponse: ExecCreateCmdResponse,
    ): Long {
        resultCallbackTemplate.awaitCompletion()
        return waitForExit(execResponse.id)
    }
}
