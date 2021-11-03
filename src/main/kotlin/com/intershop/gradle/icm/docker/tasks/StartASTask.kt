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
import com.intershop.gradle.icm.docker.tasks.utils.ContainerEnvironment
import com.intershop.gradle.icm.docker.tasks.utils.RedirectToLocalStreamsCallback
import org.gradle.api.Project
import javax.inject.Inject

/**
 * Task to start an ICM-AS on a running container.
 */
open class StartASTask
        @Inject constructor(project: Project) :
        AbstractICMASContainerTask<RedirectToLocalStreamsCallback, RedirectToLocalStreamsCallback, Unit>(project) {

    override fun createContainerEnvironment(): ContainerEnvironment {
        val ownEnv = ContainerEnvironment()
        ownEnv.add(ENV_IS_DBPREPARE, false)
        return super.createContainerEnvironment().merge(ownEnv)
    }

    override fun createCallback(): RedirectToLocalStreamsCallback {
        return RedirectToLocalStreamsCallback(System.out, System.err)
    }

    override fun waitForCompletion(execResponse: ExecCreateCmdResponse) {
        // check readiness probe
    }

}
