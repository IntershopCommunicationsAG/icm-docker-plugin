import org.asciidoctor.gradle.jvm.AsciidoctorTask
import io.gitee.pkmer.enums.PublishingType

/*
 * Copyright 2020 Intershop Communications AG.
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

plugins {

    // project plugins
    `java-gradle-plugin`
    groovy

    kotlin("jvm") version "2.2.20"

    // test coverage
    jacoco

    // ide plugin
    idea

    // publish plugin
    `maven-publish`

    // artifact signing - necessary on Maven Central
    signing

    // plugin for documentation
    id("org.asciidoctor.jvm.convert") version "4.0.5"

    // documentation
    id("org.jetbrains.dokka-javadoc") version "2.0.0"

    // plugin for publishing to Gradle Portal
    id("com.gradle.plugin-publish") version "2.0.0"

    id("io.gitee.pkmer.pkmerboot-central-publisher") version "1.1.1"
}

group = "com.intershop.gradle.icm.docker"
description = "Intershop Commerce Management Plugins for Docker Integration"
// apply gradle property 'projectVersion' to project.version, default to 'LOCAL'
val projectVersion : String? by project
version = projectVersion ?: "LOCAL"

val sonatypeUsername: String? by project
val sonatypePassword: String? by project

repositories {
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
}

val pluginUrl = "https://github.com/IntershopCommunicationsAG/${project.name}"
val pluginTags = listOf("intershop", "build", "icm", "docker")
gradlePlugin {
    website = pluginUrl
    vcsUrl = pluginUrl
    plugins {

        create("icmDockerPlugin") {
            id = "com.intershop.gradle.icm.docker"
            implementationClass = "com.intershop.gradle.icm.docker.ICMDockerPlugin"
            displayName = "icm-docker-plugin"
            description = "This ICM plugin contains Docker ICM integration."
            tags = pluginTags
        }
        create("icmDockerTestProjectPlugin") {
            id = "com.intershop.gradle.icm.docker.test"
            implementationClass = "com.intershop.gradle.icm.docker.ICMTestDockerPlugin"
            displayName = "icm-docker-test-plugin"
            description = "This ICM plugin contains special Docker tasks for special test container."
            tags = pluginTags
        }
        create("icmDockerReadmePlugin") {
            id = "com.intershop.gradle.icm.docker.readmepush"
            implementationClass = "com.intershop.gradle.icm.docker.ICMDockerReadmePushPlugin"
            displayName = "icm-readmepush-plugin"
            description = "This ICM plugin integrates tasks to readme files to Dockerhub."
            tags = pluginTags
        }
        create("icmDockerCustomizationPlugin") {
            id = "com.intershop.gradle.icm.docker.customization"
            implementationClass = "com.intershop.gradle.icm.docker.ICMDockerCustomizationPlugin"
            displayName = "icm-docker-customization-plugin"
            description = "This ICM plugin integrate Docker tasks to an ICM customization project."
            tags = pluginTags
        }
        create("icmSolrCloudPlugin") {
            id = "com.intershop.gradle.icm.docker.solrcloud"
            implementationClass = "com.intershop.gradle.icm.docker.ICMSolrCloudPlugin"
            displayName = "icm-solrlcloud-plugin"
            description = "This ICM plugin integrates tasks to maintain a ICM project."
            tags = pluginTags
        }
        create("icmGebTestPlugin") {
            id = "com.intershop.gradle.icm.docker.gebtest"
            implementationClass = "com.intershop.gradle.icm.docker.ICMGebTestPlugin"
            displayName = "icm-gebtest-plugin"
            description = "This ICM plugin integrates tasks to handle Geb Tests in a ICM project."
            tags = pluginTags
        }
    }
}
java {
    withJavadocJar()
    withSourcesJar()
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// set correct project status
if (project.version.toString().endsWith("-SNAPSHOT")) {
    status = "snapshot"
}

testing {
    suites.withType<JvmTestSuite> {
        useSpock()
        dependencies {
            implementation("com.intershop.gradle.test:test-gradle-plugin:6.0.0")
            implementation(gradleTestKit())
        }

        targets {
            all {
                testTask.configure {
                    systemProperty("intershop.gradle.versions", "8.5,8.10.2,9.1.0")
                    testLogging {
                        showStandardStreams = true
                    }
                }
            }
        }
    }
}

tasks {
    val copyAsciiDocTask = register<Copy>("copyAsciiDoc") {
        includeEmptyDirs = false

        val outputDir = project.layout.buildDirectory.dir("tmp/asciidoctorSrc")
        val inputFiles = fileTree(rootDir) {
            include("**/*.asciidoc")
            exclude("build/**")
        }

        inputs.files.plus( inputFiles )
        outputs.dir( outputDir )

        doFirst {
            outputDir.get().asFile.mkdir()
        }

        from(inputFiles)
        into(outputDir)
    }

    withType<AsciidoctorTask> {
        dependsOn(copyAsciiDocTask)
        sourceDirProperty.set(project.provider<Directory>{
            val dir = project.objects.directoryProperty()
            dir.set(copyAsciiDocTask.get().outputs.files.first())
            dir.get()
        })
        sources {
            include("README.asciidoc")
        }

        outputOptions {
            setBackends(listOf("html5", "docbook"))
        }

        setOptions(mapOf(
            "doctype"               to "article",
            "ruby"                  to "erubis"
        ))
        setAttributes(mapOf(
            "latestRevision"        to project.version,
            "toc"                   to "left",
            "toclevels"             to "2",
            "source-highlighter"    to "coderay",
            "icons"                 to "font",
            "setanchors"            to "true",
            "idprefix"              to "asciidoc",
            "idseparator"           to "-",
            "docinfo1"              to "true"
        ))
    }

    withType<JacocoReport> {
        reports {
            xml.required.set(true)
            html.required.set(true)

            html.outputLocation.set( project.layout.buildDirectory.dir("jacocoHtml"))
        }

        dependsOn(test)
    }
    
    jar.configure {
        dependsOn(asciidoctor)
    }

    withType<Sign> {
        val sign = this
        withType<PublishToMavenLocal> {
            this.dependsOn(sign)
        }
        withType<PublishToMavenRepository> {
            this.dependsOn(sign)
        }
    }

    afterEvaluate {
        getByName<Jar>("javadocJar") {
            dependsOn(dokkaGenerate)
            from(dokkaGeneratePublicationJavadoc)
        }
    }
}

