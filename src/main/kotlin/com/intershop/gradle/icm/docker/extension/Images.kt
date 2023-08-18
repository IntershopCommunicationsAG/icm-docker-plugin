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

    val webadapter: Property<String> = objectFactory.property(String::class.java)

    val webadapteragent: Property<String> = objectFactory.property(String::class.java)

    val solr: Property<String> = objectFactory.property(String::class.java)

    val zookeeper: Property<String> = objectFactory.property(String::class.java)

    val icmcustomizationbase: Property<String> = objectFactory.property(String::class.java)

    val mssqldb: Property<String> = objectFactory.property(String::class.java)

    val oracledb: Property<String> = objectFactory.property(String::class.java)

    val mailsrv: Property<String> = objectFactory.property(String::class.java)

    val testmailsrv: Property<String> = objectFactory.property(String::class.java)

    val nginx: Property<String> = objectFactory.property(String::class.java)

    val redis: Property<String> = objectFactory.property(String::class.java)

    init {
        icmcustomizationbase.convention("intershophub/icm-as-customization-base:latest")

        webadapter.convention("intershophub/icm-webadapter:2.1.0")
        webadapteragent.convention("intershophub/icm-webadapteragent:3.1.0")

        solr.convention("solr:latest")
        zookeeper.convention("zookeeper:latest")
        mssqldb.convention("intershophub/mssql-intershop:2019-latest")
        oracledb.convention("intershophub/oracle-intershop:latest")

        mailsrv.convention("mailhog/mailhog:latest")

        testmailsrv.convention("docker-internal.rnd.intershop.de/icm-test/iste-mail:latest")

        nginx.convention("intershophub/icm-nginx:latest")
        redis.convention("redis:latest")
    }
}
