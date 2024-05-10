package lime.plugins.fusiongrip.ide.tasks

import com.intellij.openapi.project.Project
import lime.plugins.fusiongrip.cli.CliCommand
import lime.plugins.fusiongrip.config.GenerationConfig
import lime.plugins.fusiongrip.database.*
import lime.plugins.fusiongrip.ide.datasource.DataSourceRegistry
import lime.plugins.fusiongrip.ide.datasource.DbType
import lime.plugins.fusiongrip.ide.datasource.IdeDataSource
import lime.plugins.fusiongrip.ide.datasource.toValidSourceName
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

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
    fun action(project: Project, config: GenerationConfig): Pair<Boolean, String> {
        try {
            // Step 1 - Start docker
            var resultCode = CliCommand.createDockerPostgres().run()

            // Remote all old setups:
            pruneDatabase()

            var local = LocalDbFactory.getLocalDb(DbConstants.DEFAULT_DB_NAME)
            local.createFdwExtensionIfNotExists()

            // Step 2 - Get Postgres(for now) Sources from IDE
            val sources = DataSourceRegistry.getIdeDataSources(project);
            val filteredSources = sources.filter {
                config.sourceNameRegex.matches(it.sourceName) && it.dbType != DbType.Unknown
            }

            var remoteServers = mutableListOf<RemoteDbServer>()

            // Step 3 - Foreach source combine with FDW
            for (source in filteredSources) {
                val remoteDb = RemoteDbFactory.getRemoteDb(source)
                val server = createLocalForeignServer(local, source)

                remoteServers.add(RemoteDbServer(source, remoteDb, server))
            }

            val serverMap = getAllServersMap(remoteServers)

            for (schemaGroupKey in serverMap) {
                val firstServer = schemaGroupKey.value.first()
                val localForeignSchema = "${firstServer.ideSource.sourceName}_${schemaGroupKey.key.schema}".toValidSourceName()

                importForeignCustomEnums(firstServer.remoteRepo, local)

                // TODO: Кидать ошибку что такой сервернейм уже есть и надо либо помнеять либо
                // вызвать тулзу в для другой папки, чтобы не было коллизий
                local.createSchemaIfNotExists(CreateSchemaCmd(localForeignSchema))

                val beforeForeignTables = local.getForeignTables(localForeignSchema)
                for (table in beforeForeignTables) {
                    local.dropForeignTableIfExists(localForeignSchema, table)
                }

                local.importForeignSchema(ImportForeignSchemaCmd(
                    schemaGroupKey.key.schema,
                    firstServer.foreignServer.serverName,
                    localForeignSchema,
                    schemaGroupKey.key.tables.joinToString(",") { "\"${it.table}\"" }
                ))

                val localFinalSchema = formatSourceNameForDataSource(localForeignSchema)
                local.createSchemaIfNotExists(CreateSchemaCmd(localFinalSchema))

                val foreignTables = local.getForeignTables(localForeignSchema)

                for (foreignTable in foreignTables) {
                    local.createRealTableCopy(foreignTable, localForeignSchema, localFinalSchema)
                }

                for (remoteServer in schemaGroupKey.value.withIndex()) {
                    for (table in schemaGroupKey.key.tables) {
                        local.createInheritedForeignTable(
                            "\"${schemaGroupKey.key.schema}.${table.table}_${remoteServer.value.foreignServer.serverName}\"",
                            table.table,
                            "${localFinalSchema}.\"${table.table}\"",
                            remoteServer.value.foreignServer.serverName)
                    }
                }
            }

            //createIdeDbDataSource()

            return Pair(false, "Failed to run result")
        }
        catch (e: Exception) {
            throw e
        }
    }

    private fun pruneDatabase() {
        val db = LocalDbFactory.getLocalDb(DbConstants.POSTGRES_DB_NAME)
        db.dropDatabase(DbConstants.DEFAULT_DB_NAME)
        db.createDatabase(DbConstants.DEFAULT_DB_NAME)
    }

    private fun formatSourceNameForDataSource(input: String): String {
        return input.replace("\\d+".toRegex(), "") + "_combined"
    }

    private fun importForeignCustomEnums(remoteDb: RemoteDbRepository, local: LocalDbRepository) {
        val enums = remoteDb.selectCustomEnums()

        val enumsDefinitions = enums
            .groupBy { it.type }
            .map { PgEnumDefinition(it.key, it.value.map { v -> v.label }) }

        for (definition in enumsDefinitions) {
            local.importCustomEnum(ImportCustomEnumCmd(definition))
        }
    }

    private fun getAllServersMap(remoteDbs: Collection<RemoteDbServer>): Map<SchemaGroupKey, List<RemoteDbServer>> {
        val hashset = HashMap<SchemaGroupKey, MutableList<RemoteDbServer>>()

        for ((source, db, serverDef) in remoteDbs) {
            val schemas = db.selectUserDefinedSchemas()
            val tables = db.selectTableDefenitions(schemas)

            for (table in tables.groupBy { it.schema }) {
                val key = SchemaGroupKey(table.key, table.value)

                if (!hashset.containsKey(key)) {
                    hashset[key] = ArrayList()
                }

                hashset[key]?.add(RemoteDbServer(source, db, serverDef))
            }
        }

        return hashset
    }

    data class SchemaUnitDef(
        val dbname: String,
        val schema: String,
        val tables: List<String>,
        val source: IdeDataSource
    )

    data class SchemaGroupKey(
        val schema: String,
        val tables: List<TableDef>
    )

    private fun foo(local: LocalDbRepository, remoteDb: RemoteDbRepository, source: IdeDataSource) {
        val validSourceServerName = source.sourceName.replace(Regex("[!@#$%^&*()+=\\- ]"), "_")

        for (schema in remoteDb.selectUserDefinedSchemas()) {
            val validDbName = source.dbName.replace(Regex("[!@#$%^&*()+=\\- ]"), "_")
            val localSchema = "${validDbName}_$schema"

            local.createSchemaIfNotExists(CreateSchemaCmd(localSchema))

            // (shard1: bucket_1)
            // (shard1: bucket_2)

            // (shard2: bucket_3)
            // (shard2: bucket_4)

            // get all schemas from shard 1
            // get all schemas from shard 2
            // [shard1, bucket1, list<table_name>]
            // [shard1, bucket2, list<table_name>]
            // [shard2, bucket3, list<table_name>]
            // [shard2, bucket4, list<table_name>]

            // merge schemas if possible:
            // [hash] => bucket1, bucket2, bucket3, bucket4
            // or
            // [hash_server1] => bucket1, bucket2
            // [hash_server2] => bucket3, bucket4

            // foreach hash:
            // create one hash-schema on local connection: server_x-bucket_x
            // import foreign schema to new hash-schema
            // foreach foreign table create real table with no data to new hash-schema
            // delete all foreign tables
            // foreach value in [hash] list:
            // create tables as inherited from created ones

            // CREATE TABLE fuck AS TABLE batching_manager_public.batch_task WITH NO DATA;

            // CREATE FOREIGN TABLE shard1_table () INHERITS (public.batch_task_second)
            //    SERVER prod__batching_manager
            //    OPTIONS (table_name 'batch_task');

//            local.importForeignSchema(ImportForeignSchemaCmd(
//                schema,
//                validSourceServerName,
//                localSchema,
//            ))
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

    private fun createLocalForeignServer(local: LocalDbRepository, source: IdeDataSource): ForeignServerDef {
        val credentialsProvider = CredentialsProvider.getPgPassProvider()
        val creds = credentialsProvider.getCredentialsForDataSource(source)
        val fdwName = "postgres_fdw"

        val validSourceServerName = source.validSourceName()

        local.createForeignServer(CreateForeignServerCmd(
            validSourceServerName,
            fdwName,
            source.host,
            source.port,
            source.dbName,
        ))

        local.createUserMapping(CreateUserMappingCmd(
            DbConstants.ADMIN_PASS,
            validSourceServerName,
            creds.username,
            creds.password,
        ))

        local.grantUsage(GrantForeignServerUsageCmd(
            DbConstants.ADMIN_PASS,
            validSourceServerName,
        ))

        return ForeignServerDef(
            validSourceServerName,
            fdwName,
            source.host,
            source.port,
            source.dbName,
        )

//        for (schema in remoteDb.selectUserDefinedSchemas()) {
//            val validDbName = source.dbName.replace(Regex("[!@#$%^&*()+=\\- ]"), "_")
//            val localSchema = "${validDbName}_$schema";
//
//            local.createSchemaIfNotExists(CreateSchemaCmd(localSchema))
//
//            // (shard1: bucket_1)
//            // (shard1: bucket_2)
//
//            // (shard2: bucket_3)
//            // (shard2: bucket_4)
//
//            // get all schemas from shard 1
//            // get all schemas from shard 2
//            // [shard1, bucket1, list<table_name>]
//            // [shard1, bucket2, list<table_name>]
//            // [shard2, bucket3, list<table_name>]
//            // [shard2, bucket4, list<table_name>]
//
//            // merge schemas if possible:
//            // [hash] => bucket1, bucket2, bucket3, bucket4
//            // or
//            // [hash_server1] => bucket1, bucket2
//            // [hash_server2] => bucket3, bucket4
//
//            // foreach hash:
//                // create one hash-schema on local connection: server_x-bucket_x
//                // import foreign schema to new hash-schema
//                // foreach foreign table create real table with no data to new hash-schema
//                // delete all foreign tables
//                // foreach value in [hash] list:
//                    // create tables as inherited from created ones
//
//            // CREATE TABLE fuck AS TABLE batching_manager_public.batch_task WITH NO DATA;
//
//            // CREATE FOREIGN TABLE shard1_table () INHERITS (public.batch_task_second)
//            //    SERVER prod__batching_manager
//            //    OPTIONS (table_name 'batch_task');
//
//            local.importForeignSchema(ImportForeignSchemaCmd(
//                schema,
//                validSourceServerName,
//                localSchema,
//            ))
//        }
    }
}

data class ForeignServerDef(
    val serverName: String,
    val fdwName: String,
    val host: String,
    val port: Int,
    val dbName: String
)

data class RemoteDbServer(
    val ideSource: IdeDataSource,
    val remoteRepo: RemoteDbRepository,
    val foreignServer: ForeignServerDef
)

data class ForeignTableDef(
    val server: String,
    val schema: String,
    val table:  String,
)