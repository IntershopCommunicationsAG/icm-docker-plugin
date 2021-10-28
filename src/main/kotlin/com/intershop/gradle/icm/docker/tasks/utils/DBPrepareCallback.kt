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
 * Callback for dbPrepare execution.
 */
class DBPrepareCallback (
    private val stdout: OutputStream,
    private val stderr: OutputStream,
    private val showJson: Boolean = false): ResultCallbackTemplate<DBPrepareCallback, Frame>() {

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
                        out.split("\\n").forEach {
                            val message = getMessageString(it)
                            logger.debug("Message is: {}.", message )

                            val info = getCartrigeInfo(message)
                            if (info != null) {
                                dbinfo = info
                            }

                            if (!showJson) {
                                val outline = message ?: it
                                if (outline.isNotEmpty()) {
                                    stdout.write(outline.toByteArray(Charsets.US_ASCII))
                                }
                            }
                        }
                        if (showJson) {
                            stdout.write(out.toByteArray(Charsets.US_ASCII))
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
        return if(input.trim().isNotEmpty()) {
            input.trim()
        } else {
            null
        }
    }

    private fun getCartrigeInfo(input: String?): DBPrepareResult? {
        if(input != null && input.contains("DBPrepare")) {
            val s1 = input.indexOf("DBPrepare")
            val e1 = input.indexOf("initialization steps")
            val c = if (s1 > -1 && e1 > -1) formatStringToInt(input.substring(s1 + "DBPrepare".length, e1)) else 0

            val regexS = Regex("success: [\\d]+")
            val matchS = regexS.find(input, 0)
            val s = formatStringToInt(matchS?.value?.substring("success:".length))

            val regexF = Regex("failure: [\\d]+")
            val matchF = regexF.find(input, 0)
            val f = formatStringToInt(matchF?.value?.substring("failure:".length))

            return DBPrepareResult(c, s, f)
        }
        return null
    }

    private fun formatStringToInt(str: String?): Int {
        return str?.trim()?.replace(",", "")?.replace(".", "")?.toInt() ?: 0
    }
}
