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
package com.intershop.gradle.icm.docker.tasks.mssql

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Statement
import javax.inject.Inject

abstract class AbstractMSSQLDBTask @Inject constructor(objectFactory: ObjectFactory) : DefaultTask() {

    companion object {
        const val FILENAME = "mssql-icm-export.bak"
    }

    /**
     * The output file contains the MSSQL DB dump of the ICM.
     *
     * @property filename of the backup file
     */
    @get:Input
    val filename: Property<String> = objectFactory.property(String::class.java)

    /**
     * Set provider for mssql jdbc url property.
     *
     * @property jdbcUrl provider for mssql jdbc url.
     */
    @get:Input
    val jdbcUrl: Property<String> = objectFactory.property(String::class.java)

    /**
     * Set provider for mssql user property.
     *
     * @property user provider for mssql user.
     */
    @get:Input
    val user: Property<String> = objectFactory.property(String::class.java)

    /**
     * Set provider for mssql password property.
     *
     * @property password provider for mssql password.
     */
    @get:Input
    val password: Property<String> = objectFactory.property(String::class.java)

    init {
        group = "icm container mssql"
        filename.convention(FILENAME)

        // the task is not incremental!
        outputs.upToDateWhen { false }
    }

    protected fun checkInputParams() {
        if(jdbcUrl.get().isNullOrBlank() || user.get().isNullOrBlank() || password.get().isNullOrBlank()) {
            logger.error("The connection paramater are not complete. (url: ${jdbcUrl.get()}, username: ${user.get()}")
            throw GradleException("The connection paramater are not complete. (url: ${jdbcUrl.get()}, username: ${user.get()}")
        }
    }

    protected fun getDBNameFrom(jdbcURL: String): String? {
        return jdbcURL.split(";").
                findLast { it.startsWith("databaseName") }?.split("=")?.last()
    }

    protected fun statement(url: String, user: String, password: String): Statement {
        Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver")
        val connection = DriverManager.getConnection(url, user, password)
        val stmt = connection.createStatement()
        return stmt
    }

    @Throws(SQLException::class)
    protected fun jdbcRequest(req: String, url: String, user: String, password: String){
        val stmt = statement(url, user, password)
        stmt.executeUpdate(req)
        stmt.connection.close()
    }
}
