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
import com.intershop.gradle.icm.docker.tasks.utils.DBPrepareCallback
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.options.Option
import java.lang.Thread.sleep
import java.util.concurrent.TimeUnit

/**
 * Task to run dbinit on a running container.
 */
open class DBPrepareTask: AbstractDockerRemoteApiTask() {

    /**
     * The ID or name of container used to perform operation.
     * The container for the provided ID has to be created first.
     */
    @get:Input
    val containerId: Property<String> = project.objects.property(String::class.java)

    @get:Option(option = "mode", description = "Mode in which dbprepare runs: 'init', 'migrate' or 'auto'. " +
            "The default is 'auto'.")
    @get:Input
    val mode: Property<String> = project.objects.property(String::class.java)

    @get:Option(option = "clean-db", description = "can be 'only', 'yes' or 'no', default is 'no'. In case of 'only'," +
            " only the database is cleaned up. If 'yes' is shown, the database is cleaned up before preparing other " +
            " steps. If 'no' is displayed, no database cleanup is done.")
    @get:Input
    val cleanDB: Property<String> = project.objects.property(String::class.java)

    @get:Option(option = "cartridges", description = "A comma-separated cartridge list. Executes the cartridges in " +
            "that list. This is an optional parameter.")
    @get:Input
    val cartridges: Property<String> = project.objects.property(String::class.java)

    @get:Option(option = "property-keys", description = "Comma-separated list of preparer property keys to execute. " +
            "This is an optional parameter.")
    @get:Input
    val propertyKeys: Property<String> = project.objects.property(String::class.java)

    init {
        mode.set("auto")
        cleanDB.set("no")
        cartridges.set("")
        propertyKeys.set("")

        group = "icm docker project"
    }

    /**
     * Executes the remote Docker command.
     */
    override fun runRemoteCommand() {
        val execCallback = createCallback()

        val execCmd = dockerClient.execCreateCmd(containerId.get())
        execCmd.withAttachStderr(true)
        execCmd.withAttachStdout(true)

        val command = mutableListOf<String>()
        command.addAll(listOf("/intershop/bin/intershop.sh", "dbprepare", "-classic"))
        command.add("--mode=${mode.get()}")
        command.add("--clean-db=${cleanDB.get()}")
        if(cartridges.get().trim().isNotEmpty()) {
            command.add("--cartridges=${cartridges.get().replace(" ", "")}")
        }
        if(propertyKeys.get().isNotEmpty()) {
            command.add("--property-keys=${propertyKeys.get().replace(" ", "")}")
        }

        execCmd.withCmd(*command.toTypedArray())
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
                    sleep(localProbe.pollInterval)
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
            throw GradleException("DBPrepare command did not finish in a timely fashion: $localProbe")
        }

        if(lastExecResponse.exitCodeLong > 0) {
            throw GradleException("DBPrepare failed! Please check your log files")
        }

        val info = execCallback.getDBInfo()

        if(info == null) {
            throw GradleException("DBPrepare does not finished correctly! Please check your log files")
        } else {
            if(info.failure > 0) {
                throw GradleException("DBPrepare failed with '" + info.failure + "' failures. " +
                        "Please check your log files")
            }
        }
    }

    private fun createCallback(): DBPrepareCallback {
        return DBPrepareCallback(System.out, System.err)
    }
}
