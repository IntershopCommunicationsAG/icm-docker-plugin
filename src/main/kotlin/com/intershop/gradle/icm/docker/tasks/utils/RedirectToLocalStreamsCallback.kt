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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.OutputStream
import java.util.function.BiConsumer

/**
 * Callback that just writes the container's output to `stdout` respectively `stderr`
 */
class RedirectToLocalStreamsCallback (
    private val stdout: OutputStream,
    private val stderr: OutputStream): ResultCallbackTemplate<RedirectToLocalStreamsCallback, Frame>() {
    private val logger: Logger = LoggerFactory.getLogger(RedirectToLocalStreamsCallback::class.java)
    private var frameHandler : BiConsumer<StreamType, ByteArray>? = null

    /**
     * Main method of callback class.
     */
    override fun onNext(frame: Frame?) {
        if (frame != null) {
            try {
                when (frame.streamType) {
                    StreamType.STDOUT, StreamType.RAW -> {
                        stdout.write(frame.payload)
                        stdout.flush()
                        frameHandler?.accept(frame.streamType, frame.payload)
                    }
                    StreamType.STDERR -> {
                        stderr.write(frame.payload)
                        stderr.flush()
                        frameHandler?.accept(frame.streamType, frame.payload)
                    }
                    else -> logger.error("unknown stream type:" + frame.streamType)
                }
            } catch (e: IOException) {
                onError(e)
            }
            logger.debug(frame.toString())
        }
    }

    fun onWriteToStream(handler : BiConsumer<StreamType, ByteArray> ) : RedirectToLocalStreamsCallback {
        frameHandler = handler
        return this
    }
}
