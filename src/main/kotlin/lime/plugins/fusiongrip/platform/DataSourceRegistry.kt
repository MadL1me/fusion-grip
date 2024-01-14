package lime.plugins.fusiongrip.platform

import com.intellij.database.dataSource.*
import com.intellij.openapi.project.Project

data class IdeDataSource(
    val sourceName: String,
    val host: String,
    val port: Int,
    val dbName: String,
    val dbType: DbType,
)

enum class DbType {
    Postgres,
    Unknown
}

//fun DbType.toForeignDataWrapper(): String {
//    when (this) {
//        DbType.Postgres -> "postgres_fdw"
//    }
//}

object DataSourceRegistry {
    fun getIdeDataSources(project: Project): List<IdeDataSource> {
        val storage = DataSourceStorage.getProjectStorage(project)

        val ideSources = storage.dataSources.map { IdeDataSource(
            it.sourceName,
            it.name,
            6532,
            "", // somehow get from jdbc?
            driverToDbType(it.driverClass)
        )}

        return ideSources;
    }

    fun createLocalDataSource(host: String,   project: Project) {
        val storage = DataSourceStorage.getProjectStorage(project)

        val dataSource = LocalDataSource()

        storage.addDataSource(dataSource)
    }

    private fun driverToDbType(driver: String): DbType {
        return when (driver) {
            "POSTGRESQL" -> DbType.Postgres
            else -> DbType.Unknown
        }
    }
}