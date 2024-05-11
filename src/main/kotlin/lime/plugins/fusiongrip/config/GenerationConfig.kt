package lime.plugins.fusiongrip.config

data class GenerationConfig(
    val sourceNameRegex: Regex,
    val groupName: String,
    val localDbName: String,
    val mergeSchemas: Boolean,
    val mergeDatabases: Boolean,
)