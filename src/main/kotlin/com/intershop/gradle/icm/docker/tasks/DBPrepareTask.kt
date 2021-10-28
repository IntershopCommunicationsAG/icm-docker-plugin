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

import com.intershop.gradle.icm.docker.tasks.utils.AdditionalICMParameters
import com.intershop.gradle.icm.docker.tasks.utils.ContainerEnvironment
import com.intershop.gradle.icm.docker.tasks.utils.DBPrepareCallback
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.options.Option
import javax.inject.Inject

/**
 * Task to run dbinit on a running container.
 */
open class DBPrepareTask
        @Inject constructor(project: Project) :
        AbstractICMASContainerTask<DBPrepareCallback, DBPrepareCallback>(project) {

    @get:Option(option = "mode", description = "Mode in which dbPrepare runs: 'init', 'migrate' or 'auto'. " +
                                               "The default is 'auto'.")
    @get:Input
    val mode: Property<String> = project.objects.property(String::class.java)

    @get:Option(option = "clean-db",
            description = "can be 'only', 'yes' or 'no', default is 'no'. In case of 'only', only the database is " +
                          "cleaned up. If 'yes' is shown, the database is cleaned up before preparing other steps. " +
                          "If 'no' is displayed, no database cleanup is done.")
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
        mode.convention("auto")
        cleanDB.convention("no")
        cartridges.convention("")
        propertyKeys.convention("")
    }

    override fun processExitCode(exitCode: Long) {
        super.processExitCode(exitCode)
        if (exitCode > 0) {
            throw GradleException("DBPrepare failed! Please check your log files")
        }
    }

    override fun createContainerEnvironment(): ContainerEnvironment {
        val ownEnv = ContainerEnvironment()
        ownEnv.add(ENV_IS_DBPREPARE, true)
        return super.createContainerEnvironment().merge(ownEnv)
    }

    override fun createAdditionalParameters(): AdditionalICMParameters {
        // add additional parameters to env
        val ownParameters = AdditionalICMParameters()
                .add("-classic")
                .add("--mode", mode)
                .add("--clean-db", cleanDB)

        if (cartridges.get().trim().isNotEmpty()) {
            ownParameters.add("--cartridges", cartridges.get().replace(" ", ""))
        }
        if (propertyKeys.get().isNotEmpty()) {
            ownParameters.add("--property-keys", propertyKeys.get().replace(" ", ""))
        }
        return super.createAdditionalParameters().merge(ownParameters)
    }

    override fun postRunRemoteCommand(dbPrepareCallback: DBPrepareCallback) {
        val info = dbPrepareCallback.getDBInfo()

        if (info == null) {
            throw GradleException("DBPrepare didn't finish correctly! Please check your log files")
        } else {
            if (info.failure > 0) {
                throw GradleException("DBPrepare failed with '" + info.failure + "' failures. " +
                                      "Please check your log files")
            }
        }
    }

    override fun createCallback(): DBPrepareCallback {
        return DBPrepareCallback(System.out, System.err)
    }

}
