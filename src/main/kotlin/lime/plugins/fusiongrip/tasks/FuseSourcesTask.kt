package lime.plugins.fusiongrip.tasks

import com.intellij.openapi.project.Project
import lime.plugins.fusiongrip.cli.CliCommand
import lime.plugins.fusiongrip.config.GenerationConfig
import lime.plugins.fusiongrip.database.*
import lime.plugins.fusiongrip.platform.DataSourceRegistry
import lime.plugins.fusiongrip.platform.DbType
import lime.plugins.fusiongrip.platform.IdeDataSource

class FuseSourcesTask {
    private val LOCALHOST = "localhost"
    private val LOCALPORT = 5432

    private val ADMIN = "postgres"

    fun action(project: Project, config: GenerationConfig): Pair<Boolean, String> {
        try {
            // Step 1 - Start docker
            var resultCode = CliCommand.createDockerPostgres().run()

            var local = LocalDatabaseRepository.getPostgresConnection(
                LOCALHOST,
                LOCALPORT,
                "db",
                ADMIN,
                ADMIN
            )
            local.createExtensionIfNotExists()

            // Step 2 - Get Postgres(for now) Sources from IDE
            val sources = DataSourceRegistry.getIdeDataSources(project)
                .filter {
                    config.sourceNameRegex.matches(it.sourceName) && it.dbType != DbType.Unknown
                 }

            // Step 3 - Foreach source combine with FDW
            for (source in sources) {
                val success = createLocalSourceConnection(local, source)
            }

            return Pair(false, "Failed to run result")
        }
        catch (e: Exception) {
            throw e;
        }
    }

    private fun createLocalSourceConnection(local: LocalDatabaseRepository, source: IdeDataSource): Boolean {
        val credentialsProvider = CredentialsProvider.getPgPassProvider()
        val creds = credentialsProvider.getCredentialsForDataSource(source)
        val fdwName = "postgres_fdw"

        val validServerName = source.sourceName.replace(Regex("[!@#$%^&*()+=\\- ]"), "_")

        local.createForeignServer(CreateForeignServerCmd(
            validServerName,
            fdwName,
            source.host, //"pg-entrypoint-service.prod.h.o3.ru",
            source.port, // 6532
            source.dbName, // batching-manager
        )) // 1705240160gZmfe67d106RdWBVCW9fYJTkuKZbkEF7pfMB2BSDRil300ct1AqhDnQUQqsAWI7XJDaO5rTJ6OP5QeMDKl7RdQ

        local.createUserMapping(CreateUserMappingCmd(
            ADMIN,
            validServerName,
            creds.username,
            creds.password,
        ))

        local.grantUsage(GrantForeignServerUsageCmd(
            ADMIN,
            validServerName,
        ))

        local.importForeignSchema(ImportForeignSchemaCmd(
            "public",
            validServerName,
            "public",
        ))

        return true
    }
}