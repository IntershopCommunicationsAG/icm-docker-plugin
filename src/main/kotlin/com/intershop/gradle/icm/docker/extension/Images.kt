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
package com.intershop.gradle.icm.docker.extension

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Extension to configure images for ICM projects.
 */
open class Images @Inject constructor(objectFactory: ObjectFactory) {

    val icmsetup: Property<String> = objectFactory.property(String::class.java)

    val webadapter: Property<String> = objectFactory.property(String::class.java)

    val webadapteragent: Property<String> = objectFactory.property(String::class.java)

    val solr: Property<String> = objectFactory.property(String::class.java)

    val zookeeper: Property<String> = objectFactory.property(String::class.java)

    val icmbase: Property<String> = objectFactory.property(String::class.java)

    val icminit: Property<String> = objectFactory.property(String::class.java)

    val mssqldb: Property<String> = objectFactory.property(String::class.java)

    val oracledb: Property<String> = objectFactory.property(String::class.java)

    val mailsrv: Property<String> = objectFactory.property(String::class.java)

    val testmailsrv: Property<String> = objectFactory.property(String::class.java)

    init {
        icmbase.convention("docker.intershop.de/intershop/icm-as:latest")
        icminit.convention("docker.intershop.de/intershop/icm-as-init:latest")

        icmsetup.convention("docker.intershop.de/intershop/icm-base:latest")
        webadapter.convention("docker.intershop.de/intershop/icm-webadapter:latest")
        webadapteragent.convention("docker.intershop.de/intershop/icm-webadapteragent:latest")

        solr.convention("solr:latest")
        zookeeper.convention("zookeeper:latest")
        mssqldb.convention("mcr.microsoft.com/mssql/server:2019-latest")
        oracledb.convention("docker.intershop.de/intershop/oracle-xe-server:18.4.0")

        mailsrv.convention("mailhog/mailhog:latest")

        testmailsrv.convention("docker-internal.rnd.intershop.de/icm-test/iste-mail:latest")
    }
}
