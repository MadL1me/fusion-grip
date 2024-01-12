package lime.plugins.fusiongrip.tasks

import com.intellij.openapi.project.Project
import lime.plugins.fusiongrip.cli.CliCommand
import lime.plugins.fusiongrip.database.FuseDatabaseConnector
import lime.plugins.fusiongrip.datagrip.DatasourceRegistry

class FuseSourcesTask {
    fun action(project: Project): Pair<Boolean, String> {
        try {
            Class.forName("org.postgresql.Driver")
            var result: Int? = CliCommand.createDockerPostgres().run()
                ?: return Pair(false, "Failed to run result")

            DatasourceRegistry.getIntellijDatasources(project)

            var connection = FuseDatabaseConnector.getPostgresConnection(
                "localhost",
                5432, "postgres", "postgres", "postgres"
            )

            connection.createExtensionIfNotExists()

            return Pair(false, "Failed to run result")
        }
        catch (e: Exception) {
            throw e;
        }
    }
}