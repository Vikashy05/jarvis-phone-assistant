package dev.local.jarvis

internal data class AgentAction(val verb: String, val index: Int? = null, val argument: String = "") {
    companion object {
        fun parse(raw: String): AgentAction? {
            val line = raw.trim()
            if (line.any { it == '\n' || it == '\r' } || line.length > 220) return null
            val verb = line.substringBefore(' ').uppercase()
            val value = line.substringAfter(' ', "").trim()
            return when (verb) {
                "MUTE", "UNMUTE", "HOME", "BACK", "DONE", "STOP" -> if (value.isEmpty()) AgentAction(verb) else null
                "OPEN", "SEARCH" -> if (value.isNotEmpty()) AgentAction(verb, argument = value) else null
                "TAP", "SCROLL" -> value.toIntOrNull()?.takeIf { it in 1..24 }?.let { AgentAction(verb, it) }
                "TYPE" -> {
                    val index = value.substringBefore(' ').toIntOrNull() ?: return null
                    val content = value.substringAfter(' ', "").trim()
                    if (index in 1..24 && content.isNotEmpty()) AgentAction(verb, index, content) else null
                }
                else -> null
            }
        }
    }
}
