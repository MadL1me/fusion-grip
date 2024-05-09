package lime.plugins.fusiongrip.platform

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

fun String.validSourceName(): String {
    return this.replace(Regex("[!@#$%^&*()+=\\- ]"), "_")
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
            it.name,
            it.username,
            getHostFromPgConnectionString(it.url.toString().removePrefix("jdbc:")) ?: throw Exception("Failed to get host"),
            6532,
            it.url?.split('/')?.last() ?: throw Exception("Failed to get dbName"), // somehow get from jdbc?
            driverToDbType(it.databaseDriver?.name ?: "unknown")
        )}

        return ideSources;
    }

    fun createLocalDataSource(host: String,   project: Project) {
        val storage = DataSourceStorage.getProjectStorage(project)

        val dataSource = LocalDataSource()

        storage.addDataSource(dataSource)
    }

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