package lime.plugins.fusiongrip.database

import java.sql.Connection
import java.sql.DriverManager

data class CreateForeignServerCmd(
    val serverName: String,
    val fdwName: String,
    val host: String,
    val port: Int,
    val dbName: String,
)

data class CreateUserMappingCmd(
    val username: String,
    val serverName: String,
    val user: String,
    val password: String,
)

data class UpdateUserMappingCmd(
    val user: String,
    val password: String,
)

class FuseDatabaseConnector private constructor(
    val connection: Connection
) {

    fun createSchema(schemaName: String) {

    }

    fun createDataWrapper() {

    }

    fun createForeignUser() {

    }

    fun createExtensionIfNotExists() {
        val sql = "CREATE EXTENSION IF NOT EXISTS postgres_fdw;"
        val statement = connection.createStatement()

        val result = statement.executeQuery(sql)

//        while (result.next()) {
//            println(result.getString("column_name"))
//        }
    }

    fun createForeignServer(cmd: CreateForeignServerCmd) {

    }

    fun createUserMapping(cmd: CreateUserMappingCmd) {

    }

    fun updateUserMapping(cmd: UpdateUserMappingCmd) {

    }

    fun deleteFdw() {

    }

    fun grantUsageForUser() {

    }

    companion object Factory {
        fun getPostgresConnection(host: String, port: Int, database: String, user: String, password: String): FuseDatabaseConnector {
            val url = "jdbc:postgresql://$host:$port/$database"
            val connection = DriverManager.getConnection(url, user, password)
            return FuseDatabaseConnector(connection)
        }
    }
}