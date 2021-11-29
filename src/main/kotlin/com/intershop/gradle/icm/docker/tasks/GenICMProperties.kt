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

import com.intershop.gradle.icm.docker.extension.IntershopDockerExtension
import com.intershop.gradle.icm.docker.utils.Configuration
import com.intershop.gradle.icm.docker.utils.IPFinder
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.kotlin.dsl.getByType
import java.io.File
import java.net.UnknownHostException
import javax.inject.Inject
import com.intershop.gradle.icm.docker.utils.mail.TaskPreparer as MailTaskPreparer
import com.intershop.gradle.icm.docker.utils.mssql.TaskPreparer as MSSQLTaskPreparer
import com.intershop.gradle.icm.docker.utils.oracle.TaskPreparer as OracleTaskPreparer

open class GenICMProperties @Inject constructor(objectFactory: ObjectFactory,
                                           projectLayout: ProjectLayout) : DefaultTask() {

    companion object {
        const val standardDevProps =
            """
                      # development properties
                      # switch auto reload on for all Intershop artifacts
                      intershop.extensions.CheckSource=true
                      intershop.queries.CheckSource=true
                      intershop.pipelines.CheckSource=true
                      intershop.pagelets.CheckSource=true
                      intershop.webforms.CheckSource=true
                      intershop.template.CheckSource=true
                      intershop.template.CheckSourceModified=true
                      intershop.template.isfilebundle.CheckSource=true
                      intershop.urlrewrite.CheckSource=true
                    
                      # switch all preload functionality off
                      intershop.pipelines.PreloadFromCartridges=
                      intershop.pipelines.PreloadFromSites=
                      intershop.pipelets.PreloadFromCartridges=
                      intershop.template.CompileOnStartup=false
                      intershop.webforms.Preload=false
                      intershop.queries.Preload=false
                    
                      # use strict modes in pipeline engine
                      intershop.pipelines.strict.CheckParameterTypes=true
                      intershop.pipelets.OnLoadError=Exception
                    
                      # switch all runtime sensors on
                      intershop.monitoring.requests=true
                      intershop.monitoring.pipelines=true
                      intershop.monitoring.pipelets=true
                      intershop.monitoring.pipelinenodes=true
                      intershop.monitoring.templates=true
                      intershop.monitoring.queries=true
                      intershop.monitoring.sql=true
                      intershop.monitoring.class=true
                      intershop.monitoring.maxSensors=100000
                    
                      # developer's helpers
                      intershop.template.PrintTemplateName=true
                      intershop.template.PrintTemplateMarker=true
                      intershop.session.TimeOut=300
                      intershop.CSRFGuard.allowRecovery=true
            """

        const val databaseTypeProp = Configuration.DB_TYPE
        const val databaseJDBCUrlProp = Configuration.DB_JDBC_URL

        const val webserverUrlProp = "intershop.WebServerURL"
        const val webserverSecureUrlProp = "intershop.WebServerSecureURL"

        const val asConnectorAdressProp = "intershop.servletEngine.connector.address"

        const val asSolrZKListProp =  "solr.zooKeeperHostList"
        const val asSolrPrefixProp = "solr.clusterIndexPrefix"

        const val asMailHostProp = "mail.smtp.host"
        const val asMailPortProp = "mail.smtp.port"

        const val asMailSMTPHostProp = "intershop.SMTPServer"
        const val asMailMessageIDProp = "intershop.mail.messageID.domain"

        const val ICM_PROPERTIES_DIR = "icmproperties"
        const val fileName = "icm.properties"
    }

    @Internal
    protected val extension = project.extensions.getByType<IntershopDockerExtension>()

    @get:OutputDirectory
    val outputDirectory: DirectoryProperty = objectFactory.directoryProperty()

    @get:Option(option = "icmenvops", description =
        """A comma-separated list of options for the icm.properties files.
            dev - General development properties for the application server
            mail - MailHog container is used as test mail server
            solr - Single node solr cluster with containers is used
        """)
    @get:Input
    val contentOptions: Property<String> = project.objects.property(String::class.java)

    @get:Option(option = "db", description =
        """Option for the used database. The following values are possible:
            oracle-container - Oracle configuration for database provided by a container 
            oracle - Oracle configuration for an external database
            mssql-container - MSSQL configuration for database provided by a container
            mssql - MSSQL configuration for an external database
        """)
    @get:Input
    val dbOption: Property<String> = project.objects.property(String::class.java)

    @get:Option(option = "icmas" , description ="If this parameter specified, the properties file "+
            "will be generated for app server development.")
    @get:Input
    val icmasOption: Property<Boolean> = project.objects.property(Boolean::class.java)

    init {
        outputDirectory.convention(projectLayout.buildDirectory.dir(ICM_PROPERTIES_DIR))
        contentOptions.convention("")
        icmasOption.convention(false)
        dbOption.convention("")
    }

    @TaskAction
    fun createFile() {
        val outputFile = outputDirectory.file(fileName).get().asFile
        if(outputFile.exists()) {
            outputFile.delete()
        } else {
            outputFile.createNewFile()
        }
        val optList = contentOptions.get().split(",")

        if(optList.contains("dev")) {
            writeDevProps(outputFile)
        }

        val db = dbOption.get()
        when {
            db.startsWith("oracle-c") -> writeOracleProps(outputFile, true)
            db == "oracle" -> writeOracleProps(outputFile, false)
            db.startsWith("mssql-c") -> writeMSSQLProps(outputFile, true)
            db == "mssql" -> writeMSSQLProps(outputFile, false)
            else -> project.logger.quiet("No database option is specified!")
        }

        writeServerProps(outputFile)

        writeMailProps(outputFile, optList.contains("mail"))
        writeSolrProps(outputFile, optList.contains("solr"))

        writeGebTestProps(outputFile)

        val envListTasks = mutableListOf<String>()

        addTo(envListTasks, "mail", optList.contains("mail"))
        addTo(envListTasks, "solr", optList.contains("solr"))
        addTo(envListTasks, "oracle", db.startsWith("oracle-c"))
        addTo(envListTasks, "mssql", db.startsWith("mssql-c"))
        addTo(envListTasks, "webserver", true)

        val text =
            """
            # The following containers will be started / stopped with
            # startEnv / stopEnv. The container can be configured in 
            # the docker image extension.
            ${Configuration.CONTAINER_ENV_PROP} = ${envListTasks.joinToString(",")}
            """.trimIndent()

        outputFile.appendText(text, Charsets.UTF_8)
        outputFile.appendText("\n\n", Charsets.UTF_8)
    }

    private fun addTo(list: MutableList<String>, value: String, check: Boolean) {
        if(check) {
            list.add(value)
        }
    }

    private fun writeDevProps(file: File) {
        file.appendText(standardDevProps.trimIndent(), Charsets.UTF_8)
        file.appendText("\n\n", Charsets.UTF_8)
    }

    private fun writeDBUserConfig(file: File, container: Boolean) {
        val user = if(container) {
            Configuration.DB_USER_NAME_VALUE
        } else {
            "<database user of ext db>"
        }
        val password = if(container) {
            Configuration.DB_USER_PASSWORD_VALUE
        } else {
            "<database user password of ext db>"
        }

        val text =
            """
            ${Configuration.DB_USER_NAME} = $user
            ${Configuration.DB_USER_PASSWORD} = $password
            """.trimIndent()
        file.appendText(text, Charsets.UTF_8)
        file.appendText("\n\n", Charsets.UTF_8)
    }

    private fun writeOracleProps(file: File, container: Boolean) {
        val icmas = icmasOption.get()

        val host = if(icmas) {
            if(container) { "localhost" } else { "<host name of the external db>" }
        } else {
            if(container) {
                "${extension.containerPrefix}-${OracleTaskPreparer.extName.lowercase()}"
            } else {
                "<host name of the external db>"
            }
        }
        val port = if(icmas) {
            if(container) {
                Configuration.DB_ORACLE_LISTENERPORT_VALUE
            } else {
                "<listener port of the external db>"
            }
        } else {
            if(container) {
                Configuration.DB_ORACLE_CONTAINER_LISTENERPORT_VALUE
            } else {
                "<listener port of the external db>"
            }
        }

        val sid = if(container) { "XE" } else { "<db name of the external db>" }

        val text =
            """
            # oracle base configuration
            $databaseTypeProp = oracle
            $databaseJDBCUrlProp = jdbc:oracle:thin:@$host:$port:$sid
            """.trimIndent()

        file.appendText(text, Charsets.UTF_8)
        file.appendText("\n", Charsets.UTF_8)
        writeDBUserConfig(file,  container)

        if(container) {
            val ctext =
            """
            # mssql container configuration - do not change this value if the default images is used 
            ${Configuration.DB_ORACLE_LISTENERPORT} = ${Configuration.DB_ORACLE_LISTENERPORT_VALUE}
            ${Configuration.DB_ORACLE_CONTAINER_LISTENERPORT} = ${Configuration.DB_ORACLE_CONTAINER_LISTENERPORT_VALUE}
            ${Configuration.DB_ORACLE_PORT} = ${Configuration.DB_ORACLE_PORT_VALUE}
            ${Configuration.DB_ORACLE_CONTAINER_PORT} = ${Configuration.DB_ORACLE_CONTAINER_PORT_VALUE}
            """.trimIndent()
            file.appendText(ctext, Charsets.UTF_8)
            file.appendText("\n\n", Charsets.UTF_8)
        }
    }

    private fun writeMSSQLProps(file: File, container: Boolean) {
        val icmas = icmasOption.get()

        val host = if (icmas) {
            if (container) {
                "localhost"
            } else {
                "<host name of the external db>"
            }
        } else {
            if (container) {
                "${extension.containerPrefix}-${MSSQLTaskPreparer.extName.lowercase()}"
            } else {
                "<host name of the external db>"
            }
        }
        val port = if (icmas) {
            if (container) {
                Configuration.DB_MSSQL_PORT_VALUE
            } else {
                "<port of the external db>"
            }
        } else {
            if (container) {
                Configuration.DB_MSSQL_CONTAINER_PORT_VALUE
            } else {
                "<port of the external db>"
            }
        }

        val dbname = if (container) {
            Configuration.DB_MSSQL_DBNAME_VALUE
        } else {
            "<db name of the external db>"
        }

        val text =
            """
            # mssql base configuration
            $databaseTypeProp = mssql
            $databaseJDBCUrlProp = jdbc:sqlserver://$host:$port;databaseName=$dbname
            """.trimIndent()

        file.appendText(text, Charsets.UTF_8)
        file.appendText("\n", Charsets.UTF_8)
        writeDBUserConfig(file, container)

        with(Configuration) {
            if (container) {
                val ctext =
                    """
                    $DATA_FOLDER_PATH =
                    $BACKUP_FOLDER_PATH =
                        
                    # mssql container configuration - do not change this value if the default images is used 
                    $DB_MSSQL_PORT = $DB_MSSQL_PORT_VALUE
                    $DB_MSSQL_CONTAINER_PORT = $DB_MSSQL_CONTAINER_PORT_VALUE
                    $DB_MSSQL_SA_PASSWORD = $DB_MSSQL_SA_PASSWORD_VALUE
                    $DB_MSSQL_RECREATE_DB = $DB_MSSQL_RECREATE_DB_VALUE
                    $DB_MSSQL_RECREATE_USER = $DB_MSSQL_RECREATE_USER_VALUE
                    $DB_MSSQL_DBNAME = $DB_MSSQL_DBNAME_VALUE
                    """.trimIndent()
                file.appendText(ctext, Charsets.UTF_8)
                file.appendText("\n\n", Charsets.UTF_8)
            }
        }
    }

    private fun writeServerProps(file: File) {
        with(Configuration) {
            val confDir = File(extension.developmentConfig.configDirectory)
            val systemIP = IPFinder.getSystemIP()

            val hostname = if(systemIP.second != null) {
                try {
                    systemIP.second
                } catch (e: UnknownHostException) {
                    e.printStackTrace()
                }
            } else {
                "localhost"
            }

            val text =
                """
                # webserver configuration
                # if youn want change the ports of the webserver, it is necessary to change the ports 
                # in $webserverUrlProp and $webserverSecureUrlProp 
                # according to the settings $WS_HTTP_PORT and $WS_HTTPS_PORT
                #
                $webserverUrlProp = http://$hostname:$WS_HTTP_PORT_VALUE
                $webserverSecureUrlProp = https://$hostname:$WS_HTTPS_PORT_VALUE
              
                # If you want add your own certs
                $WS_CERT_PATH = ${File(confDir, "certs")}
                # if the server certificate is not server.crt, add the name
                $WS_SERVER_CERT =
                # if the private key is not server.key, add the name
                $WS_SERVER_PRIVAT =
                
                # This ports will be exposed to the host.
                $WS_HTTP_PORT = $WS_HTTP_PORT_VALUE
                $WS_HTTPS_PORT = $WS_HTTPS_PORT_VALUE
                
                # Webserver container configuration - do not change this value if the default images is used
                $WS_CONTAINER_HTTP_PORT = $WS_CONTAINER_HTTP_PORT_VALUE
                $WS_CONTAINER_HTTPS_PORT = $WS_CONTAINER_HTTPS_PORT_VALUE
                """.trimIndent()

            file.appendText(text, Charsets.UTF_8)
            file.appendText("\n\n", Charsets.UTF_8)

            if (icmasOption.get()) {
                val wstext =
                    """
                # port number to start the servlet engine
                $AS_CONNECTOR_PORT = $AS_CONNECTOR_PORT_VALUE
            
                # Host name / IP of the ICM Server (local installation)
                # both values must match    
                $LOCAL_CONNECTOR_HOST = ${systemIP.first}
                # WebAdapapter container configuration
                $asConnectorAdressProp = ${systemIP.first}
         
                """.trimIndent()
                file.appendText(wstext, Charsets.UTF_8)

            } else {
                val astext =
                    """
                    # do not change this configuration, if you use the standard
                    # both ports must match
                    # port number to start the servlet engine
                    $AS_CONNECTOR_PORT = $AS_CONNECTOR_PORT_VALUE
                    # container port for the servle engine
                    $AS_CONNECTOR_CONTAINER_PORT = $AS_CONNECTOR_CONTAINER_PORT_VALUE
                    
                    # port number of the exposed port
                    $AS_EXT_CONNECTOR_PORT = $AS_EXT_CONNECTOR_PORT_VALUE
                    
                    # jmx configuration
                    $AS_JMX_CONNECTOR_PORT = $AS_JMX_CONNECTOR_PORT_VALUE
                    $AS_JMX_CONNECTOR_CONTAINER_PORT = $AS_JMX_CONNECTOR_CONTAINER_PORT_VALUE
                    
                    # Host name / IP of the ICM Server (local installation)
                    # both values must match    
                    $LOCAL_CONNECTOR_HOST = ${systemIP.first}
                    """.trimIndent()
                    file.appendText(astext, Charsets.UTF_8)
            }
        }
        file.appendText("\n\n", Charsets.UTF_8)
    }

    private fun writeMailProps(file: File, container: Boolean) {
        val icmAs = icmasOption.get()
        val host = if(icmAs) {
            if(container) { "localhost" } else { "<hostname of the mail server>" }
        } else {
            if(container) {
                "${extension.containerPrefix}-${MailTaskPreparer.extName.lowercase()}"
            } else {
                "<hostname of the mail server>"
            }
        }
        val port = if(container) { "25" } else { "<port of the external zookeeper node>" }

        val text =
            """
            ${asMailHostProp}=${host}
            ${asMailPortProp}=${port}
    
            ${asMailSMTPHostProp}=${host}
            ${asMailMessageIDProp}=${extension.containerPrefix}
            """.trimIndent()

        file.appendText(text, Charsets.UTF_8)
        file.appendText("\n\n", Charsets.UTF_8)
    }

    private fun writeSolrProps(file: File, container: Boolean) {

        val host = if(container) { IPFinder.getSystemIP() } else { "<hostname of min. one external zookeeper node>" }
        val port = if(container) { "2181" } else { "<port of the external zookeeper node>" }
        val solrpath = if(container) { "" } else { "/<path of the solr cluster>" }

        val text =
            """
            $asSolrZKListProp = $host:${port}${solrpath}
            $asSolrPrefixProp = ${extension.containerPrefix}
            """.trimIndent()

        file.appendText(text, Charsets.UTF_8)
        file.appendText("\n\n", Charsets.UTF_8)
    }

    private fun writeGebTestProps(file: File) {
        val text =
            """
            # necessary for automatic Solr resest
            intershop.smc.admin.user.name = admin
            intershop.smc.admin.user.password = <smcadminpassword>
            disable.ssl.verification = true

            geb.local.environment = chromePC
            geb.local.driver = chromeDriver
            """.trimIndent()

        file.appendText(text, Charsets.UTF_8)
        file.appendText("\n\n", Charsets.UTF_8)
    }
}
