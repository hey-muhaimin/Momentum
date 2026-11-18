package com.momentum.browser.data

import org.json.JSONObject
import kotlin.math.max

data class ExtractedPageDom(
    val title: String,
    val description: String,
    val headings: List<String>,
    val paragraphs: List<String>,
    val fullText: String
)

object OnDeviceAiEngine {

    /**
     * Extracts clean structural text from the live WebView DOM without external requests.
     */
    val EXTRACT_PAGE_DOM_JS = """
        (function() {
          try {
            var title = document.title || '';
            var metaDescEl = document.querySelector('meta[name="description"], meta[property="og:description"]');
            var description = metaDescEl ? (metaDescEl.getAttribute('content') || '') : '';
            var headings = [];
            document.querySelectorAll('h1, h2, h3').forEach(function(el) {
              var t = (el.innerText || '').trim();
              if (t.length > 3 && t.length < 160 && headings.length < 14) {
                headings.push(t);
              }
            });
            var paragraphs = [];
            var nodes = document.querySelectorAll('article p, main p, p, li');
            for (var i = 0; i < nodes.length && paragraphs.length < 40; i++) {
              var txt = (nodes[i].innerText || '').replace(/\s+/g, ' ').trim();
              if (txt.length >= 35) {
                paragraphs.push(txt);
              }
            }
            if (paragraphs.length === 0 && document.body) {
              var raw = (document.body.innerText || '').replace(/\s+/g, ' ').trim();
              if (raw.length > 0) paragraphs.push(raw.slice(0, 4000));
            }
            return JSON.stringify({
              title: title,
              description: description,
              headings: headings,
              paragraphs: paragraphs
            });
          } catch (e) {
            return JSON.stringify({ title: document.title || '', description: '', headings: [], paragraphs: [] });
          }
        })();
    """.trimIndent()

    fun buildAutofillInjectionJs(
        fullName: String,
        email: String,
        phone: String,
        address: String
    ): String {
        val safeName = JSONObject.quote(fullName)
        val safeEmail = JSONObject.quote(email)
        val safePhone = JSONObject.quote(phone)
        val safeAddress = JSONObject.quote(address)
        return """
            (function() {
              var filled = 0;
              var inputs = document.querySelectorAll('input, textarea');
              inputs.forEach(function(el) {
                if (el.disabled || el.readOnly) return;
                var hint = ((el.name || '') + ' ' + (el.id || '') + ' ' + (el.placeholder || '') + ' ' + (el.autocomplete || '')).toLowerCase();
                var val = null;
                if (hint.includes('email') || el.type === 'email') val = $safeEmail;
                else if (hint.includes('phone') || hint.includes('tel') || el.type === 'tel') val = $safePhone;
                else if (hint.includes('address') || hint.includes('street')) val = $safeAddress;
                else if (hint.includes('name')) val = $safeName;
                if (val !== null) {
                  el.value = val;
                  el.dispatchEvent(new Event('input', { bubbles: true }));
                  el.dispatchEvent(new Event('change', { bubbles: true }));
                  filled++;
                }
              });
              return filled;
            })();
        """.trimIndent()
    }

    fun parseDomJson(rawEvaluateResult: String): ExtractedPageDom {
        val cleaned: String = rawEvaluateResult
            .trim()
            .let { raw ->
                if (raw.startsWith("\"") && raw.endsWith("\"")) {
                    runCatching { JSONObject("{\"v\":$raw}").getString("v") }.getOrElse { raw }
                } else {
                    raw
                }
            }

        return runCatching {
            val json = JSONObject(cleaned)
            val title = json.optString("title", "")
            val desc = json.optString("description", "")
            val headingsArr = json.optJSONArray("headings")
            val headings = buildList {
                if (headingsArr != null) {
                    for (i in 0 until headingsArr.length()) {
                        val h = headingsArr.optString(i).trim()
                        if (h.isNotEmpty()) add(h)
                    }
                }
            }
            val paraArr = json.optJSONArray("paragraphs")
            val paragraphs = buildList {
                if (paraArr != null) {
                    for (i in 0 until paraArr.length()) {
                        val p = paraArr.optString(i).trim()
                        if (p.isNotEmpty()) add(p)
                    }
                }
            }
            val fullText = buildString {
                if (title.isNotBlank()) appendLine(title)
                if (desc.isNotBlank()) appendLine(desc)
                headings.forEach { appendLine(it) }
                paragraphs.forEach { appendLine(it) }
            }.trim()

            ExtractedPageDom(
                title = title,
                description = desc,
                headings = headings,
                paragraphs = paragraphs,
                fullText = fullText
            )
        }.getOrElse {
            ExtractedPageDom("", "", emptyList(), emptyList(), "")
        }
    }

