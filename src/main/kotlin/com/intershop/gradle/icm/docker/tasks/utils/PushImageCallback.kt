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
import com.github.dockerjava.api.exception.DockerClientException
import com.github.dockerjava.api.model.PushResponseItem


open class PushImageCallback: ResultCallback.Adapter<PushResponseItem>() {

    private var latestItem: PushResponseItem? = null

    override fun onNext(item: PushResponseItem) {
        this.latestItem = item
    }

    override fun throwFirstError() {
        super.throwFirstError()

        if (latestItem == null) {
            throw DockerClientException("Could not push image")
        } else if (latestItem !=null && latestItem!!.isErrorIndicated) {
            throw DockerClientException("Could not push image: " + latestItem!!.errorDetail.message)
        }
    }
}
