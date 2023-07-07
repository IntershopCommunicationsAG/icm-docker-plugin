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

    const val ICM_AS_ENVIRONMENT = "environment"

    const val DB_MSSQL_PORT = "intershop.db.mssql.port"
    const val DB_MSSQL_CONTAINER_PORT = "intershop.db.container.mssql.hostport"

    const val DB_MSSQL_PORT_VALUE = 1433
    const val DB_MSSQL_CONTAINER_PORT_VALUE = 1433

    const val DB_MSSQL_SA_PASSWORD = "intershop.db.mssql.sa.password"
    const val DB_MSSQL_SA_PASSWORD_VALUE = "1ntershop5A"

    const val DB_MSSQL_RECREATE_DB = "intershop.db.mssql.recreatedb"
    const val DB_MSSQL_RECREATE_USER = "intershop.db.mssql.recreateuser"

    const val DB_MSSQL_RECREATE_DB_VALUE = "false"
    const val DB_MSSQL_RECREATE_USER_VALUE = "false"

    const val DB_MSSQL_DBNAME = "intershop.db.mssql.dbname"
    const val DB_MSSQL_DBNAME_VALUE = "icmtestdb"

    const val DB_ORACLE_LISTENERPORT = "intershop.db.oracle.listenerport"
    const val DB_ORACLE_CONTAINER_LISTENERPORT = "intershop.db.container.oracle.listenerport"

    const val DB_ORACLE_PORT = "intershop.db.oracle.port"
    const val DB_ORACLE_CONTAINER_PORT = "intershop.db.container.oracle.port"

    const val DB_ORACLE_LISTENERPORT_VALUE = "1521"
    const val DB_ORACLE_CONTAINER_LISTENERPORT_VALUE = "1521"

    const val DB_ORACLE_PORT_VALUE = "5500"
    const val DB_ORACLE_CONTAINER_PORT_VALUE = "5500"

    const val DB_TYPE = "intershop.databaseType"
    const val DB_JDBC_URL = "intershop.jdbc.url"

    const val DB_USER_NAME = "intershop.jdbc.user"
    const val DB_USER_PASSWORD = "intershop.jdbc.password"

    const val DB_USER_NAME_VALUE = "intershop"
    const val DB_USER_PASSWORD_VALUE = "intershop"

    const val AS_USE_TESTIMAGE = "intershop.as.use.testimage"
    const val AS_USE_TESTIMAGE_VALUE = "true"
    
    const val LOCAL_CONNECTOR_HOST = "intershop.local.hostname"
    const val LOCAL_CONNECTOR_HOST_VALUE = "localhost"

    const val AS_CONNECTOR_ADDRESS = "intershop.servletEngine.connector.address"
    const val AS_SERVICE_CONNECTOR_HOST_PORT = "intershop.servletEngine.connector.port"
    const val AS_SERVICE_CONNECTOR_HOST_PORT_VALUE = 7743
    const val AS_SERVICE_CONNECTOR_PORT = "intershop.as.connector.port"
    const val AS_SERVICE_CONNECTOR_PORT_VALUE = 7743

    const val AS_MANAGEMENT_CONNECTOR_HOST_PORT = "intershop.servletEngine.managementConnector.port"
    const val AS_MANAGEMENT_CONNECTOR_HOST_PORT_VALUE = 7744
    const val AS_MANAGEMENT_CONNECTOR_PORT = "intershop.as.managementConnector.port"
    const val AS_MANAGEMENT_CONNECTOR_PORT_VALUE = 7744

    const val AS_JMX_CONNECTOR_CONTAINER_PORT_VALUE = 7747
    const val AS_JMX_CONNECTOR_PORT = "intershop.as.jmx.connector.port"
    const val AS_JMX_CONNECTOR_PORT_VALUE = 7747

    const val AS_DEBUG_CONTAINER_PORT_VALUE = 7746
    const val AS_DEBUG_PORT = "intershop.as.debug.port"
    const val AS_DEBUG_PORT_VALUE = 7746

    const val WS_HTTP_PORT = "webserver.http.port"
    const val WS_HTTPS_PORT = "webserver.https.port"
    const val WS_CONTAINER_HTTP_PORT = "webserver.container.http.port"
    const val WS_CONTAINER_HTTPS_PORT = "webserver.container.https.port"

    const val WS_HTTP_PORT_VALUE = 8080
    const val WS_HTTPS_PORT_VALUE = 8443
    const val WS_CONTAINER_HTTP_PORT_VALUE = 8080
    const val WS_CONTAINER_HTTPS_PORT_VALUE = 8443

    const val WS_CERT_PATH = "webServer.cert.path"
    const val WS_SERVER_CERT = "webserver.cert.server"
    const val WS_SERVER_PRIVAT = "webserver.cert.privatekey"

    const val WS_SERVER_HTTP2 = "webserver.use.http2"

    const val WS_URL = "intershop.WebServerURL"
    const val WS_URL_VALUE = "http://localhost:8080"
    const val WS_SECURE_URL = "intershop.WebServerSecureURL"
    const val WS_SECURE_URL_VALUE = "https://localhost:8443"

    const val WS_READINESS_PROBE_INTERVAL = "intershop.db.readinessProbe.interval"
    const val WS_READINESS_PROBE_INTERVAL_VALUE = 2 // 2 secs
    const val WS_READINESS_PROBE_TIMEOUT = "intershop.db.readinessProbe.timeout"
    const val WS_READINESS_PROBE_TIMEOUT_VALUE = 30 // 30 secs

    const val AS_ADMIN_USER_NAME = "intershop.smc.admin.user.name"
    const val AS_ADMIN_USER_NAME_VALUE = "admin"
    const val AS_ADMIN_USER_PASSWORD = "intershop.smc.admin.user.password"
    const val AS_READINESS_PROBE_INTERVAL = "intershop.as.readinessProbe.interval"
    const val AS_READINESS_PROBE_INTERVAL_VALUE = 15 // 15 secs
    const val AS_READINESS_PROBE_TIMEOUT = "intershop.as.readinessProbe.timeout"
    const val AS_READINESS_PROBE_TIMEOUT_VALUE = 100 * 60 // 100 mins (full dbinit may be necessary)

    const val SOLR_CLOUD_HOSTLIST = "solr.zooKeeperHostList"
    const val SOLR_CLOUD_INDEXPREFIX = "solr.clusterIndexPrefix"
    const val SOLR_CLOUD_HOST_PORT = "solr.port"
    const val SOLR_CLOUD_HOST_PORT_VALUE = 8983
    const val SOLR_DATA_FOLDER_PATH = "solr.data.folder.path"

    const val ZOOKEEPER_HOST_PORT = "zookeeper.port"
    const val ZOOKEEPER_HOST_PORT_VALUE = 2181
    const val ZOOKEEPER_METRICS_HOST_PORT = "zookeeper.metrics.port"
    const val ZOOKEEPER_METRICS_HOST_PORT_VALUE = 7000

    const val SSL_VERIFICATION = "ssl.verification"

    const val ADDITIONAL_CONTAINER_PREFIX = "additional.container.prefix"

    const val GEB_LOCAL_DRIVER = "geb.local.driver"
    const val GEB_LOCAL_ENVIRONMENT = "geb.local.environment"

    const val CONTAINER_ENV_PROP = "intershop.environment.container"

    const val SITES_FOLDER_PATH = "sites.folder.path"

    const val DATA_FOLDER_PATH = "data.folder.path"
    const val BACKUP_FOLDER_PATH = "backup.folder.path"

    const val DATA_FOLDER_VOLUME = "data.folder.volume"
    const val BACKUP_FOLDER_VOLUME = "backup.folder.volume"

    const val DATA_FOLDER_VOLUME_VALUE = "/var/opt/mssql/data"
    const val BACKUP_FOLDER_VOLUME_VALUE = "/var/opt/mssql/backup"

    const val MAIL_SMTP_HOST_PORT = "mail.smtp.host.port"
    const val MAIL_SMTP_HOST = "mail.smtp.host"
    const val MAIL_SMTP_PORT = "mail.smtp.port"
    const val MAIL_SMTP_HOST_PORT_VALUE = 25
    const val MAIL_ADMIN_HOST_PORT = "mail.admin.host.port"
    const val MAIL_ADMIN_HOST_PORT_VALUE = 8025
    const val MAIL_READINESS_PROBE_INTERVAL = "mail.readinessProbe.interval"
    const val MAIL_READINESS_PROBE_INTERVAL_VALUE = 1 // 1 secs
    const val MAIL_READINESS_PROBE_TIMEOUT = "mail.readinessProbe.timeout"
    const val MAIL_READINESS_PROBE_TIMEOUT_VALUE = 15 // 15 secs

    const val NGINX_HTTP_PORT = "nginx.http.port"
    const val NGINX_HTTPS_PORT = "nginx.https.port"

    const val NGINX_HTTP_PORT_VALUE = 8080
    const val NGINX_HTTPS_PORT_VALUE = 8443

    const val NGINX_CERT_PATH = "nginx.cert.path"
    const val NGINX_CERT_FILENAME = "nginx.cert.filename"
    const val NGINX_CERT_FILENAME_VALUE = "fullchain.pem"
    const val NGINX_PRIVATEKEY_FILENAME = "nginx.privatekey.filename"
    const val NGINX_PRIVATEKEY_FILENAME_VALUE = "privkey.pem"

    const val INTERSHOP_ENVIRONMENT_PREFIX = "intershop.environment."
}
