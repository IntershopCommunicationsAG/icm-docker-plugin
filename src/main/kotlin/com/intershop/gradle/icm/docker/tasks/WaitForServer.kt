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
package com.intershop.gradle.icm.docker.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import java.net.UnknownHostException
import javax.inject.Inject

open class WaitForServer @Inject constructor(objectFactory: ObjectFactory): DefaultTask() {

    @get:Input
    val webServerPort: Property<String> = objectFactory.property(String::class.java)

    @get:Input
    val webServerHost: Property<String> = objectFactory.property(String::class.java)

    @get:Input
    val appServerPort: Property<String> = objectFactory.property(String::class.java)

    @get:Input
    val appServerHost: Property<String> = objectFactory.property(String::class.java)

    @get:Input
    val socketTries: Property<Int> = objectFactory.property(Int::class.java)

    @get:Input
    val urlTries: Property<Int> = objectFactory.property(Int::class.java)

    init {
        socketTries.convention(100)
        urlTries.convention(200)
    }

    @TaskAction
    fun waiting() {
        // wait for web server
        waitForSocket(webServerHost.get(), webServerPort.get(), socketTries.get())

        // wait for app server
        waitForURL(
            "http://${appServerHost.get()}:${appServerPort.get()}/servlet/ConfigurationServlet",
            urlTries.get()
        )

        project.logger.quiet("Server on {}:{} is ready!", webServerHost.get(), webServerPort.get())
    }

    private fun waitForSocket(hostaddr: String, port: String, counter: Int) {
        project.logger.info("Try to connect {}:{}", hostaddr, port)

        var counterCur = counter
        while(! checkSocket(hostaddr, port) && counterCur > 0) {
            if(counterCur % 10 == 0) {
                logger.quiet("Waiting on $port")
            }
            Thread.sleep(6000)
            counterCur -= 1
        }
        if(counterCur <= 0 && ! checkSocket(hostaddr, port)) {
            throw GradleException("Socket for ${hostaddr}:${port} is still not available.")
        }
    }

    private fun waitForURL(url: String, counter: Int) {
        project.logger.info("Try to get {}", url)

        var counterCur = counter
        while(! checkURL(url) && counterCur > 0) {
            if(counterCur % 10 == 0) {
                logger.quiet("Waiting on $url")
            }
            Thread.sleep(6000)
            counterCur -= 1
        }

        if(counterCur <= 0 && ! checkURL(url)) {
            throw GradleException("$url is still not available.")
        }
    }

    private fun checkSocket(hostaddr: String, port: String): Boolean {
        try {
            Socket(hostaddr, port.toInt())
            return true
        } catch (uke: UnknownHostException) {
            logger.info("Server was not available on $hostaddr and $port (unknown host)")
        } catch (ioe: IOException) {
            logger.info("Server was not available on $hostaddr and $port (IO)")
        }
        return false
    }

    private fun checkURL(url: String): Boolean {
        return try {
            val urlObj = URL(url)
            val httpUrlConn = urlObj.openConnection() as HttpURLConnection
            httpUrlConn.requestMethod = "HEAD"

            // Set timeouts in milliseconds
            httpUrlConn.connectTimeout = 1000
            httpUrlConn.readTimeout = 1000

            val responseCode = httpUrlConn.responseCode
            (responseCode == HttpURLConnection.HTTP_OK)
        } catch (e: IOException) {
            logger.info("Server was not available on {} because of: {}", url, e.message)
            false
        }
    }
}

