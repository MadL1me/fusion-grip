package lime.plugins.fusiongrip.datagrip

import com.intellij.database.dataSource.DataSourceStorage
import com.intellij.openapi.project.Project

data class DataSource(val dbName: String)

enum class DbType {
    Postgres,
    Clickhouse,
    Unknown
}

object DatasourceRegistry {

    fun getIntellijDatasources(project: Project) {
        val sources = DataSourceStorage.getProjectStorage(project)

        sources.dataSources.map {
            it.username
            it.passwordStorage.serialize()
        }

        val a = 4;
        //DataSourceStorage.
    }
}