    fun summarizeKeyPoints(
        title: String,
        url: String,
        extractedText: String,
        headings: List<String>
    ): Triple<List<String>, Int, Int> {
        val words = extractedText.split(Regex("\\s+")).filter { it.isNotBlank() }
        val wordCount = words.size
        val readMinutes = max(1, (wordCount + 199) / 200)

        if (extractedText.isBlank()) {
            val fallback = listOf(
                "No live article text has been extracted from this page yet.",
                "Open any webpage or #Net URN in Browser and tap Understand this page again.",
                "All summarization runs locally on-device without sending page content to external servers."
            )
            return Triple(fallback, 0, 1)
        }

        val sentences = extractedText
            .split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.length in 35..260 }
            .distinct()

        if (sentences.isEmpty()) {
            val fallback = headings.take(3).ifEmpty {
                listOf("Loaded page: ${title.ifBlank { url }} ($wordCount words extracted).")
            }
            return Triple(fallback, wordCount, readMinutes)
        }

        val stopWords = setOf(
            "the", "and", "for", "that", "this", "with", "from", "are", "was", "were",
            "have", "has", "not", "but", "you", "your", "can", "will", "about", "into",
            "more", "when", "they", "their", "them", "than", "also", "been", "being"
        )
        val freq = mutableMapOf<String, Int>()
        words.forEach { w ->
            val norm = w.lowercase().replace(Regex("[^a-z0-9]"), "")
            if (norm.length >= 4 && norm !in stopWords) {
                freq[norm] = (freq[norm] ?: 0) + 1
            }
        }

        val scored = sentences.mapIndexed { idx, sentence ->
            val tokens = sentence.split(Regex("\\s+"))
                .map { it.lowercase().replace(Regex("[^a-z0-9]"), "") }
                .filter { it.length >= 4 && it !in stopWords }
            val termScore = tokens.sumOf { freq[it] ?: 0 }.toDouble() / max(1, tokens.size)
            val positionBoost = if (idx < 4) 1.35 else 1.0
            val headingOverlap = if (headings.any { h -> sentence.contains(h.take(16), ignoreCase = true) }) 1.25 else 1.0
            sentence to (termScore * positionBoost * headingOverlap)
        }.sortedByDescending { it.second }
            .take(4)
            .map { it.first }

        return Triple(scored, wordCount, readMinutes)
    }

    fun answerQuestionFromPage(
        question: String,
        pageTitle: String,
        pageUrl: String,
        extractedText: String,
        headings: List<String>
    ): String {
        val cleanQ = question.trim()
        if (extractedText.isBlank()) {
            return "No extracted text is available yet for $pageUrl. Open a webpage in the Browser first so Momentum AI can inspect its on-device DOM content."
        }

        val queryTerms = cleanQ.lowercase()
            .split(Regex("\\s+"))
            .map { it.replace(Regex("[^a-z0-9]"), "") }
            .filter { it.length >= 3 }
            .toSet()

        val chunks = extractedText
            .split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.length >= 25 }
            .distinct()

        if (chunks.isEmpty()) {
            return "Page \"$pageTitle\" is loaded, but contains minimal readable body text."
        }

        val ranked = chunks.map { chunk ->
            val lower = chunk.lowercase()
            val hits = queryTerms.count { term -> lower.contains(term) }
            chunk to hits
        }.sortedByDescending { it.second }

        val topMatches = ranked.filter { it.second > 0 }.take(3).map { it.first }
        return if (topMatches.isNotEmpty()) {
            buildString {
                append("Based on ")
                append(pageTitle.ifBlank { pageUrl })
                append(":\n\n")
                topMatches.forEach { match ->
                    append("• ").append(match).append('\n')
                }
            }.trim()
        } else {
            val overview = headings.take(3).joinToString(" • ")
            val snippet = chunks.firstOrNull().orEmpty()
            buildString {
                append("I couldn't find an exact keyword match on this page, but here is the closest section")
                if (overview.isNotBlank()) append(" (Sections: $overview)")
                append(":\n\n• ").append(snippet)
            }
        }
    }

    fun buildTranslatedPreview(
        extractedText: String,
        targetLang: String
    ): String {
        if (extractedText.isBlank()) {
            return "Open a webpage first to preview an on-device $targetLang reading summary."
        }
        val header = "On-Device Reading Digest ($targetLang)"
        val excerpt = extractedText
            .split(Regex("\\n+"))
            .filter { it.isNotBlank() }
            .take(5)
            .joinToString("\n\n")
        return "$header\n\n$excerpt"
    }
}
