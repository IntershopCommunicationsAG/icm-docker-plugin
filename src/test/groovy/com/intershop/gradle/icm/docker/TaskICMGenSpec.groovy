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

import com.intershop.gradle.icm.docker.extension.DevelopmentConfiguration
import com.intershop.gradle.test.AbstractIntegrationGroovySpec
import org.gradle.wrapper.GradleUserHomeLookup
import spock.lang.Ignore

import java.nio.file.Paths

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class TaskICMGenSpec extends AbstractIntegrationGroovySpec {

    final ICMGRADLEVERSION = "6.0.1"

    String buildfileContent =
            """
            plugins {
                id 'java'
                id 'com.intershop.gradle.icm.project' version '$ICMGRADLEVERSION'
                id 'com.intershop.gradle.icm.docker'
            }
            """.stripIndent()

    String settingsfileContent =
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                    mavenLocal()
                }
            }
           rootProject.name='rootproject'
            """.stripIndent()

    def 'test simple file creation'() {
        settingsFile << settingsfileContent
        buildFile << buildfileContent

        when:
        def result1 = getPreparedGradleRunner()
                .withArguments("generateICMProps", "--icmenvops=dev", "-s")
                .withGradleVersion(gradleVersion)
                .build()
        File file = new File(testProjectDir, "build/icmproperties/icm.properties")

        then:
        result1.task(":generateICMProps").outcome == SUCCESS
        file.exists()
        file.text.contains("development properties")

        where:
        gradleVersion << supportedGradleVersions
    }

    def 'mssql file creation (container)'() {
        settingsFile << settingsfileContent
        buildFile << buildfileContent

        when:
        def result1 = getPreparedGradleRunner()
                .withArguments("generateICMProps", "--db=mssql-cont", "-s")
                .withGradleVersion(gradleVersion)
                .build()
        File file = new File(testProjectDir, "build/icmproperties/icm.properties")

        then:
        result1.task(":generateICMProps").outcome == SUCCESS
        file.exists()
        file.text.contains("mssql base configuration")
        file.text.contains("intershop.jdbc.url = jdbc:sqlserver://rootproject-mssql:1433;databaseName=icmtestdb")

        where:
        gradleVersion << supportedGradleVersions
    }

    def 'mssql file creation (external)'() {
        settingsFile << settingsfileContent
        buildFile << buildfileContent

        when:
        def result1 = getPreparedGradleRunner()
                .withArguments("generateICMProps", "--db=mssql", "-s")
                .withGradleVersion(gradleVersion)
                .build()
        File file = new File(testProjectDir, "build/icmproperties/icm.properties")

        then:
        result1.task(":generateICMProps").outcome == SUCCESS
        file.exists()
        file.text.contains("mssql base configuration")
        file.text.contains("<database user of ext db>")

        where:
        gradleVersion << supportedGradleVersions
    }

    def 'mssql file creation (container + icmas)'() {
        settingsFile << settingsfileContent
        buildFile << buildfileContent

        when:
        def result1 = getPreparedGradleRunner()
                .withArguments("generateICMProps", "--db=mssql-cont", "--icmas", "-s")
                .withGradleVersion(gradleVersion)
                .build()
        File file = new File(testProjectDir, "build/icmproperties/icm.properties")

        then:
        result1.task(":generateICMProps").outcome == SUCCESS
        file.exists()
        file.text.contains("mssql base configuration")
        file.text.contains("jdbc:sqlserver://localhost:1433;databaseName=icmtestdb")

        where:
        gradleVersion << supportedGradleVersions
    }

    def 'mssql file creation (external + icmas)'() {
        settingsFile << settingsfileContent
        buildFile << buildfileContent

        when:
        def result1 = getPreparedGradleRunner()
                .withArguments("generateICMProps", "--db=mssql", "--icmas", "-s")
                .withGradleVersion(gradleVersion)
                .build()
        File file = new File(testProjectDir, "build/icmproperties/icm.properties")

        then:
        result1.task(":generateICMProps").outcome == SUCCESS
        file.exists()
        file.text.contains("mssql base configuration")
        file.text.contains("<database user of ext db>")

        where:
        gradleVersion << supportedGradleVersions
    }

    def 'mssql, mail, solr file creation (external)'() {
        settingsFile << settingsfileContent
        buildFile << buildfileContent

        when:
        def result1 = getPreparedGradleRunner()
                .withArguments("generateICMProps", "--db=mssql", "--icmenvops=dev,mail,solr", "-s")
                .withGradleVersion(gradleVersion)
                .build()
        File file = new File(testProjectDir, "build/icmproperties/icm.properties")

        then:
        result1.task(":generateICMProps").outcome == SUCCESS
        file.exists()
        file.text.contains("mssql base configuration")
        file.text.contains("<database user of ext db>")

        where:
        gradleVersion << supportedGradleVersions
    }

    def 'mssql-container, mail, solr file creation (external)'() {
        settingsFile << settingsfileContent
        buildFile << buildfileContent

        when:
        def result1 = getPreparedGradleRunner()
                .withArguments("generateICMProps", "--db=mssql-container", "--icmenvops=dev,mail,solr", "-s")
                .withGradleVersion(gradleVersion)
                .build()
        File file = new File(testProjectDir, "build/icmproperties/icm.properties")

        then:
        result1.task(":generateICMProps").outcome == SUCCESS
        file.exists()
        file.text.contains("mssql base configuration")
        file.text.contains("intershop.jdbc.user = intershop")

        where:
        gradleVersion << supportedGradleVersions
    }

    @Ignore
    def 'start environment'() {
        settingsFile << """
        rootProject.name='rootproject'
        """.stripIndent()

        buildFile << """
            plugins {
                id 'java'
                id 'com.intershop.gradle.icm.docker'
            }
        """.stripIndent()
        when:
        def result1 = getPreparedGradleRunner()
                .withArguments("generateICMProps", "--db=mssql-container", "--icmenvops=dev,mail,solr", "-s")
                .withGradleVersion(gradleVersion)
                .build()
        File file = new File(testProjectDir, "build/icmproperties/icm.properties")

        then:
        result1.task(":generateICMProps").outcome == SUCCESS
        file.exists()
        file.text.contains("mssql base configuration")
        file.text.contains("intershop.jdbc.user = intershop")

        when:
        def path = file.parentFile.absolutePath

        def result2 = getPreparedGradleRunner()
                .withArguments("startEnv", "-PconfigDir=${path}", "-s")
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result2.task(":startEnv").outcome == SUCCESS

        when:
        def result3 = getPreparedGradleRunner()
                .withArguments("stopEnv", "-PconfigDir=${path}", "-s")
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result3.task(":stopEnv").outcome == SUCCESS

        when:
        def result4 = getPreparedGradleRunner()
                .withArguments("showICMASConfig", "-s")
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result4.task(":showICMASConfig").outcome == SUCCESS

        where:
        gradleVersion << supportedGradleVersions
    }

    def 'verify certificate path'() {
        settingsFile << settingsfileContent
        buildFile << buildfileContent

        String gradleUserHomePath = GradleUserHomeLookup.gradleUserHome().absolutePath
        String expectedCertPath = Paths.get(gradleUserHomePath, DevelopmentConfiguration.DEFAULT_CONFIG_PATH, "certs").toString()

        when:
        File file = new File(testProjectDir, "build/icmproperties/icm.properties")

        def result1 = getPreparedGradleRunner()
                .withArguments("generateICMProps", "-s")
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result1.task(":generateICMProps").outcome == SUCCESS
        file.exists()
        file.text.contains("webServer.cert.path = ${expectedCertPath}")

        where:
        gradleVersion << supportedGradleVersions
    }

    def 'verify certificate path - new config path'() {
        settingsFile << settingsfileContent
        buildFile << buildfileContent

        File targetCertPath = new File(testProjectDir, "build/targetPath/certs")
        String confPath = targetCertPath.getParentFile().absolutePath

        when:
        def result1 = getPreparedGradleRunner()
                .withArguments("generateICMProps", "-PconfigDir=${confPath}", "-s")
                .withGradleVersion(gradleVersion)
                .build()
        File file = new File(testProjectDir, "build/icmproperties/icm.properties")

        then:
        result1.task(":generateICMProps").outcome == SUCCESS
        file.exists()
        file.text.contains("webServer.cert.path = ${targetCertPath.absolutePath}")

        where:
        gradleVersion << supportedGradleVersions
    }
}
