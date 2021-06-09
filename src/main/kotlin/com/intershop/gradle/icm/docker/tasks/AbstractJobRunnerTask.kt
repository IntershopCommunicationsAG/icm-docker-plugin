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

import com.intershop.gradle.icm.docker.utils.Configuration
import com.intershop.icm.jobrunner.JobRunner
import com.intershop.icm.jobrunner.configuration.Server
import com.intershop.icm.jobrunner.configuration.User
import com.intershop.icm.jobrunner.utils.JobRunnerException
import com.intershop.icm.jobrunner.utils.Protocol
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import javax.inject.Inject


abstract class AbstractJobRunnerTask @Inject constructor(objectFactory: ObjectFactory) : DefaultTask() {

    @get:Input
    val hostAddress: Property<String> = objectFactory.property(String::class.java)

    @get:Input
    val protocol: Property<String> = objectFactory.property(String::class.java)

    @get:Input
    val webServerPort: Property<String> = objectFactory.property(String::class.java)

    @get:Input
    val domain: Property<String> = objectFactory.property(String::class.java)

    @get:Input
    val servergroup: Property<String> = objectFactory.property(String::class.java)

    @get:Input
    val userName: Property<String> = objectFactory.property(String::class.java)

    @get:Input
    val userPassword: Property<String> = objectFactory.property(String::class.java)

    @get:Input
    val maxWait: Property<Long> = objectFactory.property(Long::class.java)

    @get:Input
    val sslVerification: Property<Boolean> = objectFactory.property(Boolean::class.java)

    init {
        protocol.convention("https")
        hostAddress.convention(Configuration.LOCAL_CONNECTOR_HOST_VALUE)
        webServerPort.convention(Configuration.WS_HTTPS_PORT_VALUE)
        maxWait.convention(600000)
        domain.convention("SLDSystem")
        servergroup.convention("BOS")
        sslVerification.convention(true)
    }

    private val jobRunner: JobRunner by lazy {
        val protocolObj = if(protocol.get().toLowerCase() == "https") {
            Protocol.HTTPS
        } else {
            Protocol.HTTP
        }
        val user = User(userName.get(), userPassword.get())
        val server = Server(protocolObj, hostAddress.get(), webServerPort.get())

        val runner = JobRunner(
                    server = server,
                    domain = domain.get(),
                    srvgroup = servergroup.get(),
                    user = user,
                    timeout = maxWait.get(),
                    logger = project.logger)

        if(sslVerification.get()) {
            runner.enableSSLVerification()
        }

        runner
    }

    protected fun triggerJob(jobName: String) {
        try {
            jobRunner.triggerJob(jobName)
        } catch(ex: JobRunnerException) {
            throw GradleException(ex.message?: "There was a technical problem to run '$jobName'")
        }
    }
}
