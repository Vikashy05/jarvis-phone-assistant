package dev.local.jarvis

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import java.util.Locale

internal const val YOUTUBE = "com.google.android.youtube"

internal sealed interface Command {
    data class Open(val app: String) : Command
    data class Search(val query: String) : Command
}

internal object Commands {
    private val apps = mapOf(
        "youtube" to YOUTUBE,
        "chrome" to "com.android.chrome",
        "maps" to "com.google.android.apps.maps",
        "whatsapp" to "com.whatsapp"
    )
    private val names = mapOf("you tube" to "youtube", "google maps" to "maps", "google chrome" to "chrome", "what's app" to "whatsapp")
    private val prefix = Regex("""^(?:(?:hey\s+)?jarvis\b[,.:!]?\s*)""", RegexOption.IGNORE_CASE)
    private val search = Regex("""(?:open you\s?tube and search for|search you\s?tube for)\s+(.+)""", RegexOption.IGNORE_CASE)
    private val open = Regex("""(?:open|launch)\s+(.+)""", RegexOption.IGNORE_CASE)

    fun parse(input: String): Command? {
        val phrase = input.trim().replace(Regex("""\s+"""), " ").replaceFirst(prefix, "").trimEnd('.', '!', '?').trim()
        search.matchEntire(phrase)?.let { return Command.Search(it.groupValues[1]) }
        val name = open.matchEntire(phrase)?.groupValues?.get(1)?.lowercase(Locale.ROOT) ?: return null
        val app = names[name] ?: name
        return if (app in apps || app == "settings" || app == "camera") Command.Open(app) else null
    }

    fun intent(context: Context, command: Command): Intent? = when (command) {
        is Command.Search -> {
            val url = Uri.parse("https://www.youtube.com/results").buildUpon()
                .appendQueryParameter("search_query", command.query).build()
            Intent(Intent.ACTION_VIEW, url).setPackage(YOUTUBE)
        }
        is Command.Open -> when (command.app) {
            "settings" -> Intent(Settings.ACTION_SETTINGS)
            "camera" -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            else -> apps[command.app]?.let(context.packageManager::getLaunchIntentForPackage)
        }
    }
}
