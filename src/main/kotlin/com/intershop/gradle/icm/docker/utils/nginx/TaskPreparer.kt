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
package com.intershop.gradle.icm.docker.utils.nginx

import com.intershop.gradle.icm.docker.tasks.PrepareNetwork
import com.intershop.gradle.icm.docker.tasks.StartExtraContainer
import com.intershop.gradle.icm.docker.utils.AbstractTaskPreparer
import com.intershop.gradle.icm.docker.utils.Configuration
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import java.io.File

class TaskPreparer(
    project: Project,
    networkTask: Provider<PrepareNetwork>
) : AbstractTaskPreparer(project, networkTask) {

    companion object {
        const val extName: String = "NGINX"
        const val HTTP_CONTAINER_PORT: Int = 80
        const val HTTPS_CONTAINER_PORT: Int = 443

        const val ENV_UPSTREAM_HOST = "NGINX_UPSTREAM_HOST"
        const val ENV_UPSTREAM_PORT = "NGINX_UPSTREAM_PORT"
        const val ENV_SSL_CERTIFICATEFILENAME = "NGINX_SSL_CERTIFICATEFILENAME"
        const val ENV_SSL_PRIVATEKEYFILENAME = "NGINX_SSL_PRIVATEKEYFILENAME"

        const val VOLUME_SSL = "/etc/nginx/ssl"
    }

    override fun getExtensionName(): String = extName

    override fun getImage(): Provider<String> = dockerExtension.images.nginx

    init {
        initBaseTasks()

        project.tasks.register("start${getExtensionName()}", StartExtraContainer::class.java) { task ->
            configureContainerTask(task)
            task.description = "Starts an NGINX server configured as a reverse proxy"

            task.targetImageId(project.provider { pullTask.get().image.get() })
            task.image.set(pullTask.get().image)

            with(dockerExtension.developmentConfig) {
                val httpPortMapping = getPortMapping(
                    "HTTP",
                    Configuration.NGINX_HTTP_PORT,
                    Configuration.NGINX_HTTP_PORT_VALUE,
                    HTTP_CONTAINER_PORT,
                    false
                )
                val httpsPortMapping = getPortMapping(
                    "HTTPS",
                    Configuration.NGINX_HTTPS_PORT,
                    Configuration.NGINX_HTTPS_PORT_VALUE,
                    HTTPS_CONTAINER_PORT,
                    true
                )
                task.withPortMappings(httpPortMapping, httpsPortMapping)
                task.hostConfig.network.set(networkId)

                task.envVars.set(
                    mutableMapOf(
                        ENV_UPSTREAM_HOST to getConfigProperty(
                            Configuration.LOCAL_CONNECTOR_HOST,
                            Configuration.LOCAL_CONNECTOR_HOST_VALUE
                        ),
                        ENV_UPSTREAM_PORT to getConfigProperty(
                            Configuration.AS_SERVICE_CONNECTOR_PORT,
                            Configuration.AS_SERVICE_CONNECTOR_PORT_VALUE.toString()
                        ),
                        ENV_SSL_CERTIFICATEFILENAME to
                                getConfigProperty(
                                    Configuration.NGINX_CERT_FILENAME,
                                    Configuration.NGINX_CERT_FILENAME_VALUE
                                ),
                        ENV_SSL_PRIVATEKEYFILENAME to
                                getConfigProperty(
                                    Configuration.NGINX_PRIVATEKEY_FILENAME,
                                    Configuration.NGINX_PRIVATEKEY_FILENAME_VALUE
                                )
                    )
                )

                val nginxCertPath = dockerExtension.developmentConfig.getConfigProperty(Configuration.NGINX_CERT_PATH)
                val wsCertPath = dockerExtension.developmentConfig.getConfigProperty(Configuration.WS_CERT_PATH)

                // if NginxCertPath is not set, we take the webserver certificate path instead
                val certPath: String
                val configOption: String
                if (nginxCertPath.isBlank() && wsCertPath.isBlank()) {
                    throw GradleException(
                        "Missing or empty property '${Configuration.NGINX_CERT_PATH}' in your " +
                                "icm.properties!"
                    )
                } else {
                    certPath = if (nginxCertPath.isNotBlank()) {
                        configOption = Configuration.NGINX_CERT_PATH
                        nginxCertPath
                    } else {
                        configOption = Configuration.WS_CERT_PATH
                        wsCertPath
                    }
                }

                val certDir = File(certPath)
                if (!certDir.exists()) {
                    throw GradleException(
                        "Property '${configOption}' in your icm.properties " +
                                "points to a non-existing or non-accessible path: '${certPath}'"
                    )
                }

                task.hostConfig.binds.set(
                    mutableMapOf(certDir.absolutePath to VOLUME_SSL)
                )
            }
            task.dependsOn(pullTask, networkTask)
        }
    }

}
