package lime.plugins.fusiongrip.ide.datasource

import com.intellij.database.dataSource.*
import com.intellij.openapi.project.Project

data class IdeDataSource(
    val sourceName: String,
    val userName: String,
    val host: String,
    val port: Int,
    val dbName: String,
    val dbType: DbType,
) {
    fun validSourceName(): String {
        return this.sourceName.replace(Regex("[!@#$%^&*()+=\\- ]"), "_")
    }
}

enum class DbType {
    Postgres,
    Unknown
}

fun String.toValidSourceName(): String {
    return this.replace(Regex("[!@#$%^&*()+=\\- ]"), "_")
}

object DataSourceRegistry {
    private const val POSTGRES_DRIVER = "org.postgresql.Driver"

    private const val scopeTemplate =
        """
          <schema-mapping>
            <introspection-scope>
              <node kind="database" qname="%s">
                <node kind="schema" negative="1" condition="%s" />
              </node>
            </introspection-scope>
          </schema-mapping>
        """

    fun getIdeDataSources(project: Project): List<IdeDataSource> {
        val storage = DataSourceStorage.getProjectStorage(project)

        val ideSources = storage.dataSources.map {
            val components = extractJdbcComponents(it.url.toString())
                ?: throw Exception("Cannot parse jdbc url, it should satisfy following regex: ^jdbc:postgresql://([^:]+):(\\d+)/([^/]+)\$")

            IdeDataSource(
                it.name,
                it.username,
                components.host,
                components.port,
                components.dbName,
                driverToDbType(it.databaseDriver?.name ?: "unknown")
        )}

        return ideSources
    }

//    fun createIdeDbDataSource(project: Project, source: IdeDataSource) {
//        val store = DataSourceStorage.getProjectStorage(project)
//
//        val jdbc = getJdbcUrl(source)
//
//        val stringReader = StringReader(String.format(scopeTemplate, source.dbName, ".*"))
//        val reader: HierarchicalStreamReader = StaxDriver().createReader(stringReader)
//        val schema = DataSourceSchemaMapping()
//        schema.deserialize(reader)
//
//        val newDataSource = LocalDataSource.create(
//            "fusion-source",
//            POSTGRES_DRIVER,
//            jdbc,
//            DbConstants.ADMIN_LOGIN,
//        )
//
//        val postgresDriver = DatabaseDriverManager.getInstance().getDriver("postgresql")
//
//        //newDataSource.introspectionScope = schemaPattern
//        newDataSource.authProviderId = "pgpass"
//        newDataSource.databaseDriver = postgresDriver
//        newDataSource.schemaMapping = schema
//        newDataSource.isAutoSynchronize = true
//        newDataSource.groupName = "${config.groupPrefix}"
//
//        store.addDataSource(newDataSource)
//    }
//
//    private fun getJdbcUrl(source: IdeDataSource): String {
//        return "jdbc:postgresql://${source.host}}:${source.port}/${source.dbName}"
//    }

    private fun extractJdbcComponents(jdbcUrl: String): JdbcComponents? {
        val pattern = Regex("^jdbc:postgresql://([^:]+):(\\d+)/([^/]+)$")
        val matchResult = pattern.matchEntire(jdbcUrl)

        return matchResult?.let {
            JdbcComponents(
                it.groupValues[1],
                it.groupValues[2].toInt(),
                it.groupValues[3]
            )
        }
    }

    private data class JdbcComponents(
        val host: String,
        val port: Int,
        val dbName: String,
    )

    private fun getHostFromPgConnectionString(connString: String): String? {
        val pattern = Regex("postgresql://(?:[^:@/]*:?[^:@/]*@)?([^:/?]+)")
        val matchResult = pattern.find(connString)
        return matchResult?.groups?.get(1)?.value
    }

    private fun driverToDbType(driver: String): DbType {
        return when (driver) {
            "PostgreSQL" -> DbType.Postgres
            else -> DbType.Unknown
        }
    }
}