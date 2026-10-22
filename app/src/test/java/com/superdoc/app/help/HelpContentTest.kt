package com.superdoc.app.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class HelpContentTest {
    private val content = HelpContent.parse(File("src/main/assets/help/help.json").readText(), boxesJson())

    private fun boxesJson(): String? = File("src/main/assets/help/boxes.json").takeIf { it.exists() }?.readText()

    private fun strings(dir: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        File("src/main/res/$dir").listFiles { f -> f.name.startsWith("strings") && f.extension == "xml" }!!.forEach { file ->
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val nodes = doc.getElementsByTagName("string")
            for (i in 0 until nodes.length) {
                val n = nodes.item(i)
                result[n.attributes.getNamedItem("name").nodeValue] = n.textContent
            }
        }
        return result
    }

    @Test
    fun `every topic the app links to exists`() {
        val linked = File("src/main/java").walkTopDown().filter { it.extension == "kt" }
            .flatMap { Regex("help\\(\"([a-z]+)\"\\)").findAll(it.readText()).map { m -> m.groupValues[1] } }
            .toSet()
        assertTrue(linked.isNotEmpty())
        linked.forEach { assertNotNull("Missing help topic '$it'", content.topic(it)) }
    }

    @Test
    fun `placeholders name real strings in both languages`() {
        val en = strings("values")
        val hi = strings("values-hi")
        val placeholder = Regex("\\{([a-z0-9_]+)\\}")
        content.topics.flatMap { it.steps }.forEach { step ->
            for (text in listOf(step.text.en, step.text.hi)) {
                placeholder.findAll(text).forEach { m ->
                    val name = m.groupValues[1]
                    assertTrue("'$name' missing in English strings", name in en)
                    assertTrue("'$name' missing in Hindi strings", name in hi)
                }
            }
        }
    }

    @Test
    fun `every step has text in both languages`() {
        content.topics.forEach { topic ->
            assertTrue(topic.steps.isNotEmpty())
            topic.steps.forEach { step ->
                assertTrue("${topic.id}: empty English", step.text.en.isNotBlank())
                assertTrue("${topic.id}: Hindi text has no Devanagari", step.text.hi.any { it in '\u0900'..'\u097F' })
            }
        }
    }

    @Test
    fun `fill replaces known names and keeps unknown ones`() {
        assertEquals("Tap “Home”. {nope}", HelpContent.fill("Tap “{action_home}”. {nope}") { if (it == "action_home") "Home" else null })
    }

    @Test
    fun `boxes are keyed by topic and step`() {
        val c = HelpContent.parse(
            """{"topics":[{"id":"home","title":{"en":"H","hi":"ह"},"steps":[{"image":"home","text":{"en":"a","hi":"अ"}}]}]}""",
            """{"en":{"home_1":[0.1,0.2,0.3,0.4]}}""",
        )
        assertEquals(HelpBox(0.1f, 0.2f, 0.3f, 0.4f), c.box("en", "home", 0))
        assertEquals(null, c.box("hi", "home", 0))
    }
}
