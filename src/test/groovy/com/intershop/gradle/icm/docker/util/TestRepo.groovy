/*
 * Copyright 2019 Intershop Communications AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.intershop.gradle.icm.docker.util

import com.intershop.gradle.test.builder.TestMavenRepoBuilder

class TestRepo {

    private File repoDir
    private String intRepoconfig

    TestRepo(File repoDirTarget) {
        repoDir = new File(repoDirTarget.absolutePath)

        if(repoDir.exists()) {
            repoDir.deleteDir()
            repoDir.mkdirs()
        }
    }

    String getRepoConfig() {
        if(! intRepoconfig) {
            createRepo(repoDir)

            String repostr = """
            repositories {
                jcenter()
                maven {
                    url "${repoDir.toURI().toURL()}"
                }
            }""".stripIndent()

            return repostr
        }
        return intRepoconfig
    }

    String getRepoKtsConfig() {
        if(! intRepoconfig) {
            createRepo(repoDir)

            String repostr = """
            repositories {
                maven {
                    url=uri("${repoDir.toURI().toURL()}")
                }
                jcenter()
            }""".stripIndent()

            return repostr
        }
        return intRepoconfig
    }

    private String createRepo(File dir) {

        dir.deleteDir()
        dir.mkdirs()

        new TestMavenRepoBuilder().repository {
            project(groupId: 'com.intershop', artifactId: 'libbom', version: '1.0.0') {
                dependencyManagement {
                    dependency groupId: 'com.other', artifactId: 'library1', version: '1.5.0'
                    dependency groupId: 'com.other', artifactId: 'library2', version: '1.5.0'
                    dependency groupId: 'com.other', artifactId: 'library3', version: '1.5.0'
                }
            }

            project(groupId: 'com.intershop', artifactId: 'library3', version: '1.0.0'){
                dependencyManagement {
                    dependency groupId: 'com.intershop', artifactId: 'libbom', version: '1.5.0'
                }
                artifact entries: [
                        TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'com/class3/test1.file', content: 'test1.file'),
                        TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'com/class3/test2.file', content: 'test2.file'),
                ]
                dependency groupId: 'com.other', artifactId: 'library2', version: '1.5.0'
                dependency groupId: 'com.intershop', artifactId: 'library2', version: '1.0.0'
            }
            project(groupId: 'com.other', artifactId: 'library4', version: '1.0.0'){
                dependencyManagement {
                    dependency groupId: 'com.intershop', artifactId: 'libbom', version: '1.5.0'
                }
                artifact entries: [
                        TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'com/class4/test1.file', content: 'test1.file'),
                        TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'com/class4/test2.file', content: 'test2.file'),
                ]
                dependency groupId: 'com.intershop', artifactId: 'library3', version: '1.0.0'
            }

        }.writeTo(repoDir)

        addCartridgeList('com.project.group', 'configuration', '1.0.0')

        addSimpleLib('library1', '1.5.0')
        addSimpleLib('library2', '1.5.0')
        addSimpleLib('library3', '1.5.0')

        addExtLib('library1', '1.0.0', 'library1', '1.5.0')
        addExtLib('library2', '1.0.0', 'library2', '1.5.0')

        addCartridge('cartridge_dev', '1.0.0', 'development')
        addCartridge('cartridge_test', '1.0.0', 'test')
        addCartridge('cartridge_adapter', '1.0.0', 'adapter')
        addCartridge('cartridge_prod', '1.0.0', 'cartridge')

        addBaseProject('icm-as', '1.0.0')
        addSecondBaseProject('com.intershop.search', 'solrcloud', '1.0.0')
        addThirdBaseProject('com.intershop.payment', 'paymenttest', '1.0.0')
        addBaseProjectWithoutLibsTxt('icm-as', '2.0.0')
        addBaseProjectWithoutSites('icm-as', '3.0.0')
    }

    private void addCartridgeList(String group, String artifactID, String version) {
        new TestMavenRepoBuilder().repository {
            project(groupId: group, artifactId: artifactID, version: version) {
                artifact (
                        classifier: "cartridgelist",
                        ext: "properties",
                        content: getTextResources('cartridgelist/repocartridgelist.properties')
                )
            }
        }.writeTo(repoDir)
    }

    private void addSimpleLib(String artifactId, String version) {
        new TestMavenRepoBuilder().repository {
            project(groupId: 'com.other', artifactId: artifactId, version: version) {
                artifact entries: [
                        TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'com/class1/test1.file', content: 'test1.file'),
                        TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'com/class1/test2.file', content: 'test2.file'),
                ]
            }
        }.writeTo(repoDir)
    }

    private void addExtLib(String artifactId, String version, String depArtifactId, String depVersion) {
        new TestMavenRepoBuilder().repository {
            project(groupId: 'com.intershop', artifactId: artifactId, version: version) {
                dependencyManagement {
                    dependency groupId: 'com.intershop', artifactId: 'libbom', version: '1.5.0'
                }
                artifact entries: [
                        TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'com/class1/test1.file', content: 'test1.file'),
                        TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'com/class1/test2.file', content: 'test2.file'),
                ]
                dependency groupId: 'com.other', artifactId: depArtifactId, version: depVersion
            }
        }.writeTo(repoDir)
    }

    private void addCartridge(String artifactId, String version, String style) {
        new TestMavenRepoBuilder().repository {
            project(groupId: 'com.intershop.cartridge', artifactId: artifactId, version: version) {
                dependencyManagement {
                    dependency groupId: 'com.intershop', artifactId: 'libbom', version: '1.5.0'
                }
                mvnProperties pairs: [ 'cartridge.name': artifactId, 'cartridge.style': style, 'cartridge.type':'external']
                artifact entries: [
                        TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'com/cclass1/test1.file', content: 'test1.file'),
                        TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'com/cclass1/test2.file', content: 'test1.file')
                ]
                artifact (
                        classifier: "staticfiles",
                        ext: "zip",
                        entries: [
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'components/test1.component', content: 'text = test1.component'),
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'localozations/cartridge_de.properties', content: 'text = test1_de'),
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'localozations/cartridge_en.properties', content: 'text = test1_en'),
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'pagecompile/default/template1.jsp', content: 'jsp content file'),
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'pagecompile/org/apache/jsp/cartridge/default/template1.jsp', content: 'jsp content file'),
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'pagecompile/org/apache/jsp/cartridge/default/template1.class', content: 'class content file'),
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'pipelines/pipeline1.pipeline', content: 'pipeline content file'),
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'pipelines/pipelines-acl.properties', content: 'test = value'),
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'templates/default/template1.isml', content: 'isml content file'),
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'webforms/webform1.properties', content: 'webbform = webform1'),
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'webforms/webform1.webform', content: 'webbform content file'),
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'cartridge.descriptor', content: 'cartridge content file')
                        ]
                )
                dependency groupId: 'com.other', artifactId: 'library2', version: '1.5.0'
                dependency groupId: 'com.other', artifactId: 'library3', version: '1.5.0'
            }
        }.writeTo(repoDir)
    }

    private void addBaseProject(String projectName, String version) {

        new TestMavenRepoBuilder().repository {
            project(groupId: 'com.intershop.icm', artifactId: projectName, version: version) {
                artifact (
                        classifier: "configuration",
                        ext: "zip",
                        entries: [
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'system-conf/apps/file.txt', content: "apps = test1.component(com.intershop.icm:${projectName}:${version})"),
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'system-conf/cluster/cartridgelist.properties', content: getTextResources('cartridgelist/cartridgelist.properties')),
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'system-conf/cluster/version.properties', content: "testprop = com.intershop.icm:${projectName}:${version}")
                        ]
                )
                artifact (
                        classifier: "sites",
                        ext: "zip",
                        entries: [
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'sites/SLDSystem/file.txt', content: "SLDSystem = test1.site (com.intershop.icm:${projectName}:${version}"),
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'sites/SMC/file.txt', content: "smc = test2.site (com.intershop.icm:${projectName}:${version}")
                        ]
                )
                artifact (
                        classifier: "libs",
                        ext: "txt",
                        content: getTextResources('liblist/liblist.txt')
                )
            }
        }.writeTo(repoDir)
    }

    private void addSecondBaseProject(String group, String projectName, String version) {

        new TestMavenRepoBuilder().repository {
            project(groupId: group, artifactId: projectName, version: version) {
                artifact (
                        classifier: "configuration",
                        ext: "zip",
                        entries: [
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'system-conf/apps/file2.txt', content: "apps = test2.component (${group}:${projectName}:${version})"),
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'system-conf/cluster/cartridgelist.properties', content: getTextResources('cartridgelist/cartridgelist.properties')),
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'system-conf/cluster/version.properties', content: "testprop = ${group}:${projectName}:${version}")
                        ]
                )
                artifact (
                        classifier: "sites",
                        ext: "zip",
                        entries: [
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'sites/customer1_site/file.txt', content: "customer1_site = test1.component (${group}:${projectName}:${version})"),
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'sites/customer2_site/file.txt', content: "customer2_site = test1.component (${group}:${projectName}:${version})")
                        ]
                )
            }
        }.writeTo(repoDir)
    }

    private void addThirdBaseProject(String group, String projectName, String version) {

        new TestMavenRepoBuilder().repository {
            project(groupId: group, artifactId: projectName, version: version) {
                artifact (
                        classifier: "configuration",
                        ext: "zip",
                        entries: [
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'system-conf/apps/paymenr.txt', content: 'payment = test2.component'),
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'system-conf/cluster/version.properties', content: "testprop = ${group}:${projectName}:${version}")
                        ]
                )
                artifact (
                        classifier: "sites",
                        ext: "zip",
                        entries: [
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'sites/payment1_site/file.txt', content: "customer1_site = test1.site ${group}:${projectName}:${version}"),
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'sites/payment2/file.txt', content: "customer2_site = test2.site ${group}:${projectName}:${version}")
                        ]
                )
            }
        }.writeTo(repoDir)
    }

    private void addBaseProjectWithoutLibsTxt(String projectName, String version) {

        new TestMavenRepoBuilder().repository {
            project(groupId: 'com.intershop.icm', artifactId: projectName, version: version) {
                artifact (
                        classifier: "configuration",
                        ext: "zip",
                        entries: [
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'system-conf/apps/file.txt', content: "apps = test1.component (com.intershop.icm:${projectName}:${version})"),
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'system-conf/cluster/cartridgelist.properties', content: getTextResources('cartridgelist/cartridgelist.properties')),
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'system-conf/cluster/version.properties', content: "testprop = com.intershop.icm:${projectName}:${version}")
                        ]
                )
                artifact (
                        classifier: "sites",
                        ext: "zip",
                        entries: [
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'sites/SLDSystem/file.txt', content: "SLDSystem = test1.site com.intershop.icm:${projectName}:${version}"),
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'sites/SMC/file.txt', content: "smc = test2.site com.intershop.icm:${projectName}:${version}")
                        ]
                )
            }
        }.writeTo(repoDir)
    }

    private void addBaseProjectWithoutSites(String projectName, String version) {

        new TestMavenRepoBuilder().repository {
            project(groupId: 'com.intershop.icm', artifactId: projectName, version: version) {
                artifact (
                        classifier: "configuration",
                        ext: "zip",
                        entries: [
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'system-conf/apps/file.txt', content: "apps = test1.component com.intershop.icm:${projectName}:${version}"),
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'system-conf/cluster/cartridgelist.properties', content: getTextResources('cartridgelist/cartridgelist.properties')),
                                TestMavenRepoBuilder.ArchiveFileEntry.newInstance(path: 'system-conf/cluster/version.properties', content: "testprop = com.intershop.icm:${projectName}:${version}")
                        ]
                )
            }
        }.writeTo(repoDir)
    }

    protected String getTextResources(String srcDir) {
        ClassLoader classLoader = getClass().getClassLoader()
        URL resource = classLoader.getResource(srcDir)
        if (resource == null) {
            throw new RuntimeException("Could not find classpath resource: $srcDir")
        }

        File resourceFile = new File(resource.toURI())
        return resourceFile.text
    }
}
