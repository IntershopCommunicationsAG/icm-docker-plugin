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
import spock.lang.Ignore

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@Ignore
class ICMDockerTestPluginIntegrationSpec extends AbstractIntegrationGroovySpec {

    def 'test test mailserver configuration'() {
        settingsFile << """
        rootProject.name='rootproject'
        """.stripIndent()

        buildFile << """
            plugins {
                id 'java'
                id 'com.intershop.gradle.icm.docker'
                id 'com.intershop.gradle.icm.docker.test'
            }
            
            intershop_docker {
                images {
                    testmailsrv = "docker-internal.rnd.intershop.de/icm-test/iste-mail:latest"
                }
            }
        """.stripIndent()

        when:
        def result1 = getPreparedGradleRunner()
                .withArguments("startTestMailSrv", "-s")
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result1.task(":startTestMailSrv").outcome == SUCCESS

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
