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
import com.intershop.gradle.icm.docker.extension.DevelopmentConfiguration
import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.tasks.utils.ContainerEnvironment
import com.intershop.gradle.icm.docker.tasks.utils.RedirectToLocalStreamsCallback
import com.intershop.gradle.icm.docker.utils.Configuration.AS_READINESS_PROBE_INTERVAL
import com.intershop.gradle.icm.docker.utils.Configuration.AS_READINESS_PROBE_INTERVAL_VALUE
import com.intershop.gradle.icm.docker.utils.Configuration.AS_READINESS_PROBE_TIMEOUT
import com.intershop.gradle.icm.docker.utils.Configuration.AS_READINESS_PROBE_TIMEOUT_VALUE
import com.intershop.gradle.icm.utils.HttpProbe
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import java.net.URI
import java.time.Duration
import javax.inject.Inject


/**
 * Task to start an ICM-AS on a running container.
 */
open class StartASTask
@Inject constructor(project: Project) :
        AbstractICMASContainerTask<RedirectToLocalStreamsCallback, RedirectToLocalStreamsCallback, Unit>(project) {

    companion object {
        const val PATTERN_READINESS_PROBE_URL = "http://localhost:%d/status/ReadinessProbe"
    }

    private val developmentConfig: DevelopmentConfiguration by lazy {
        project.extensions.getByType(IntershopDockerExtension::class.java).developmentConfig
    }

    @get:Input
    val readinessProbeInterval: Property<Duration> = project.objects.property(Duration::class.java)
            .convention(project.provider {
                Duration.ofSeconds(
                        developmentConfig.getIntProperty(AS_READINESS_PROBE_INTERVAL, AS_READINESS_PROBE_INTERVAL_VALUE)
                                .toLong()
                )
            })

    @get:Input
    val readinessProbeTimeout: Property<Duration> = project.objects.property(Duration::class.java)
            .convention(project.provider {
                Duration.ofSeconds(
                        developmentConfig.getIntProperty(AS_READINESS_PROBE_TIMEOUT, AS_READINESS_PROBE_TIMEOUT_VALUE)
                                .toLong()
                )
            })

    override fun createContainerEnvironment(): ContainerEnvironment {
        val ownEnv = ContainerEnvironment()
        ownEnv.add(ENV_IS_DBPREPARE, false)
        return super.createContainerEnvironment().merge(ownEnv)
    }

    override fun createCallback(): RedirectToLocalStreamsCallback {
        return RedirectToLocalStreamsCallback(System.out, System.err)
    }

    override fun waitForCompletion(
            resultCallbackTemplate: RedirectToLocalStreamsCallback,
            execResponse: ExecCreateCmdResponse,
    ) {
        // don't wait for callback template completion or exit instead wait for readiness probe
        val readinessProbe = createReadinessProbe()
        val success = readinessProbe.execute()
        if (success) {
            project.logger.quiet("AS now is ready to accept requests")
        } else {
            throw GradleException("AS failed to start (not ready) until timeout was reached. Check the logs and/or" +
                                  " manually check readiness using URI '${readinessProbe.request.uri()}'.")
        }
    }

    private fun createReadinessProbe(): HttpProbe {
        val probeUri = URI.create(
                PATTERN_READINESS_PROBE_URL.format(developmentConfig.asPortConfiguration.servletEngine.get().hostPort)
        )
        return HttpProbe(probeUri).withRetryInterval(readinessProbeInterval.get())
                .withRetryTimeout(readinessProbeTimeout.get())
    }
}