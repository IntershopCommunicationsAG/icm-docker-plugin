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

import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.StreamType
import org.gradle.api.logging.Logger

/**
 * Implementation of an Adapter of a ResultCallback. Used to
 * handle log output of a container.
 */
open class LogContainerCallback(val logger: Logger, private val finishedCheck: String): ResultCallback.Adapter<Frame>() {

    var startSuccessful = false

    override fun onNext(frame: Frame) {

        when(frame.streamType) {
            StreamType.RAW,StreamType.STDOUT -> {
                val out = frame.payload.toString(Charsets.US_ASCII)
                out.split("\n").forEach {
                    val message = it.trim()
                    if(message.isNotEmpty()) {
                        if(message.contains(finishedCheck)) {
                            startSuccessful = true
                        }
                        logger.quiet(message)
                    }
                }
            }
            StreamType.STDERR -> {
                logger.quiet(frame.payload.toString(Charsets.US_ASCII).trim())
            }
            else -> logger.error("unknown stream type:" + frame.streamType)
        }
    }

}
