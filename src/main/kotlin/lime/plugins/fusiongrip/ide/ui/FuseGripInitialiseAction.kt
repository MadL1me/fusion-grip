package lime.plugins.fusiongrip.ide.ui

import com.intellij.database.dataSource.DataSourceStorage
import com.intellij.database.dataSource.rawDataSource
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.DialogWrapper
import org.jetbrains.annotations.NotNull

class FuseGripInitialiseAction : AnAction() {
    override fun update(@NotNull event: AnActionEvent) {
        // Using the event, evaluate the context,
        // and enable or disable the action.
    }

    override fun actionPerformed(@NotNull event: AnActionEvent) {
        val project = event.project

        val storage = DataSourceStorage.getProjectStorage(project)

        val ideSources = storage.dataSources.mapNotNull { it.rawDataSource }

        //val ideDataSources = event.dataContext.getSelectedDataSources().toList()

        val dialog = FuseGripInitialisationDialog(project, ideSources)
        dialog.show()

        if (dialog.exitCode == DialogWrapper.OK_EXIT_CODE) {
            // Handle the case when OK is pressed
        }
    }
}