package com.pesaflow.app.parsers

import com.pesaflow.app.data.ml.TfIdfInference
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

@Serializable
private data class IntentCase(
    val text: String,
    val true_label: String,
    val python_pred: String
)


/** Intent MLP parity: Kotlin reproduces torch exactly. */
class MlIntentParityTest {

    private fun resource(name: String): String =
        javaClass.getResourceAsStream("/ml/$name")!!.bufferedReader().readText()

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `kotlin mlp matches torch on every held-out case`() {
        val weights = TfIdfInference.parseMlpWeights(resource("intent_weights.json"))
        assertEquals("tfidf-mlp-v1", weights.format)
        assertEquals(14, weights.labels.size)
        val cases: List<IntentCase> = json.decodeFromString(resource("intent_test_cases.json"))
        assertTrue(cases.isNotEmpty())
        for (c in cases) {
            val pred = TfIdfInference.predictMlp(weights, "", c.text)
            assertEquals("parity for: ${c.text}", c.python_pred, pred.label)
        }
    }

    @Test
    fun `shipped intent accuracy holds on device`() {
        val weights = TfIdfInference.parseMlpWeights(resource("intent_weights.json"))
        val cases: List<IntentCase> = json.decodeFromString(resource("intent_test_cases.json"))
        val correct = cases.count {
            TfIdfInference.predictMlp(weights, "", it.text).label == it.true_label
        }
        assertTrue("top1 $correct/${cases.size}", correct.toDouble() / cases.size >= 0.75)
    }

    @Test
    fun `affordability and balance route correctly`() {
        val weights = TfIdfInference.parseMlpWeights(resource("intent_weights.json"))
        assertEquals(
            "affordability_check",
            TfIdfInference.predictMlp(weights, "", "Can I afford lunch for 200?").label
        )
        assertEquals(
            "balance_query",
            TfIdfInference.predictMlp(weights, "", "what is my balance").label
        )
    }
}
