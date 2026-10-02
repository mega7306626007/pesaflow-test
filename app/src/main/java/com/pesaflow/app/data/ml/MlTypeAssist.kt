package com.pesaflow.app.data.ml

import android.content.Context

// Android glue for the trained type classifier. Loads the bundled weights
// once, answers second opinions. NEVER writes the ledger, NEVER overrides
// a deterministic parse — disagreements route rows to human review.
object MlTypeAssist {
    @Volatile
    private var model: PreparedTypeModel? = null

    fun ensureLoaded(context: Context): PreparedTypeModel? {
        model?.let { return it }
        return try {
            val raw = context.assets.open("ml/type_weights.json")
                .bufferedReader().readText()
            PreparedTypeModel(TfIdfInference.parseWeights(raw)).also { model = it }
        } catch (e: Exception) {
            null
        }
    }

    /** Returns null when the model is missing, not confident, or errors. */
    fun suggest(context: Context, sender: String, text: String): TypePrediction? {
        val m = ensureLoaded(context) ?: return null
        return try {
            TfIdfInference.predict(m, sender, text)
        } catch (e: Exception) {
            null
        }
    }
}
