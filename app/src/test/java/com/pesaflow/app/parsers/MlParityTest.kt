package com.pesaflow.app.parsers

import com.pesaflow.app.data.ml.PreparedTypeModel
import com.pesaflow.app.data.ml.TfIdfInference
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

@Serializable
private data class ParityCase(
    val sender: String,
    val text: String,
    val true_label: String,
    val python_pred: String
)


/** Kotlin inference must reproduce the Python model exactly. */
class MlParityTest {

    private fun resource(name: String): String =
        javaClass.getResourceAsStream("/ml/$name")!!.bufferedReader().readText()

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `tokenizer mirrors training uni plus bigrams`() {
        val toks = TfIdfInference.tokenize("You have sent KSh500")
        assertTrue(toks.contains("you"))
        assertTrue(toks.contains("you have"))
        assertTrue(toks.contains("sent"))
        assertTrue(toks.contains("ksh500"))
    }

    @Test
    fun `kotlin matches python on every held-out case`() {
        val model = PreparedTypeModel(TfIdfInference.parseWeights(resource("type_weights.json")))
        val cases: List<ParityCase> = json.decodeFromString(resource("type_test_cases.json"))
        assertTrue(cases.isNotEmpty())
        for (c in cases) {
            val pred = TfIdfInference.predict(model, c.sender, c.text)
            assertEquals("parity for: ${c.text.take(60)}", c.python_pred, pred.label)
        }
    }

    @Test
    fun `shipped model is accurate not just consistent`() {
        val model = PreparedTypeModel(TfIdfInference.parseWeights(resource("type_weights.json")))
        val cases: List<ParityCase> = json.decodeFromString(resource("type_test_cases.json"))
        val correct = cases.count {
            TfIdfInference.predict(model, it.sender, it.text).label == it.true_label
        }
        assertTrue("accuracy $correct/${cases.size}", correct >= cases.size - 1)
    }

    @Test
    fun `empty text does not crash`() {
        val model = PreparedTypeModel(TfIdfInference.parseWeights(resource("type_weights.json")))
        val pred = TfIdfInference.predict(model, "", "")
        assertTrue(pred.label in setOf("EXPENSE", "INCOME", "NULL", "SAVING", "TRANSFER"))
        assertTrue(pred.confidence in 0f..1f)
    }
}
