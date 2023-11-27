import org.asciidoctor.gradle.jvm.AsciidoctorTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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

    kotlin("jvm") version "1.9.21"

    // test coverage
    jacoco

    // ide plugin
    idea

    // publish plugin
    `maven-publish`

    // artifact signing - necessary on Maven Central
    signing

    // intershop version plugin
    id("com.intershop.gradle.scmversion") version "6.2.0"

    // plugin for documentation
    id("org.asciidoctor.jvm.convert") version "3.3.2"

    // documentation
    id("org.jetbrains.dokka") version "1.9.10"

    // code analysis for kotlin
    id("io.gitlab.arturbosch.detekt") version "1.23.4"

    // plugin for publishing to Gradle Portal
    id("com.gradle.plugin-publish") version "1.2.1"
}

scm {
    version.initialVersion = "1.0.0"
}

group = "com.intershop.gradle.icm.docker"
description = "Intershop Commerce Management Plugins for Docker Integration"
version = scm.version.version

val sonatypeUsername: String? by project
val sonatypePassword: String? by project

repositories {
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
}

gradlePlugin {
    val pluginURL = "https://github.com/IntershopCommunicationsAG/${project.name}"
    val pluginTags = listOf("intershop", "build", "icm", "docker")
    website = pluginURL
    vcsUrl = pluginURL
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
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

// set correct project status
if (project.version.toString().endsWith("-SNAPSHOT")) {
    status = "snapshot'"
}

detekt {
    source.setFrom(files("src/main/kotlin"))
    config.setFrom(files("detekt.yml"))
}

val shaded by configurations.creating
val compileOnly = configurations.getByName("compileOnly")
compileOnly.extendsFrom(shaded)
val buildDir = project.layout.buildDirectory.asFile.get()

tasks {
    withType<KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_11.toString()
        }
    }

    withType<Test>().configureEach {
        systemProperty("intershop.gradle.versions", "8.4")

        testLogging {
            showStandardStreams = true
        }
        useJUnitPlatform()

        dependsOn("jar")
    }

    register<Copy>("copyAsciiDoc") {
        includeEmptyDirs = false

        val outputDir = file("$buildDir/tmp/asciidoctorSrc")
        val inputFiles = fileTree(rootDir) {
            include("**/*.asciidoc")
            exclude("build/**")
        }

        inputs.files.plus( inputFiles )
        outputs.dir( outputDir )

        doFirst {
            outputDir.mkdir()
        }

        from(inputFiles)
        into(outputDir)
    }

    withType<AsciidoctorTask> {
        dependsOn("copyAsciiDoc")

        setSourceDir(file("$buildDir/tmp/asciidoctorSrc"))
        sources(delegateClosureOf<PatternSet> {
            include("README.asciidoc")
        })

        outputOptions {
            setBackends(listOf("html5", "docbook"))
        }

        options = mapOf( "doctype" to "article",
                "ruby"    to "erubis")
        attributes = mapOf(
                "latestRevision"        to  project.version,
                "toc"                   to "left",
                "toclevels"             to "2",
                "source-highlighter"    to "coderay",
                "icons"                 to "font",
                "setanchors"            to "true",
                "idprefix"              to "asciidoc",
                "idseparator"           to "-",
                "docinfo1"              to "true")
    }

    withType<JacocoReport> {
        reports {
            xml.required.set(true)
            html.required.set(true)

            html.outputLocation.set( File(project.layout.buildDirectory.asFile.get(), "jacocoHtml"))
        }

        val jacocoTestReport by tasks
        jacocoTestReport.dependsOn("test")
    }
    
    getByName("jar").dependsOn("asciidoctor")

    dokkaJavadoc.configure {
        outputDirectory.set(buildDir.resolve("dokka"))
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
            dependsOn(dokkaJavadoc)
            from(dokkaJavadoc)
        }
    }
}

publishing {
    publications {
        create("intershopMvn", MavenPublication::class.java) {
            from(components["java"])

            artifact(File(buildDir, "docs/asciidoc/html5/README.html")) {
                classifier = "reference"
            }

            artifact(File(buildDir, "docs/asciidoc/docbook/README.xml")) {
                classifier = "docbook"
            }

            pom {
                name.set(project.name)
                description.set(project.description)
                url.set("https://github.com/IntershopCommunicationsAG/${project.name}")
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
                    url.set("https://github.com/IntershopCommunicationsAG/${project.name}")
                }
            }
        }
    }
    repositories {
        maven {
            val releasesRepoUrl = "https://oss.sonatype.org/service/local/staging/deploy/maven2"
            val snapshotsRepoUrl = "https://oss.sonatype.org/content/repositories/snapshots"
            url = uri(if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)
            credentials {
                username = sonatypeUsername
                password = sonatypePassword
            }
        }
    }
}

signing {
    sign(publishing.publications["intershopMvn"])
}

dependencies {
    implementation(gradleApi())
    implementation(localGroovy())

    implementation(gradleKotlinDsl())

    implementation("org.apache.solr:solr-solrj:9.4.0")
    implementation("com.bmuschko:gradle-docker-plugin:8.1.0")
    implementation("com.intershop.gradle.icm:icm-gradle-plugin:5.8.0")
    implementation("com.intershop.gradle.jobrunner:icmjobrunner:1.0.5")

    testImplementation("com.intershop.gradle.test:test-gradle-plugin:4.1.2")
    testImplementation(gradleTestKit())
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.4")
}

