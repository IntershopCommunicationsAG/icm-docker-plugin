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

object Configuration {

    const val DB_MSSQL_PORT = "intershop.db.mssql.hostport"
    const val DB_MSSQL_CONTAINER_PORT  = "intershop.db.container.mssql.hostport"

    const val DB_MSSQL_PORT_VALUE = "1433"
    const val DB_MSSQL_CONTAINER_PORT_VALUE = "1433"

    const val DB_MSSQL_SA_PASSWORD = "intershop.db.mssql.sa.password"
    const val DB_MSSQL_SA_PASSWORD_VALUE = "1ntershop5A"

    const val DB_MSSQL_RECREATE_DB = "intershop.db.mssql.recreatedb"
    const val DB_MSSQL_RECREATE_USER = "intershop.db.mssql.recreateuser"

    const val DB_MSSQL_RECREATE_DB_VALUE = "false"
    const val DB_MSSQL_RECREATE_USER_VALUE = "false"

    const val DB_MSSQL_DBNAME = "intershop.db.mssql.dbname"
    const val DB_MSSQL_DBNAME_VALUE = "icmtestdb"

    const val DB_USER_NAME = "intershop.jdbc.user"
    const val DB_USER_PASSWORD = "intershop.jdbc.password"

    const val DB_USER_NAME_VALUE = "intershop"
    const val DB_USER_PASSWORD_VALUE = "intershop"

    const val AS_CONNECTOR_PORT = "intershop.servletEngine.connector.port"
    const val AS_CONNECTOR_PORT_VALUE = "7743"

    const val AS_CONNECTOR_HOST = "intershop.as.hostname"
    const val AS_CONNECTOR_HOST_VALUE = "localhost"

    const val AS_CONNECTOR_CONTAINER_PORT = "intershop.servletEngine.connector.container.port"
    const val AS_CONNECTOR_CONTAINER_PORT_VALUE = "7743"
    const val AS_EXT_CONNECTOR_PORT = "intershop.as.connector.port"
    const val AS_EXT_CONNECTOR_PORT_VALUE = "7743"

    const val AS_JMX_CONNECTOR_CONTAINER_PORT = "intershop.as.jmx.connector.container.port"
    const val AS_JMX_CONNECTOR_CONTAINER_PORT_VALUE = "7747"
    const val AS_JMX_CONNECTOR_PORT = "intershop.as.jmx.connector.port"
    const val AS_JMX_CONNECTOR_PORT_VALUE = "7747"

    const val WS_HTTP_PORT = "webserver.http.port"
    const val WS_HTTPS_PORT = "webserver.https.port"
    const val WS_CONTAINER_HTTP_PORT = "webserver.container.http.port"
    const val WS_CONTAINER_HTTPS_PORT = "webserver.container.https.port"

    const val WS_HTTP_PORT_VALUE = "8080"
    const val WS_HTTPS_PORT_VALUE = "8443"
    const val WS_CONTAINER_HTTP_PORT_VALUE = "8080"
    const val WS_CONTAINER_HTTPS_PORT_VALUE = "8443"

    const val AS_ADMIN_USER_NAME = "intershop.smc.admin.user.name"
    const val AS_ADMIN_USER_NAME_VALUE = "admin"
    const val AS_ADMIN_USER_PASSWORD = "intershop.smc.admin.user.password"

    const val SOLR_CLOUD_HOSTLIST = "solr.zooKeeperHostList"
    const val SOLR_CLOUD_INDEXPREFIX = "solr.clusterIndexPrefix"

    const val DISABLE_SSL_VERIFICATION = "disable.ssl.verification"
}
