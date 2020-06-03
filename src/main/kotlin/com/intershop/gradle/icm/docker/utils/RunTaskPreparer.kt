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
import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.tasks.DBInitTask
import com.intershop.gradle.icm.docker.tasks.ISHUnitHTMLTestReportTask
import com.intershop.gradle.icm.docker.tasks.ISHUnitTask
import org.gradle.api.Project

open class RunTaskPreparer(val project: Project, val dockerExtension: IntershopDockerExtension) {

    companion object {
        const val TASK_DBINIT = "dbinit"
        const val TASK_ISHUNIT = "ishunit"
        const val TASK_ISHUNIT_REPORT = "ishunitReport"
    }

    fun getDBInitTask(containertask: DockerCreateContainer): DBInitTask {
        return with(project) {
            tasks.maybeCreate(TASK_DBINIT, DBInitTask::class.java).apply {
                containerId.set(containertask.containerId)
            }
        }
    }

    fun getISHUnitTask(containertask: DockerCreateContainer): ISHUnitTask {
        return with(project) {
            tasks.maybeCreate(TASK_ISHUNIT, ISHUnitTask::class.java).apply {
                containerId.set(containertask.containerId)
                testConfigSet.set(dockerExtension.ishUnitTest.testConfigSet)
            }
        }
    }

    fun getISHUnitHTMLTestReportTask(): ISHUnitHTMLTestReportTask {
        return with(project) {
            tasks.maybeCreate(TASK_ISHUNIT_REPORT, ISHUnitHTMLTestReportTask::class.java)
        }
    }
}
