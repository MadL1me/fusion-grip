package lime.plugins.fusiongrip.tasks

import com.intellij.database.dataSource.DataSourceSchemaMapping
import com.intellij.database.dataSource.DataSourceStorage
import com.intellij.database.dataSource.DatabaseDriverManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.project.Project
import com.thoughtworks.xstream.io.HierarchicalStreamReader
import com.thoughtworks.xstream.io.xml.StaxDriver
import lime.plugins.fusiongrip.cli.CliCommand
import lime.plugins.fusiongrip.config.GenerationConfig
import lime.plugins.fusiongrip.database.*
import lime.plugins.fusiongrip.platform.DataSourceRegistry
import lime.plugins.fusiongrip.platform.DbType
import lime.plugins.fusiongrip.platform.IdeDataSource
import java.io.StringReader

val scopeTemplate = """
  <schema-mapping>
    <introspection-scope>
      <node kind="database" qname="%s">
        <node kind="schema" negative="1" condition="%s" />
      </node>
    </introspection-scope>
  </schema-mapping>
""".trimIndent()


class FuseSourcesTask {
    private val LOCALHOST = "localhost"
    private val PGPORT = 5432
    private val ADMIN_LOGIN = "postgres"
    private val ADMIN_PASS = "postgres"

    fun action(project: Project, config: GenerationConfig): Pair<Boolean, String> {
        try {
            // Step 1 - Start docker
            var resultCode = CliCommand.createDockerPostgres().run()

            var local = LocalDatabaseRepository(DbConnectionFactory.getPostgresConnection(
                LOCALHOST,
                PGPORT,
                "db",
                ADMIN_LOGIN,
                ADMIN_PASS
            ));

            local.createExtensionIfNotExists()

            // Step 2 - Get Postgres(for now) Sources from IDE
            val sources = DataSourceRegistry.getIdeDataSources(project);
            val filteredSources = sources.filter {
                config.sourceNameRegex.matches(it.sourceName) && it.dbType != DbType.Unknown
            }

            // Step 3 - Foreach source combine with FDW
            for (source in filteredSources) {
                val success = createLocalSourceConnection(local, source)
            }

            //createIdeDbDataSource()

            return Pair(false, "Failed to run result")
        }
        catch (e: Exception) {
            throw e;
        }
    }

//    private fun getJdbcUrl(source: IdeDataSource): String {
//        return "jdbc:postgresql://${source.host}}:${source.port}/${source.dbName}"
//    }
//
//    private fun createIdeDbDataSource(project: Project, source: IdeDataSource) {
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
//            "org.postgresql.Driver",
//            jdbc,
//            "postgres",
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

    private fun createLocalSourceConnection(local: LocalDatabaseRepository, source: IdeDataSource): Boolean {
        val credentialsProvider = CredentialsProvider.getPgPassProvider()
        val creds = credentialsProvider.getCredentialsForDataSource(source)
        val fdwName = "postgres_fdw"

        val validSourceServerName = source.sourceName.replace(Regex("[!@#$%^&*()+=\\- ]"), "_")

        local.createForeignServer(CreateForeignServerCmd(
            validSourceServerName,
            fdwName,
            source.host,
            source.port,
            source.dbName,
        ))

        local.createUserMapping(CreateUserMappingCmd(
            ADMIN_PASS,
            validSourceServerName,
            creds.username,
            creds.password,
        ))

        local.grantUsage(GrantForeignServerUsageCmd(
            ADMIN_PASS,
            validSourceServerName,
        ))

        val remoteDb = RemoteDbRepository(DbConnectionFactory.getPostgresConnection(
            source.host,
            source.port,
            source.dbName,
            creds.username,
            creds.password
        ))

        for (schema in remoteDb.selectUserDefinedSchemas()) {
            val validDbName = source.dbName.replace(Regex("[!@#$%^&*()+=\\- ]"), "_")
            val localSchema = "${validDbName}_$schema";

            local.createSchemaIfNotExists(CreateSchemaCmd(localSchema))

            local.importForeignSchema(ImportForeignSchemaCmd(
                schema,
                validSourceServerName,
                localSchema,
            ))
        }

        return true
    }
}
