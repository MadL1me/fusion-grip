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

data class CreateSchemaCmd(
    val schemaName: String,
)

data class CreateDatabaseCmd(
    val databaseName: String,
)

data class ImportCustomEnumCmd(
    val enum: PgEnumDefinition
)

class LocalDatabaseRepository(val connection: Connection) {
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
        requireValidName("host", cmd.host)
        requireValidName("port", cmd.port.toString())
        requireValidName("dbName", cmd.dbName)

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
        requireValidName("LocalSchema", cmd.localSchema)
        requireValidName("ForeignSchema", cmd.foreignSchema)

        val sql = """
            IMPORT FOREIGN SCHEMA ${cmd.foreignSchema}
                FROM SERVER ${cmd.serverName} INTO ${cmd.localSchema};
        """.trimIndent()

        val statement = connection.createStatement()
        return statement.execute(sql)
    }

    fun createSchemaIfNotExists(cmd: CreateSchemaCmd): Boolean {
        requireValidName("SchemaName", cmd.schemaName)

        val sql = """
            CREATE SCHEMA IF NOT EXISTS ${cmd.schemaName};
        """.trimIndent()

        val statement = connection.createStatement()
        return statement.execute(sql)
    }

    fun importCustomEnum(cmd: ImportCustomEnumCmd): Boolean {
        if (isTypeExists(cmd.enum.typeName)) {
            return true
        }

        val labelsString = cmd.enum.labels.joinToString { "'$it'" }

        val sql = """
            CREATE TYPE ${cmd.enum.typeName} AS ENUM ($labelsString);
        """.trimIndent()

        val statement = connection.createStatement()
        return statement.execute(sql)
    }

    private fun isTypeExists(typename: String): Boolean {
        val sql = """
            select exists (select 1 from pg_type where pg_type.typname = '$typename');
        """.trimIndent()

        val statement = connection.createStatement()
        val result = statement.executeQuery(sql)

        result.next()

        return result.getBoolean(1)
    }

    fun createDatabase(cmd: CreateSchemaCmd): Boolean {
        requireValidName("SchemaName", cmd.schemaName)

        val sql = """
            CREATE SCHEMA IF NOT EXISTS ${cmd.schemaName};
        """.trimIndent()

        val statement = connection.createStatement()
        return statement.execute(sql)
    }

    private fun requireValidName(propName: String, value: String) {
        require(value.validSqlTableName()) { "Invalid $propName - $value should satisfy ^[a-zA-Z0-9_.-]+\$ regex"}
    }

    fun updateUserMapping(cmd: UpdateUserMappingCmd) {

    }

    fun deleteFdw() {

    }

    fun grantUsageForUser() {

    }

    fun createDatabase(cmd: CreateDatabaseCmd): Boolean {
        requireValidName("DatabaseName", cmd.databaseName)

        val sql = """
            CREATE DATABASE ${cmd.databaseName}
        """.trimIndent()

        val statement = connection.createStatement()
        return statement.execute(sql)
    }
}

object DbConnectionFactory {
    fun getPostgresConnection(host: String, port: Int, database: String, user: String, password: String): Connection {
        Class.forName("org.postgresql.Driver")

        val url = "jdbc:postgresql://$host:$port/$database"

        return DriverManager.getConnection(url, user, password);
    }
}

class RemoteDbRepository (private val connection: Connection) {
    fun selectUserDefinedSchemas(): List<String> {
        val sql = """
            SELECT nspname AS schema_name
            FROM pg_catalog.pg_namespace
            WHERE nspname NOT IN ('pg_catalog', 'information_schema')
              AND nspname !~ '^pg_toast'
              AND nspname !~ '^pg_temp_';
        """.trimIndent()

        val statement = connection.createStatement()
        val results = statement.executeQuery(sql)

        val schemaNames = mutableListOf<String>()

        while (results.next()) {
            val schemaName = results.getString("schema_name")
            schemaNames.add(schemaName)
        }

        return schemaNames
    }

    fun selectCustomEnums(): List<EnumMapping> {
        val sql = """
            SELECT pg_type.typname AS enumtype,
            pg_enum.enumlabel AS enumlabel
            FROM pg_type
                JOIN pg_enum
                    ON pg_enum.enumtypid = pg_type.oid;
        """.trimIndent()

        val statement = connection.createStatement()
        val results = statement.executeQuery(sql)

        val mappings = mutableListOf<EnumMapping>()

        while (results.next()) {
            val type = results.getString("enumtype")
            val label = results.getString("enumlabel")
            mappings.add(EnumMapping(type, label))
        }

        return mappings
    }
}

data class EnumMapping(
    val type: String,
    val label: String
)

data class PgEnumDefinition(
    val typeName: String,
    val labels: List<String>
)

//fun String.validSqlTableName(): Boolean {
//    val regex = Regex("^[a-zA-Z0-9_-]+\$")
//    return this.matches(regex)
//}

fun String.validSqlTableName(): Boolean {
    val regex = Regex("^[a-zA-Z0-9_.-]+\$")
    return this.matches(regex)
}