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

import com.intershop.gradle.icm.docker.ICMDockerProjectPlugin.Companion.ISHUNIT_REGISTRY
import com.intershop.gradle.icm.docker.tasks.utils.AdditionalICMParameters
import com.intershop.gradle.icm.docker.tasks.utils.ContainerEnvironment
import com.intershop.gradle.icm.docker.tasks.utils.ISHUnitCallback
import com.intershop.gradle.icm.docker.tasks.utils.ISHUnitTestResult
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
        AbstractICMASContainerTask<ISHUnitCallback, ISHUnitCallback>(project) {

    companion object {
        const val ENV_CARTRIDGE_NAME = "CARTRIDGE_NAME"
    }

    init {
        group = "icm container project"
    }

    @get:Input
    val testCartridge: Property<String> = project.objects.property(String::class.java)

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
                ?: throw GradleException ("Unable to find build service with name '$name'.")

        return registration.service
    }

    override fun processExitCode(exitCode: Long) {
        super.processExitCode(exitCode)
        val exitMsg = when (exitCode) {
            0L -> ISHUnitTestResult(0L,
                    "ISHUnit ${testCartridge.get()} with ${testSuite.get()} finished successfully")
            1L -> ISHUnitTestResult(1L,
                    "ISHUnit ${testCartridge.get()} with ${testSuite.get()} run failed with failures." +
                    "Please check files in " + project.layout.buildDirectory.dir("ishunitrunner").get().asFile)
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

    override fun createCartridgeList(): Provider<Set<String>> = project.provider {
        // use normal cartridge list plus testCartridge
        super.createCartridgeList().get().plus(testCartridge.get())
    }

    override fun createContainerEnvironment(): ContainerEnvironment {
        val ownEnv = ContainerEnvironment()
        // start IshTestrunner instead of ICM-AS
        ownEnv.add(ENV_MAIN_CLASS, "com.intershop.testrunner.IshTestrunner")
        // required by classloader, can be removed when
        // https://dev.azure.com/intershop-com/Products/_git/icm-as/pullrequest/339 is merged
        ownEnv.add(ENV_ADDITIONAL_VM_PARAMETERS, "-DtestMode=true")
        // indirectly required by EmbeddedServerRule
        ownEnv.add(ENV_CARTRIDGE_NAME, testCartridge.get())

        return super.createContainerEnvironment().merge(ownEnv)
    }

    override fun createAdditionalParameters(): AdditionalICMParameters {
        val ownParameters = AdditionalICMParameters()
                .add("-o", "/intershop/ishunitrunner/output/${testSuite.get()}")
                .add("-s", testSuite.get())

        return super.createAdditionalParameters().merge(ownParameters)
    }

    override fun createCallback(): ISHUnitCallback {
        return ISHUnitCallback(System.out, System.err)
    }

}
