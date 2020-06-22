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
package com.intershop.gradle.icm.docker.utils

import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import com.intershop.gradle.icm.docker.tasks.DBPrepareTask
import com.intershop.gradle.icm.docker.tasks.ISHUnitHTMLTestReportTask
import org.gradle.api.Project

/**
 * Provides methods to configure run tasks.
 */
open class RunTaskPreparer(val project: Project) {

    companion object {
        const val TASK_DBPREPARE = "dbPrepare"
        const val TASK_ISHUNIT_REPORT = "ishUnitTestReport"
    }

    /**
     * Return a configured dbinit task.
     *
     * @param containertask task that creates the container.
     */
    fun getDBPrepareTask(containertask: DockerCreateContainer): DBPrepareTask {
        return with(project) {
            tasks.maybeCreate(TASK_DBPREPARE, DBPrepareTask::class.java).apply {
                containerId.set(containertask.containerId)
            }
        }
    }

    /**
     * Returns a task to create a HTML report from tes results.
     */
    fun getISHUnitHTMLTestReportTask(): ISHUnitHTMLTestReportTask {
        return with(project) {
            tasks.maybeCreate(TASK_ISHUNIT_REPORT, ISHUnitHTMLTestReportTask::class.java)
        }
    }
}
