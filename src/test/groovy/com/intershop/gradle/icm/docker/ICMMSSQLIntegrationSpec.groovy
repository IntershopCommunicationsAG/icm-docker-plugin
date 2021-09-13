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

import com.intershop.gradle.icm.docker.util.TestRepo
import com.intershop.gradle.test.AbstractIntegrationGroovySpec
import static com.intershop.gradle.icm.docker.utils.Configuration.*
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class ICMMSSQLIntegrationSpec extends AbstractIntegrationGroovySpec {

    def 'startMSSQL, export database, stop, restart and restore'() {
        File prjProperties = new File(testProjectDir, "prjconfig/icm.properties")
        prjProperties.parentFile.mkdirs()
        prjProperties.createNewFile()
        prjProperties << """
        intershop.databaseType = mssql
        intershop.jdbc.url = jdbc:sqlserver://localhost:1433;databaseName=icmtestdb
        intershop.jdbc.user = intershop
        intershop.jdbc.password = intershop
        
        intershop.db.mssql.hostport = 1433
        intershop.db.container.mssql.hostport = 1433
        intershop.db.mssql.sa.password = 1ntershop5A
        intershop.db.mssql.recreatedb = false
        intershop.db.mssql.recreateuser = false
        intershop.db.mssql.dbname = icmtestdb
        """.stripIndent()

        buildFile << """
        plugins {
            id 'java'
            id 'com.intershop.gradle.icm.docker'
            id 'com.intershop.gradle.icm.mssql.backup'
        }
        
        group = "test.company"
        version = "1.0.0"
        
        tasks.register('dbPrepare') {
            doLast {
                println 'dbPrepare'
            }
        }
        
        intershop_docker {
            images {
                mssqldb = 'intershophub/mssql-intershop:2019-1.0'
            }        
        }         
        """.stripIndent()

        when:
        def resultStart = getPreparedGradleRunner()
                .withArguments("startMSSQL", "exportMSSQLDB", "-s", "-DconfigDir=${prjProperties.parent}")
                .withGradleVersion(gradleVersion)
                .build()

        then:
        resultStart.task(":startMSSQL").outcome == SUCCESS
        resultStart.task(":exportMSSQLDB").outcome == SUCCESS
        new File(testProjectDir, "build/${BACKUP_FOLDER_PATH_VALUE}/mssql-icm-export.bak").exists()

        when:
        def resultStop = getPreparedGradleRunner()
                .withArguments("stopMSSQL", "-s", "-DconfigDir=${prjProperties.parent}")
                .withGradleVersion(gradleVersion)
                .build()

        then:
        resultStop.task(":stopMSSQL").outcome == SUCCESS

        when:
        (new File(testProjectDir, "build/data_folder")).deleteDir()
        def resultRestore = getPreparedGradleRunner()
                .withArguments("startMSSQL", "restoreLocalMSSQLDB", "-s", "-DconfigDir=${prjProperties.parent}")
                .withGradleVersion(gradleVersion)
                .build()

        then:
        resultRestore.task(":startMSSQL").outcome == SUCCESS
        resultRestore.task(":restoreLocalMSSQLDB").outcome == SUCCESS

        when:
        def resultStop2 = getPreparedGradleRunner()
                .withArguments("stopMSSQL", "-s", "-DconfigDir=${prjProperties.parent}")
                .withGradleVersion(gradleVersion)
                .build()

        then:
        resultStop2.task(":stopMSSQL").outcome == SUCCESS

        where:
        gradleVersion << supportedGradleVersions
    }

    def 'startMSSQL, export datbase, publish, restart and restore from download'() {
        //TestRepo repo = new TestRepo(new File(testProjectDir, "build/pubrepo"))
        //String repoConf = repo.getRepoConfig()

        File prjProperties = new File(testProjectDir, "prjconfig/icm.properties")
        prjProperties.parentFile.mkdirs()
        prjProperties.createNewFile()
        prjProperties << """
        intershop.databaseType = mssql
        intershop.jdbc.url = jdbc:sqlserver://localhost:1433;databaseName=icmtestdb
        intershop.jdbc.user = intershop
        intershop.jdbc.password = intershop
        
        intershop.db.mssql.hostport = 1433
        intershop.db.container.mssql.hostport = 1433
        intershop.db.mssql.sa.password = 1ntershop5A
        intershop.db.mssql.recreatedb = false
        intershop.db.mssql.recreateuser = false
        intershop.db.mssql.dbname = icmtestdb
        """.stripIndent()

        settingsFile << """
            rootProject.name = "exporttest"
        """.stripIndent()
        buildFile << """
        plugins {
            id 'java'
            id 'maven-publish'
            id 'com.intershop.gradle.icm.docker'
            id 'com.intershop.gradle.icm.mssql.backup'
        }
        
        group = "test.company"
        version = "1.0.0"

        tasks.register('dbPrepare') {
            doLast {
                println 'dbPrepare'
            }
        }

        intershop_docker {
            images {
                mssqldb = 'intershophub/mssql-intershop:2019-1.0'
            }        
        }        
        
        tasks.register('zipBackup', Zip) {
            dependsOn(tasks.named("exportMSSQLDB"))
            from(layout.buildDirectory.dir("${BACKUP_FOLDER_PATH_VALUE}")) {
                include "mssql-icm-export.bak"
            }
            archiveFileName.set("mssql-db.zip")
            archiveClassifier.set("dbexport")
            destinationDirectory.set(file("\$buildDir/publish/dbexport"))
        }

        repositories {
            maven {
                url "${testProjectDir.path}/build/pubrepo/"
            }
        }

        publishing {
            publications {
                mvn(MavenPublication) {
                    artifact(tasks.named("zipBackup")) {
                        extension = "zip"
                    }
                }
            }
            repositories {
                maven {
                    // change to point to your repo, e.g. http://my.org/repo
                    url = "\$buildDir/pubrepo"
                }
            }
        }
             
        """.stripIndent()

        when:
        def resultStart = getPreparedGradleRunner()
                .withArguments("startMSSQL", "exportMSSQLDB", "publish", "-s", "-DconfigDir=${prjProperties.parent}")
                .withGradleVersion(gradleVersion)
                .build()

        then:
        resultStart.task(":startMSSQL").outcome == SUCCESS
        resultStart.task(":exportMSSQLDB").outcome == SUCCESS
        resultStart.task(":publish").outcome == SUCCESS
        new File(testProjectDir, "build/${BACKUP_FOLDER_PATH_VALUE}/mssql-icm-export.bak").exists()

        where:
        gradleVersion << supportedGradleVersions
    }

    def 'startMSSQL, restore from download'() {
        TestRepo repo = new TestRepo(new File(testProjectDir, "build/pubrepo"))
        def repoConf = repo.getDBRepoConfig()

        File prjProperties = new File(testProjectDir, "prjconfig/icm.properties")
        prjProperties.parentFile.mkdirs()
        prjProperties.createNewFile()
        prjProperties << """
        intershop.databaseType = mssql
        intershop.jdbc.url = jdbc:sqlserver://localhost:1433;databaseName=icmtestdb
        intershop.jdbc.user = intershop
        intershop.jdbc.password = intershop
        
        intershop.db.mssql.hostport = 1433
        intershop.db.container.mssql.hostport = 1433
        intershop.db.mssql.sa.password = 1ntershop5A
        intershop.db.mssql.recreatedb = false
        intershop.db.mssql.recreateuser = false
        intershop.db.mssql.dbname = icmtestdb
        """.stripIndent()

        settingsFile << """
            rootProject.name = "exporttest"
        """.stripIndent()
        buildFile << """
        plugins {
            id 'java'
            id 'com.intershop.gradle.icm.docker'
            id 'com.intershop.gradle.icm.mssql.backup'
        }
        
        group = "com.company"
        version = "1.0.0" 

        intershop_docker {
            images {
                mssqldb = 'intershophub/mssql-intershop:2019-1.0'
            }        
        }   

        $repoConf
        """.stripIndent()

        when:
        def resultRestore = getPreparedGradleRunner()
                .withArguments("startMSSQL", "downloadDB", "--dbversion=1.0.0", "restoreMSSQLDB", "-s", "-DconfigDir=${prjProperties.parent}")
                .withGradleVersion(gradleVersion)
                .build()

        then:
        resultRestore.task(":testdownload").outcome == SUCCESS

        where:
        gradleVersion << supportedGradleVersions
    }
}
