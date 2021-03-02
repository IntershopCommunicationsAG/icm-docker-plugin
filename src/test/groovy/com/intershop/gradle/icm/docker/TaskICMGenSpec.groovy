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

class TaskICMGenSpec extends AbstractIntegrationGroovySpec {

    String buildfileContent =
            """
            plugins {
                id 'java'
                id 'com.intershop.gradle.icm.docker'
            }
            """.stripIndent()

    String settingsfileContent =
            """
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

    def 'oracle file creation (container)'() {
        settingsFile << settingsfileContent
        buildFile << buildfileContent

        when:
        def result1 = getPreparedGradleRunner()
                .withArguments("generateICMProps", "--db=oracle-cont", "-s")
                .withGradleVersion(gradleVersion)
                .build()
        File file = new File(testProjectDir, "build/icmproperties/icm.properties")

        then:
        result1.task(":generateICMProps").outcome == SUCCESS
        file.exists()
        file.text.contains("oracle base configuration")
        file.text.contains("jdbc:oracle:thin:@rootproject-oracle:1521:XE")

        where:
        gradleVersion << supportedGradleVersions
    }

    def 'oracle file creation (external)'() {
        settingsFile << settingsfileContent
        buildFile << buildfileContent

        when:
        def result1 = getPreparedGradleRunner()
                .withArguments("generateICMProps", "--db=oracle", "-s")
                .withGradleVersion(gradleVersion)
                .build()
        File file = new File(testProjectDir, "build/icmproperties/icm.properties")

        then:
        result1.task(":generateICMProps").outcome == SUCCESS
        file.exists()
        file.text.contains("oracle base configuration")
        file.text.contains("<database user of ext db>")

        where:
        gradleVersion << supportedGradleVersions
    }

    def 'oracle file creation (container + icmas)'() {
        settingsFile << settingsfileContent
        buildFile << buildfileContent

        when:
        def result1 = getPreparedGradleRunner()
                .withArguments("generateICMProps", "--db=oracle-cont", "--icmas", "-s")
                .withGradleVersion(gradleVersion)
                .build()
        File file = new File(testProjectDir, "build/icmproperties/icm.properties")

        then:
        result1.task(":generateICMProps").outcome == SUCCESS
        file.exists()
        file.text.contains("oracle base configuration")
        file.text.contains("intershop.jdbc.url = jdbc:oracle:thin:@localhost:1521:XE")

        where:
        gradleVersion << supportedGradleVersions
    }

    def 'oracle file creation (external + icmas)'() {
        settingsFile << settingsfileContent
        buildFile << buildfileContent

        when:
        def result1 = getPreparedGradleRunner()
                .withArguments("generateICMProps", "--db=oracle", "--icmas", "-s")
                .withGradleVersion(gradleVersion)
                .build()
        File file = new File(testProjectDir, "build/icmproperties/icm.properties")

        then:
        result1.task(":generateICMProps").outcome == SUCCESS
        file.exists()
        file.text.contains("oracle base configuration")
        file.text.contains("<database user of ext db>")

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

        where:
        gradleVersion << supportedGradleVersions
    }
}
