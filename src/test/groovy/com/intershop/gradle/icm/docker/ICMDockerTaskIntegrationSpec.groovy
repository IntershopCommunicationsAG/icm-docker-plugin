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

package com.intershop.gradle.icm.docker

import com.intershop.gradle.test.AbstractIntegrationGroovySpec

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class ICMDockerTaskIntegrationSpec extends AbstractIntegrationGroovySpec {

    final ICMGRADLEVERSION = "6.2.1"

    def 'test create volumes task'() {
        settingsFile << """
        pluginManagement {
            repositories {
        		gradlePluginPortal()
                mavenCentral()
                mavenLocal()
            }
        }
        rootProject.name='rootproject'
        """.stripIndent()

        buildFile << """
            plugins {
                id 'java'
                id 'com.intershop.gradle.icm.project' version '$ICMGRADLEVERSION'
                id 'com.intershop.gradle.icm.docker'
            }
            
            task('createVolumes', type: com.intershop.gradle.icm.docker.tasks.CreateVolumes) {
                volumeNames.set(['test1', 'test2', "webserver" ])
            }
            
            task('listVolumes', type: com.intershop.gradle.icm.docker.tasks.ListVolumes) {
                filter.set(['test*'])
            }
            
            task('listAllVolumes', type: com.intershop.gradle.icm.docker.tasks.ListVolumes) 
            
            task('removeVolumes', type: com.intershop.gradle.icm.docker.tasks.RemoveVolumes) {
                volumeNames.set(['test1', 'test2', "webserver"])
            }
            
        """.stripIndent()

        when:
        def result1 = getPreparedGradleRunner()
                .withArguments("createVolumes", "-s")
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result1.task(":createVolumes").outcome == SUCCESS

        when:
        def result2 = getPreparedGradleRunner()
                .withArguments("listVolumes", "-s")
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result2.task(":listVolumes").outcome == SUCCESS

        when:
        def result3 = getPreparedGradleRunner()
                .withArguments("removeVolumes", "-s")
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result3.task(":removeVolumes").outcome == SUCCESS

        when:
        def result4 = getPreparedGradleRunner()
                .withArguments("listAllVolumes", "-s")
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result4.task(":listAllVolumes").outcome == SUCCESS

        where:
        gradleVersion << supportedGradleVersions
    }

    def 'test webserver configuration'() {
        settingsFile << """
        pluginManagement {
            repositories {
        		gradlePluginPortal()
                mavenCentral()
                mavenLocal()
            }
        }
        rootProject.name='rootproject'
        """.stripIndent()

        buildFile << """
            plugins {
                id 'java'
                id 'com.intershop.gradle.icm.project' version '$ICMGRADLEVERSION'
                id 'com.intershop.gradle.icm.docker'
            }
            
            intershop_docker {
                images {
                    webadapter = 'intershophub/icm-webadapter:2.2.0'
                    webadapteragent = 'intershophub/icm-webadapteragent:3.1.0'
                }
            }
        """.stripIndent()

        when:
        def result1 = getPreparedGradleRunner()
                .withArguments("startWebServer", "-s")
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result1.task(":startWebServer").outcome == SUCCESS

        when:
        def result2 = getPreparedGradleRunner()
                .withArguments("containerClean", "-s")
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result2.task(":containerClean").outcome == SUCCESS

        where:
        gradleVersion << supportedGradleVersions

    }
}
