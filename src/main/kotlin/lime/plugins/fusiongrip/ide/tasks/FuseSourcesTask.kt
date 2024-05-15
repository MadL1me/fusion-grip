package lime.plugins.fusiongrip.ide.tasks

import com.intellij.openapi.project.Project
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.ui.Messages

import lime.plugins.fusiongrip.cli.CliCommand
import lime.plugins.fusiongrip.config.GenerationConfig
import lime.plugins.fusiongrip.database.*
import lime.plugins.fusiongrip.ide.datasource.DataSourceRegistry
import lime.plugins.fusiongrip.logic.AutoFdwSetupTask

class DataSourceProcessor(
    project: Project?,
    private val selectedSources: List<String>,
    private val mergeBases: Boolean,
    private val mergeSchemas: Boolean
) : Task.Backgroundable(project, "Processing Data Sources", true) {

    override fun run(indicator: ProgressIndicator) {
        // Example task logic
        indicator.isIndeterminate = false
        val total = selectedSources.size
        selectedSources.forEachIndexed { index, dataSource ->
            if (indicator.isCanceled) return
            indicator.text = "Processing $dataSource"
            // Simulate some processing work
            Thread.sleep(1000)  // This is just a placeholder for actual processing logic
            indicator.fraction = (index + 1) / total.toDouble()
        }
        if (mergeBases) {
            // Additional processing for merging bases
        }
        if (mergeSchemas) {
            // Additional processing for merging schemas
        }
    }

    override fun onSuccess() {
        // What to do after task successfully completes
        Messages.showMessageDialog(project, "Data sources processed successfully.", "Task Completed", Messages.getInformationIcon())
    }

    override fun onThrowable(error: Throwable) {
        // Handle any exceptions thrown during the task
        Messages.showErrorDialog(project, "Error processing data sources: ${error.message}", "Error")
    }
}

class FuseSourcesTask {
    fun action(project: Project, config: GenerationConfig): Pair<Boolean, String> {
        try {
            // Step 1 - Start docker
            var resultCode = CliCommand.createDockerPostgres().run()

            // Remote all old setups:
            pruneDatabase()

            // Step 2 - Get Postgres(for now) Sources from IDE
            val sources = DataSourceRegistry.getIdeDataSources(project);

            return AutoFdwSetupTask().setupFdwForSourceList(sources, config)
        } catch (e: Exception) {
            throw e
        }
    }

    private fun pruneDatabase() {
        val db = LocalDbFactory.getLocalDb(LocalDbConstants.POSTGRES_DB_NAME)
        db.dropDatabase(LocalDbConstants.DEFAULT_DB_NAME)
        db.createDatabase(LocalDbConstants.DEFAULT_DB_NAME)
    }
}