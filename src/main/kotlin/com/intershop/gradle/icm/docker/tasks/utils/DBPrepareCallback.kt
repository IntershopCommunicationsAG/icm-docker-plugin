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
class DBPrepareCallback (
    private val stdout: OutputStream,
    private val stderr: OutputStream,
    private val showJson: Boolean = false): ResultCallbackTemplate<DBPrepareCallback, Frame>() {

    companion object {
        const val MESSAGELOG_START = "message"
        const val MESSAGELOG_END = "logger_name"
    }

    private val logger: Logger = LoggerFactory.getLogger(DBPrepareCallback::class.java)
    private var dbinfo: DBPrepareResult? = null

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
                            val message = getMessageString(it)
                            logger.debug("Message is: {}.", message )

                            val info = getCartrigeInfo(message)
                            if (info != null) {
                                dbinfo = info
                            }

                            if (!showJson) {
                                val outline = message ?: it
                                if (outline.isNotEmpty()) {
                                    stdout.write((outline + "\n").toByteArray(Charsets.US_ASCII))
                                }
                            }
                        }
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
    fun getDBInfo(): DBPrepareResult? {
        return dbinfo
    }

    private fun getMessageString(input: String): String? {
        val s1 = input.indexOf(MESSAGELOG_START)
        val e1 = input.indexOf(MESSAGELOG_END)
        if(s1 > -1) {
            return if( e1 > s1 ) {
                input.substring(s1 + MESSAGELOG_START.length + 3, e1 - 3)
            } else {
                input.substring(s1 + MESSAGELOG_START.length + 3)
            }
        }
        return null
    }

    private fun getCartrigeInfo(input: String?): DBPrepareResult? {
        if(input != null && input.contains("DBInit with")) {
            val s1 = input.indexOf("DBInit with")
            val e1 = input.indexOf("initialization steps")
            val c = if (s1 > -1 && e1 > -1) input.substring(s1 + "DBInit with".length, e1).trim().toInt() else 0

            val regexS = Regex("success: [\\d]+")
            val matchS = regexS.find(input, 0)
            val s = matchS?.value?.substring("success:".length)?.trim()?.toInt() ?: 0

            val regexF = Regex("failure: [\\d]+")
            val matchF = regexF.find(input, 0)
            val f = matchF?.value?.substring("failure:".length)?.trim()?.toInt() ?: 0

            return DBPrepareResult(c, s, f)
        }
        return null
    }
}