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

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class ICMDockerPluginIntegegrationSpec extends AbstractIntegrationGroovySpec {

    private def prepareDefaultBuildConfig(File testProjectDir, File settingsFile, File buildFile) {
        TestRepo repo = new TestRepo(new File(testProjectDir, "/repo"))
        String repoConf = repo.getRepoConfig()

        settingsFile << """
        rootProject.name='rootproject'
        """.stripIndent()

        createLocalFile("config/base/cluster/test.properties", "test.properties = base_dir")
        createLocalFile("config/base/cluster/cartridgelist.properties", "cartridgelist = base_dir")
        createLocalFile("config/test/cluster/test.properties", "test_test = 1")
        createLocalFile("config/dev/cluster/test.properties", "dev_test = 1")
        createLocalFile("config/prod/cluster/test.properties", "test.properties = prod_dir")

        createLocalFile("sites/base/test-site1/units/test.properties", "test-site1-sites = base")
        createLocalFile("sites/base/test-site2/units/test.properties", "test-site2-sites = 2")
        createLocalFile("sites/prod/test-site1/units/test.properties", "test-site1-sites = prod")
        createLocalFile("sites/test/test-site1/units/test.properties", "test-site1-sites = test")
        createLocalFile("sites/dev/test-site1/units/test.properties", "test-site1-sites = dev")

        buildFile << """
            plugins {
                id 'java'
                id 'com.intershop.gradle.icm.project'
                id 'com.intershop.gradle.icm.docker.project'
            }
            
            group = 'com.intershop.test'
            version = '10.0.0'

            intershop {
                projectConfig {
                    
                    cartridges = [ 'com.intershop.cartridge:cartridge_test:1.0.0', 
                                   'prjCartridge_prod',
                                   'com.intershop.cartridge:cartridge_dev:1.0.0', 
                                   'com.intershop.cartridge:cartridge_adapter:1.0.0',
                                   'prjCartridge_adapter',
                                   'prjCartridge_dev',
                                   'prjCartridge_test',
                                   'com.intershop.cartridge:cartridge_prod:1.0.0' ] 

                    dbprepareCartridges = [ 'prjCartridge_prod',
                                            'prjCartridge_test' ] 

                    base {
                        dependency = "com.intershop.icm:icm-as:1.0.0"
                        platforms = [ "com.intershop:libbom:1.0.0" ]
                    }

                    modules {
                        solrExt {
                            dependency = "com.intershop.search:solrcloud:1.0.0"
                        }
                        paymentExt {
                            dependency = "com.intershop.payment:paymenttest:1.0.0"
                        }
                    }

                    serverDirConfig {
                        base {
                            sites {
                                dirs {
                                    main {
                                        dir.set(file("sites/base"))
                                        exclude("**/test-site1/units/test.properties")
                                    }
                                }
                            }
                            config {
                                dirs {
                                    main {
                                        dir.set(file("config/base"))
                                        exclude("**/cluster/test.properties")
                                    }
                                }
                                exclude("**/cluster/cartridgelist.properties")
                            }
                        }
                        prod {
                            config {
                                dirs {
                                    main {
                                        dir.set(file("config/prod"))
                                    }
                                }
                            }
                            sites {
                                dirs {
                                    main {
                                        dir.set(file("sites/prod"))
                                    }
                                }
                            }
                        }
                        test {
                            sites {
                                dirs {
                                    main {
                                        dir.set(file("sites/test"))
                                    }
                                }
                            }
                            config {
                                dirs {
                                    main {
                                        dir.set(file("config/test"))
                                    }
                                }
                            }
                        }
                        dev {
                            sites {
                                dirs {
                                    main {
                                        dir.set(file("sites/dev"))
                                    }
                                    test {
                                        dir.set(file("sites/test"))
                                        exclude("**/units/test.properties")
                                    }
                                }
                            }
                            config {
                                dirs {
                                    main {
                                        dir.set(file("config/dev"))
                                    }
                                    test {
                                        dir.set(file("config/test"))
                                        exclude("**/cluster/test.properties")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            intershop_docker {
                images {
                    icmbase = 'intershopmock/icm-as-mock:latest'
                }

                ishUnitTest {
                    test('ac_solr_cloud_test', 'tests.embedded.com.intershop.adapter.search_solr.internal.SuiteSolrCloud')
                }
            }

            ${repoConf}

        """.stripIndent()

        def prj1dir = createSubProject('prjCartridge_prod', """
        plugins {
            id 'java-library'
            id 'com.intershop.icm.cartridge.product'
        }
        
        dependencies {
            implementation 'com.google.inject:guice:4.0'
            implementation 'com.google.inject.extensions:guice-servlet:3.0'
            implementation 'javax.servlet:javax.servlet-api:3.1.0'
        
            implementation 'io.prometheus:simpleclient:0.6.0'
            implementation 'io.prometheus:simpleclient_hotspot:0.6.0'
            implementation 'io.prometheus:simpleclient_servlet:0.6.0'
        } 
        
        repositories {
            jcenter()
        }
        """.stripIndent())

        def prj2dir = createSubProject('ac_solr_cloud_test', """
        plugins {
            id 'java-library'
            id 'com.intershop.icm.cartridge.test'
        }
        
        buildDir = "target"
        
        dependencies {
            implementation 'org.codehaus.janino:janino:2.5.16'
            implementation 'org.codehaus.janino:commons-compiler:3.0.6'
            implementation 'ch.qos.logback:logback-core:1.2.3'
            implementation 'ch.qos.logback:logback-classic:1.2.3'
            implementation 'commons-collections:commons-collections:3.2.2'
        } 
        
        repositories {
            jcenter()
        }        
        """.stripIndent())

        def prj3dir = createSubProject('prjCartridge_dev', """
        plugins {
            id 'java-library'
            id 'com.intershop.icm.cartridge.development'
        }
        
        repositories {
            jcenter()
        }        
        """.stripIndent())

        def prj4dir = createSubProject('prjCartridge_adapter', """
        plugins {
            id 'java-library'
            id 'com.intershop.icm.cartridge.adapter'
        }
        
        dependencies {
            implementation 'ch.qos.logback:logback-core:1.2.3'
            implementation 'ch.qos.logback:logback-classic:1.2.3'
        } 
        
        repositories {
            jcenter()
        }        
        """.stripIndent())

        writeJavaTestClass("com.intershop.prod", prj1dir)
        writeJavaTestClass("com.intershop.test", prj2dir)
        writeJavaTestClass("com.intershop.dev", prj3dir)
        writeJavaTestClass("com.intershop.adapter", prj4dir)

        return repoConf
    }

    def 'pull image from extension'() {
        prepareDefaultBuildConfig(testProjectDir, settingsFile, buildFile)

        when:
        def result = getPreparedGradleRunner()
                .withArguments("pullImage", "-s")
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result.task(":pullImage").outcome == SUCCESS

        where:
        gradleVersion << supportedGradleVersions

    }

    def 'pull image from extension with force'() {
        prepareDefaultBuildConfig(testProjectDir, settingsFile, buildFile)

        when:
        def result = getPreparedGradleRunner()
                .withArguments("pullImage", "--forcePull", "-s")
                .withGradleVersion(gradleVersion)
                .buildAndFail()

        then:
        result.output.contains("pull access denied for intershopmock/icm-as-mock")

        where:
        gradleVersion << supportedGradleVersions
    }

    def 'pull image from extension with alt image'() {
        prepareDefaultBuildConfig(testProjectDir, settingsFile, buildFile)

        when:
        def result = getPreparedGradleRunner()
                .withArguments("pullImage", "--altImage=busybox:latest", "--forcePull", "-s")
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result.output.contains("Pulling image 'busybox:latest'")
        result.task(":pullImage").outcome == SUCCESS

        where:
        gradleVersion << supportedGradleVersions
    }

    def 'run start container'() {

        prepareDefaultBuildConfig(testProjectDir, settingsFile, buildFile)

        when:
        def result2 = getPreparedGradleRunner()
                .withArguments("startContainer", "-s")
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result2.task(":startContainer").outcome == SUCCESS

        when:
        def result3 = getPreparedGradleRunner()
                .withArguments("removeContainer", "-s")
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result3.task(":removeContainer").outcome == SUCCESS

        where:
        gradleVersion << supportedGradleVersions
    }

    def 'run dbinit'() {
        prepareDefaultBuildConfig(testProjectDir, settingsFile, buildFile)

        when:
        def result2 = getPreparedGradleRunner()
                .withArguments("dbinit", "-s")
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result2.task(":dbinit").outcome == SUCCESS

        where:
        gradleVersion << supportedGradleVersions
    }

    def 'run ishunit'() {
        prepareDefaultBuildConfig(testProjectDir, settingsFile, buildFile)

        when:
        def result2 = getPreparedGradleRunner()
                .withArguments("ishunit", "-s")
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result2.task(":ishunit").outcome == SUCCESS

        where:
        gradleVersion << supportedGradleVersions
    }

    private def createLocalFile(String path, String content) {
        def testFile = new File(testProjectDir, path)
        testFile.parentFile.mkdirs()
        testFile << content.stripIndent()
    }
}
