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

import com.intershop.gradle.icm.docker.tasks.CreateExtraContainer
import com.intershop.gradle.icm.docker.tasks.PrepareNetwork
import com.intershop.gradle.icm.docker.tasks.utils.ContainerEnvironment
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
        const val EXT_NAME: String = "NGINX"
        const val HTTP_CONTAINER_PORT: Int = 80
        const val HTTPS_CONTAINER_PORT: Int = 443

        const val ENV_UPSTREAM_HOST = "NGINX_UPSTREAM_HOST"
        const val ENV_UPSTREAM_PORT = "NGINX_UPSTREAM_PORT"
        const val ENV_SSL_CERTIFICATEFILENAME = "NGINX_SSL_CERTIFICATEFILENAME"
        const val ENV_SSL_PRIVATEKEYFILENAME = "NGINX_SSL_PRIVATEKEYFILENAME"

        const val VOLUME_SSL = "/etc/nginx/ssl"
    }

    override fun getExtensionName(): String = EXT_NAME

    override fun getImage(): Provider<String> = dockerExtension.images.nginx
    override fun getUseHostUserConfigProperty(): String = Configuration.NGINX_USE_HOST_USER

    init {
        initBaseTasks()

        val httpPortMapping = devConfig.getPortMapping(
                "HTTP",
                Configuration.NGINX_HTTP_PORT,
                Configuration.NGINX_HTTP_PORT_VALUE,
                HTTP_CONTAINER_PORT,
                false
        )
        val httpsPortMapping = devConfig.getPortMapping(
                "HTTPS",
                Configuration.NGINX_HTTPS_PORT,
                Configuration.NGINX_HTTPS_PORT_VALUE,
                HTTPS_CONTAINER_PORT,
                true
        )
        val env = project.provider {
            ContainerEnvironment().addAll(
                    ENV_UPSTREAM_HOST to devConfig.getConfigProperty(
                            Configuration.LOCAL_CONNECTOR_HOST,
                            Configuration.LOCAL_CONNECTOR_HOST_VALUE
                    ),
                    ENV_UPSTREAM_PORT to devConfig.getConfigProperty(
                            Configuration.AS_SERVICE_CONNECTOR_PORT,
                            Configuration.AS_SERVICE_CONNECTOR_PORT_VALUE.toString()
                    ),
                    ENV_SSL_CERTIFICATEFILENAME to
                            devConfig.getConfigProperty(
                                    Configuration.NGINX_CERT_FILENAME,
                                    Configuration.NGINX_CERT_FILENAME_VALUE
                            ),
                    ENV_SSL_PRIVATEKEYFILENAME to
                            devConfig.getConfigProperty(
                                    Configuration.NGINX_PRIVATEKEY_FILENAME,
                                    Configuration.NGINX_PRIVATEKEY_FILENAME_VALUE
                            )
            )
        }
        val volumes = project.provider { mapOf(getCertDir().absolutePath to VOLUME_SSL) }

        val createTask = registerCreateContainerTask(findTask, CreateExtraContainer::class.java, volumes, env)
        createTask.configure { task ->
            task.withPortMappings(httpPortMapping, httpsPortMapping)
        }

        registerStartContainerTask(createTask)
    }

    private fun getCertDir(): File {
        val nginxCertPath = devConfig.getConfigProperty(Configuration.NGINX_CERT_PATH)
        val wsCertPath = devConfig.getConfigProperty(Configuration.WS_CERT_PATH)

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

        return certDir
    }
}
