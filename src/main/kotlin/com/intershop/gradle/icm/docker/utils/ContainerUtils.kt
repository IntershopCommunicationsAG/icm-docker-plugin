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

package com.intershop.gradle.icm.docker.utils

object ContainerUtils {

    /**
     * Adapt volume definitions for Windows, so that Docker can
     * mount these directories.
     *
     * @param volumes map of mount definitions independent from the platform
     * @return a map of mount definitions changed if the system is windows
     */
    fun transformVolumes(volumes: Map<String,String>) : Map<String, String> {
        val tv = mutableMapOf<String, String>()
        volumes.forEach { (k, v) ->
            if(k.contains('\\') || k.contains(':')) {
                tv["//${k.replace('\\','/')}".replace(":", "")] = v
            } else {
                tv[k] = v
            }
        }
        return tv.toMap()
    }
}
