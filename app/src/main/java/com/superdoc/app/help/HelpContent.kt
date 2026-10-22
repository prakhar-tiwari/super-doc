package com.superdoc.app.help

import org.json.JSONArray
import org.json.JSONObject

data class LocalText(val en: String, val hi: String) {
    fun get(hindi: Boolean) = if (hindi) hi else en
}

/** Where the highlight goes, as fractions (0–1) of the screenshot's width and height. */
data class HelpBox(val left: Float, val top: Float, val right: Float, val bottom: Float)

data class HelpStep(val image: String, val text: LocalText)

data class HelpTopic(val id: String, val icon: String, val title: LocalText, val steps: List<HelpStep>)

/**
 * Help topics from `assets/help/help.json`, plus highlight boxes from `boxes.json`
 * (written by tools/help/capture.py from the real screen layout, per language).
 */
class HelpContent(val topics: List<HelpTopic>, private val boxes: Map<String, Map<String, HelpBox>>) {
    fun topic(id: String): HelpTopic? = topics.firstOrNull { it.id == id }

    /** Box for step [index] (0-based) of [topicId]; null when the step shows the whole screen. */
    fun box(lang: String, topicId: String, index: Int): HelpBox? = boxes[lang]?.get("${topicId}_${index + 1}")

    companion object {
        fun parse(helpJson: String, boxesJson: String?): HelpContent {
            val topics = JSONObject(helpJson).getJSONArray("topics").objects().map { t ->
                HelpTopic(
                    id = t.getString("id"),
                    icon = t.optString("icon", t.getString("id")),
                    title = t.getJSONObject("title").localText(),
                    steps = t.getJSONArray("steps").objects().map { s ->
                        HelpStep(image = s.getString("image"), text = s.getJSONObject("text").localText())
                    },
                )
            }
            val boxes = boxesJson?.let { json ->
                val root = JSONObject(json)
                root.keys().asSequence().associateWith { lang ->
                    val perLang = root.getJSONObject(lang)
                    perLang.keys().asSequence().associateWith { key ->
                        val a = perLang.getJSONArray(key)
                        HelpBox(a.getDouble(0).toFloat(), a.getDouble(1).toFloat(), a.getDouble(2).toFloat(), a.getDouble(3).toFloat())
                    }
                }
            }.orEmpty()
            return HelpContent(topics, boxes)
        }

        private fun JSONObject.localText() = LocalText(en = getString("en"), hi = getString("hi"))

        private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

        private val PLACEHOLDER = Regex("\\{([a-z0-9_]+)\\}")

        /** Replaces `{string_name}` with the app's own text, so help always names buttons exactly as shown. */
        fun fill(text: String, lookup: (String) -> String?): String =
            PLACEHOLDER.replace(text) { m -> lookup(m.groupValues[1]) ?: m.value }
    }
}

object HelpAssets {
    fun image(lang: String, image: String) = "help/img/$lang/$image.webp"
    fun audio(lang: String, topicId: String, index: Int) = "help/audio/$lang/${topicId}_${index + 1}.m4a"
}
