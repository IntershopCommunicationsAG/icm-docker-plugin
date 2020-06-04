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
package com.intershop.gradle.icm.docker.tasks.utils

import com.github.dockerjava.api.async.ResultCallbackTemplate
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.StreamType
import java.io.IOException
import java.io.OutputStream
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Callback for dbinit execution.
 */
class DBInitCallback (
    private val stdout: OutputStream,
    private val stderr: OutputStream,
    private val showJson: Boolean = false): ResultCallbackTemplate<DBInitCallback, Frame>() {

    private val logger: Logger = LoggerFactory.getLogger(DBInitCallback::class.java)
    private var dbinfo: DBInitResult? = null

    /**
     * Main method of callback class.
     */
    override fun onNext(frame: Frame?) {
        if (frame != null) {
            try {
                when (frame.streamType) {
                    StreamType.STDOUT, StreamType.RAW -> {
                        val out = frame.payload.toString(Charsets.US_ASCII)
                        out.split("\n").forEach {

                            logger.info("Output line: {}", it)
                            val message = getMessageString(it)
                            logger.info("Message is: {}.", message )

                            val info = getCartrigeInfo(message)
                            if (info != null) {
                                dbinfo = info
                            }

                            if (!showJson) {

                                val outline = message ?: it
                                logger.info("Output is {}", outline)
                                if (outline.isNotEmpty()) {
                                    stdout.write((outline + "\n").toByteArray(Charsets.US_ASCII))
                                }
                            }
                        }
                        logger.info("Show Json is {}", showJson)
                        if (showJson) {
                            stdout.write((out + "\n").toByteArray(Charsets.US_ASCII))
                        }
                        stdout.flush()
                    }
                    StreamType.STDERR -> {
                        stderr.write(frame.payload)
                        stderr.flush()
                    }
                    else -> logger.error("unknown stream type:" + frame.streamType)
                }
            } catch (e: IOException) {
                onError(e)
            }
            logger.debug(frame.toString())
        }
    }

    /**
     * Returns a container with dbinit result.
     */
    fun getDBInfo(): DBInitResult? {
        return dbinfo
    }

    private fun getMessageString(input: String): String? {
        val s1 = input.indexOf("message:")
        val e1 = input.indexOf("logger_name:")
        logger.info("Input: {} with message start {} and end {}.", input, s1, e1)
        if(s1 > 0) {
            return if( e1 > 0) {
                input.substring(s1 + "message:".length, e1 - 1)
            } else {
                input.substring(s1 + "message:".length)
            }
        }
        return null
    }

    private fun getCartrigeInfo(input: String?): DBInitResult? {
        if(input != null && input.startsWith("DBInit with")) {
            val s1 = "DBInit with".length
            val e1 = input.indexOf("initialization steps")
            val c = input.substring(s1, e1).trim().toInt()

            val regexS = Regex("success: [\\d]+")
            val matchS = regexS.find(input, 0)
            val s = matchS?.value?.substring("success:".length)?.trim()?.toInt() ?: 0

            val regexF = Regex("failure: [\\d]+")
            val matchF = regexF.find(input, 0)
            val f = matchF?.value?.substring("failure:".length)?.trim()?.toInt() ?: 0

            return DBInitResult(c, s, f)
        }
        return null
    }
}
