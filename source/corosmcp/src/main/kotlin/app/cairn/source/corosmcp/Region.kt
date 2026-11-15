package app.cairn.source.corosmcp

enum class Region(val label: String, val host: String) {
    US("US", "https://mcpus.coros.com"),
    EU("EU", "https://mcpeu.coros.com"),
    CN("CN", "https://mcpcn.coros.com"),
}

class CorosException(message: String) : Exception(message)