val stagingRepoDir = project.layout.buildDirectory.dir("stagingRepo")

publishing {
    publications {
        create("intershopMvn", MavenPublication::class.java) {
            from(components["java"])

            artifact(project.layout.buildDirectory.file("docs/asciidoc/html5/README.html")) {
                classifier = "reference"
            }

            artifact(project.layout.buildDirectory.file("docs/asciidoc/docbook/README.xml")) {
                classifier = "docbook"
            }
        }
        withType<MavenPublication>().configureEach {
            pom {
                name.set(project.name)
                description.set(project.description)
                url.set(pluginUrl)
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }
                organization {
                    name.set("Intershop Communications AG")
                    url.set("http://intershop.com")
                }
                developers {
                    developer {
                        id.set("m-raab")
                        name.set("M. Raab")
                        email.set("mraab@intershop.de")
                    }
                }
                scm {
                    connection.set("git@github.com:IntershopCommunicationsAG/${project.name}.git")
                    developerConnection.set("git@github.com:IntershopCommunicationsAG/${project.name}.git")
                    url.set(pluginUrl)
                }
            }
        }
    }
    repositories {
        maven {
            name = "LOCAL"
            url = stagingRepoDir.get().asFile.toURI()
        }
    }
}

pkmerBoot {
    sonatypeMavenCentral{
        // the same with publishing.repositories.maven.url in the configuration.
        stagingRepository = stagingRepoDir

        /**
         * get username and password from
         * <a href="https://central.sonatype.com/account"> central sonatype account</a>
         */
        username = sonatypeUsername
        password = sonatypePassword

        // Optional the publishingType default value is PublishingType.AUTOMATIC
        publishingType = PublishingType.USER_MANAGED
    }
}

signing {
    sign(publishing.publications["intershopMvn"])
}

dependencies {
    implementation(gradleApi())
    implementation(gradleKotlinDsl())

    implementation("org.apache.solr:solr-solrj:9.9.0")
    implementation("com.bmuschko.docker-remote-api:com.bmuschko.docker-remote-api.gradle.plugin:9.4.0")
    implementation("com.intershop.gradle.icm:icm-gradle-plugin:7.0.0")
    implementation("com.intershop.gradle.jobrunner:icmjobrunner:7.0.0")
}

