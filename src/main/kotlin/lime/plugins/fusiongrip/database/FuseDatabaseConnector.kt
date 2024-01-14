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
    val localUsername: String,
    val serverName: String,
    val foreignUser: String,
    val foreignPassword: String,
)

data class UpdateUserMappingCmd(
    val user: String,
    val password: String,
)

data class GrantForeignServerUsageCmd(
    val localUsername: String,
    val serverName: String,
)

data class ImportForeignSchemaCmd(
    val foreignSchema: String,
    val serverName: String,
    val localSchema: String,
)

class LocalDatabaseRepository private constructor(
    val connection: Connection
) {

    fun createSchema(schemaName: String) {

    }

    fun createDataWrapper() {

    }

    fun createForeignUser() {

    }

    fun createExtensionIfNotExists(): Boolean {
        val sql = "CREATE EXTENSION IF NOT EXISTS postgres_fdw;"
        val statement = connection.createStatement()

        return statement.execute(sql);
    }

    fun createForeignServer(cmd: CreateForeignServerCmd): Boolean {
        requireValidName("ServerName", cmd.serverName)
        requireValidName("FdwName", cmd.fdwName)
        requireValidName("host", cmd.fdwName)
        requireValidName("port", cmd.fdwName)
        requireValidName("dbName", cmd.fdwName)

        val sql = """
            CREATE SERVER IF NOT EXISTS ${cmd.serverName}
                FOREIGN DATA WRAPPER ${cmd.fdwName}
                OPTIONS (host '${cmd.host}', port '${cmd.port}', dbname '${cmd.dbName}');
        """.trimIndent()

        val statement = connection.createStatement()
        return statement.execute(sql)
    }

    fun createUserMapping(cmd: CreateUserMappingCmd): Boolean {
        requireValidName("ServerName", cmd.serverName)
        requireValidName("LocalUsername", cmd.localUsername)
        requireValidName("ForeignUser", cmd.foreignUser)
        requireValidName("ForeignPassword", cmd.foreignPassword)

        val sql = """
            CREATE USER MAPPING IF NOT EXISTS FOR ${cmd.localUsername} 
                SERVER ${cmd.serverName} 
                OPTIONS (user '${cmd.foreignUser}', password '${cmd.foreignPassword}');
        """.trimIndent()

        val statement = connection.createStatement()
        return statement.execute(sql)
    }

    fun grantUsage(cmd: GrantForeignServerUsageCmd): Boolean {
        requireValidName("ServerName", cmd.serverName)
        requireValidName("LocalUsername", cmd.localUsername)

        val sql = """
            GRANT USAGE ON FOREIGN SERVER ${cmd.serverName} TO ${cmd.localUsername};
        """.trimIndent()

        val statement = connection.createStatement()
        return statement.execute(sql)
    }

    fun importForeignSchema(cmd: ImportForeignSchemaCmd): Boolean {
        requireValidName("ServerName", cmd.serverName)
        requireValidName("ServerName", cmd.localSchema)
        requireValidName("ForeignSchema", cmd.foreignSchema)

        val sql = """
            IMPORT FOREIGN SCHEMA ${cmd.foreignSchema} 
                FROM SERVER ${cmd.serverName} INTO ${cmd.localSchema};
        """.trimIndent()

        val statement = connection.createStatement()
        return statement.execute(sql)
    }

    private fun requireValidName(propName: String, value: String) {
        require(value.validSqlTableName()) { "Invalid $propName - $value should satisfy ^[a-zA-Z0-9_]+\$ regex"}
    }

    fun updateUserMapping(cmd: UpdateUserMappingCmd) {

    }

    fun deleteFdw() {

    }

    fun grantUsageForUser() {

    }

    companion object Factory {
        fun getPostgresConnection(host: String, port: Int, database: String, user: String, password: String): LocalDatabaseRepository {
            Class.forName("org.postgresql.Driver")

            val url = "jdbc:postgresql://$host:$port/$database"
            val connection = DriverManager.getConnection(url, user, password)

            return LocalDatabaseRepository(connection)
        }
    }
}

fun String.validSqlTableName(): Boolean {
    val regex = Regex("^[a-zA-Z0-9_-]+\$")
    return this.matches(regex)
}