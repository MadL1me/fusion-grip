package lime.plugins.fusiongrip.cli

import lime.plugins.fusiongrip.database.DbConstants
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class CliCommand private constructor (
    private val workingDir: String,
    private val command: String) {

     fun run(): Int? {
        try {
            val parts = command.split("\\s+".toRegex())
            val proc = ProcessBuilder(*parts.toTypedArray())
                .directory(File(workingDir))
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start()

            proc.waitFor(5, TimeUnit.MINUTES)
            return proc.exitValue()
        } catch(e: IOException) {
            e.printStackTrace()
            return null
        }
    }

    companion object Factory {
        fun createDockerPostgres() : CliCommand {
            return CliCommand(
                "/",
                ("""
                    docker run --name fuse-grip-postgres
                        -e POSTGRES_USER=${DbConstants.ADMIN_LOGIN}
                        -e POSTGRES_PASSWORD=${DbConstants.ADMIN_PASS}
                        -e POSTGRES_DB=${DbConstants.DEFAULT_DB_NAME} -p ${DbConstants.PGPORT}:5432
                        -d postgres:16.1
                 """.trimIndent()))
        }
    }
}