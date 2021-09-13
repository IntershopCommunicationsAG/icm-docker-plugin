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
    kotlin("jvm") version "1.5.21"

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
    id("org.jetbrains.dokka") version "1.4.32"

    // code analysis for kotlin
    id("io.gitlab.arturbosch.detekt") version "1.17.1"

    // plugin for publishing to Gradle Portal
    id("com.gradle.plugin-publish") version "0.15.0"
}

scm {
    version.initialVersion = "1.0.0"
}

group = "com.intershop.gradle.icm.docker"
description = "Intershop Commerce Management Plugins for Docker Integration"
version = scm.version.version

val sonatypeUsername: String by project
val sonatypePassword: String? by project

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11

    withSourcesJar()
}

// set correct project status
if (project.version.toString().endsWith("-SNAPSHOT")) {
    status = "snapshot'"
}

detekt {
    input = files("src/main/kotlin")
    config = files("detekt.yml")
}

val shaded by configurations.creating
val compileOnly = configurations.getByName("compileOnly")
compileOnly.extendsFrom(shaded)

tasks {
    withType<KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_11.toString()
        }
    }

    withType<Test>().configureEach {
        systemProperty("intershop.gradle.versions", "7.2")

        testLogging {
            showStandardStreams = true
        }
        useJUnitPlatform()

        dependsOn("jar")
    }

    val copyAsciiDoc = register<Copy>("copyAsciiDoc") {
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
        dependsOn(copyAsciiDoc)

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

            html.outputLocation.set( File(project.buildDir, "jacocoHtml"))
        }

        val jacocoTestReport by tasks
        jacocoTestReport.dependsOn("test")
    }
    
    getByName("jar").dependsOn("asciidoctor")

    dokkaJavadoc.configure {
        outputDirectory.set(buildDir.resolve("dokka"))
    }

    register<Jar>("javaDoc") {
        dependsOn(dokkaJavadoc)
        from(dokkaJavadoc)
        archiveClassifier.set("javadoc")
    }
}

gradlePlugin {
    plugins {
        create("icmDockerPlugin") {
            id = "com.intershop.gradle.icm.docker"
            implementationClass = "com.intershop.gradle.icm.docker.ICMDockerPlugin"
            displayName = "icm-docker-plugin"
            description = "This ICM plugin contains Docker ICM integration."
        }
        create("icmDockerTestProjectPlugin") {
            id = "com.intershop.gradle.icm.docker.test"
            implementationClass = "com.intershop.gradle.icm.docker.ICMDockerTestPlugin"
            displayName = "icm-docker-test-plugin"
            description = "This ICM plugin contains special Docker tasks for special test container."
        }
        create("icmDockerReadmePlugin") {
            id = "com.intershop.gradle.icm.docker.readmepush"
            implementationClass = "com.intershop.gradle.icm.docker.ICMDockerReadmePushPlugin"
            displayName = "icm-readmepush-plugin"
            description = "This ICM plugin integrates tasks to readme files to Dockerhub."
        }
        create("icmDockerProjectPlugin") {
            id = "com.intershop.gradle.icm.docker.project"
            implementationClass = "com.intershop.gradle.icm.docker.ICMDockerProjectPlugin"
            displayName = "icm-docker-project-plugin"
            description = "This ICM plugin integrate Docker tasks to an ICM project."
        }
        create("icmSolrCloudPlugin") {
            id = "com.intershop.gradle.icm.docker.solrcloud"
            implementationClass = "com.intershop.gradle.icm.docker.ICMSolrCloudPlugin"
            displayName = "icm-solrlcloud-plugin"
            description = "This ICM plugin integrates tasks to maintain a ICM project."
        }
        create("icmGebTestPlugin") {
            id = "com.intershop.gradle.icm.docker.gebtest"
            implementationClass = "com.intershop.gradle.icm.docker.ICMGebTestPlugin"
            displayName = "icm-gebtest-plugin"
            description = "This ICM plugin integrates tasks to handle Geb Tests in a ICM project."
        }
        create("mssqlPlugin") {
            id = "com.intershop.gradle.icm.mssql.backup"
            implementationClass = "com.intershop.gradle.icm.docker.ICMMSSQLBackupPlugin"
            displayName = "icm-mssql-backup-plugin"
            description = "This ICM plugin integrates tasks to import or export MSSQL database export files."
        }
    }
}

pluginBundle {
    val pluginURL = "https://github.com/IntershopCommunicationsAG/${project.name}"
    website = pluginURL
    vcsUrl = pluginURL
    tags = listOf("intershop", "gradle", "plugin", "build", "icm", "docker")
}

publishing {
    publications {
        create("intershopMvn", MavenPublication::class.java) {
            from(components["java"])

            artifact(tasks.getByName("javaDoc"))

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

    implementation("com.microsoft.sqlserver:mssql-jdbc:8.1.1.jre11-preview")

    implementation("com.bmuschko:gradle-docker-plugin:7.1.0")
    implementation("org.apache.solr:solr-solrj:8.4.1")
    implementation("com.intershop.gradle.jobrunner:icmjobrunner:1.0.5")

    testImplementation("com.intershop.gradle.icm:icm-gradle-plugin:4.3.0")
    testImplementation("com.intershop.gradle.test:test-gradle-plugin:4.1.1")
    testImplementation(gradleTestKit())
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://plugins.gradle.org/m2/")
    }
}