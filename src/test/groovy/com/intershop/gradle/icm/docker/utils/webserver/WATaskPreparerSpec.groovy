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

package com.intershop.gradle.icm.docker.utils.webserver

import com.intershop.gradle.icm.docker.utils.PortMapping
import spock.lang.Specification

class WATaskPreparerSpec extends Specification {

    def 'needsNetBindCapability is true when any container port is privileged'() {
        expect:
        WATaskPreparer.needsNetBindCapability(httpPort, httpsPort) == expected

        where:
        httpPort | httpsPort || expected
        8080     | 8443      || false     // both default, non-privileged
        80       | 8443      || true      // http privileged
        8080     | 443       || true      // https privileged
        80       | 443       || true      // both privileged
        1024     | 1024      || false     // boundary: 1024 is not privileged
        1023     | 8443      || true      // boundary: 1023 is privileged
    }

    def 'portMismatchWarning returns null when host and container port are equal'() {
        given:
        def mapping = new PortMapping('https', 8443, 8443, true)

        expect:
        WATaskPreparer.portMismatchWarning(mapping, 'webserver.https.port', 'webserver.container.https.port', 443) == null
    }

    def 'portMismatchWarning returns null for a non-default host port because clients send it in the Host header'() {
        given:
        def mapping = new PortMapping('https', hostPort, 8443, true)

        expect:
        WATaskPreparer.portMismatchWarning(mapping, 'webserver.https.port', 'webserver.container.https.port', 443) == null

        where:
        hostPort << [1463, 8080, 9443]
    }

    def 'portMismatchWarning ignores the http default port 80 on the https scheme'() {
        given:
        def mapping = new PortMapping('https', 80, 8443, true)

        expect:
        // https clients only omit 443 in the Host header, so port 80 is still sent and used for generated URLs
        WATaskPreparer.portMismatchWarning(mapping, 'webserver.https.port', 'webserver.container.https.port', 443) == null
    }

    def 'portMismatchWarning describes the mismatch when the host port is the scheme default port'() {
        given:
        def mapping = new PortMapping('https', 443, 8443, true)

        when:
        def message = WATaskPreparer.portMismatchWarning(mapping, 'webserver.https.port', 'webserver.container.https.port', 443)

        then:
        message != null
        message.contains('webserver.https.port=443')
        message.contains('webserver.container.https.port=8443')
        // recommends aligning to the host port property
        message.contains("Set 'webserver.container.https.port' equal to 'webserver.https.port'")
    }

    def 'portMismatchWarning warns for http on the default port 80'() {
        given:
        def mapping = new PortMapping('http', 80, 8080, false)

        expect:
        WATaskPreparer.portMismatchWarning(mapping, 'webserver.http.port', 'webserver.container.http.port', 80) != null
    }
}
