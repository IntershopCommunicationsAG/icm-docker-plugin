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

package com.intershop.gradle.icm.docker.extension.geb

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

@DisableCachingByDefault(because = "Downloads a browser driver from a remote location into a local directory - the download is environment specific and not worth caching")
abstract class GebDriverDownload @Inject constructor(objectFactory: ObjectFactory,
                                                 @Internal val name: String) {

    val url: Property<String> = objectFactory.property(String::class.java)

    val archiveType: Property<String> = objectFactory.property(String::class.java)

    val webDriverExecName: Property<String> = objectFactory.property(String::class.java)

}
