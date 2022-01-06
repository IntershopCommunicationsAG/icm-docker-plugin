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
import org.gradle.api.logging.Logger
import java.io.IOException
import java.util.function.BiConsumer

/**
 * Callback that just writes the container's output using a [Logger] redirecting
 * + [StreamType.STDOUT] and [StreamType.RAW] to [Logger.quiet]
 * + [StreamType.STDERR] to [Logger.warn]
 */
class RedirectToLoggerCallback(
        val logger: Logger,
        private val payloadToString: (payload: ByteArray) -> String = { payload ->
            payload.toString(Charsets.US_ASCII)
        },
) : ResultCallbackTemplate<RedirectToLoggerCallback, Frame>() {
    private var frameHandler : BiConsumer<StreamType, ByteArray>? = null

    private var currentLine : String = ""

    /**
     * Main method of callback class.
     */
    override fun onNext(frame: Frame?) {
        if (frame != null) {
            try {
                val msg = payloadToString.invoke(frame.payload)
                currentLine += msg
                val lastChar = msg.last()
                // end of line ?
                if (lastChar == '\n' || lastChar == '\r') {
                    currentLine = currentLine.trim()
                    // log it
                    when (frame.streamType) {
                        StreamType.STDOUT, StreamType.RAW -> {
                            logger.quiet(currentLine)
                            frameHandler?.accept(frame.streamType, frame.payload)
                        }
                        StreamType.STDERR -> {
                            logger.warn(currentLine)
                            frameHandler?.accept(frame.streamType, frame.payload)
                        }
                        else -> logger.error("unknown stream type:" + frame.streamType)
                    }
                    // reset current line
                    currentLine = ""
                }
                // else: msg is just appended to currentLine
            } catch (e: IOException) {
                onError(e)
            }
        }
    }

    /**
     * Attaches a callback handler to the [Frame] processing inside of [onNext] that is called when a [Frame] with
     * one of the following types is processed:
     * + [StreamType.STDOUT]
     * + [StreamType.RAW]
     * + [StreamType.STDERR]
     */
    fun onWriteToStream(handler : BiConsumer<StreamType, ByteArray> ) : RedirectToLoggerCallback {
        frameHandler = handler
        return this
    }
}
