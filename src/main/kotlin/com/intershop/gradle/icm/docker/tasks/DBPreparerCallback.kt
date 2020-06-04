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

import com.github.dockerjava.api.async.ResultCallbackTemplate
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.StreamType
import com.github.dockerjava.core.command.ExecStartResultCallback
import java.io.IOException
import java.io.OutputStream
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DBPreparerCallback(val stdout: OutputStream, val stderr: OutputStream): ResultCallbackTemplate<DBPreparerCallback, Frame>() {

    private val LOGGER: Logger = LoggerFactory.getLogger(DBPreparerCallback::class.java)

    override fun onNext(frame: Frame?) {
        if (frame != null) {
            try {
                when (frame.streamType) {
                    StreamType.STDOUT, StreamType.RAW -> if (stdout != null) {
                        stdout.write(frame.payload)
                        stdout.flush()
                    }
                    StreamType.STDERR -> if (stderr != null) {
                        stderr.write(frame.payload)
                        stderr.flush()
                    }
                    else -> LOGGER.error("unknown stream type:" + frame.streamType)
                }
            } catch (e: IOException) {
                onError(e)
            }
            LOGGER.debug(frame.toString())
        }
    }
}
