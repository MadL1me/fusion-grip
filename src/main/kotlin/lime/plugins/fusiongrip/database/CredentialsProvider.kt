package lime.plugins.fusiongrip.database

import lime.plugins.fusiongrip.ide.datasource.IdeDataSource
import java.io.File

data class DbConnectionCredentials(
    val username: String,
    val password: String,
)

interface CredentialsProvider {
    fun getCredentialsForDataSource(source: IdeDataSource): DbConnectionCredentials

    companion object Factory {
        fun getPgPassProvider(): CredentialsProvider {
            return SingleCachedPgPassCredentialsProvider()
        }
    }
}

class SingleCachedPgPassCredentialsProvider : CredentialsProvider {
    private var pgPassCreds: DbConnectionCredentials? = null

    override fun getCredentialsForDataSource(source: IdeDataSource): DbConnectionCredentials {
        if (pgPassCreds != null) {
            return pgPassCreds as DbConnectionCredentials
        }

        val pgpassFile = File(System.getProperty("user.home"), ".pgpass")
        if (!pgpassFile.exists()) {
            throw IllegalArgumentException("~/.pgpass file not found")
        }

        val credentials = pgpassFile.readLines().filter { it.trim().isNotEmpty() }.map { line ->
            val parts = line.split(":")
            var a = line.isEmpty()
            if (parts.size != 5) {
                throw IllegalArgumentException("Invalid line format: $line")
            }

            return@map DbConnectionCredentials(parts[3], parts[4])
        }

        pgPassCreds = credentials.first()
        return pgPassCreds as DbConnectionCredentials
    }
}