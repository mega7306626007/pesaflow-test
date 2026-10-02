package com.pesaflow.app.parsers

import com.pesaflow.app.data.ml.PreparedTypeModel
import com.pesaflow.app.data.ml.TfIdfInference
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

@Serializable
private data class CategoryCase(
    val text: String,
    val true_label: String,
    val python_pred: String
)


/** Category scorer parity: Kotlin reproduces Python exactly. */
class MlCategoryParityTest {

    private fun resource(name: String): String =
        javaClass.getResourceAsStream("/ml/$name")!!.bufferedReader().readText()

    private val json = Json { ignoreUnknownKeys = true }

    private fun model() = PreparedTypeModel(
        TfIdfInference.parseWeights(resource("category_weights.json")))

    private fun cases(): List<CategoryCase> =
        json.decodeFromString(resource("category_eval_cases.json"))

    @Test
    fun `kotlin matches python on every eval merchant`() {
        val m = model()
        val cases = cases()
        assertEquals(40, cases.size)
        for (c in cases) {
            val pred = TfIdfInference.predict(m, "", c.text)
            assertEquals("parity for: ${c.text}", c.python_pred, pred.label)
        }
    }

    @Test
    fun `shipped accuracy holds on device`() {
        val m = model()
        val cases = cases()
        val correct = cases.count {
            TfIdfInference.predict(m, "", it.text).label == it.true_label
        }
        assertTrue("top1 $correct/${cases.size}", correct.toDouble() / cases.size >= 0.80)
    }

    @Test
    fun `common merchants score sensibly`() {
        val m = model()
        val food = TfIdfInference.predict(m, "", "KIBANDA LUNCH")
        assertEquals("Food", food.label)
        val fare = TfIdfInference.predict(m, "", "MATATU FARE")
        assertEquals("Transport", fare.label)
    }
}
