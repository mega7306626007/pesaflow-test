package com.pesaflow.app.data.ml

import android.content.Context

// Android glue for the trained intent MLP. Same contract as the type
// assist: speaks ONLY when the rule layer draws a blank (top conf < 0.35)
// and the model is sure (conf >= 0.7). Everything else flows through the
// existing keyword branches unchanged.
object MlIntentAssist {
    const val RULE_BLANK_BELOW = 0.35f
    const val ACT_AT_OR_ABOVE = 0.7f

    @Volatile
    private var model: MlpWeights? = null

    fun ensureLoaded(context: Context): MlpWeights? {
        model?.let { return it }
        return try {
            val raw = context.assets.open("ml/intent_weights.json")
                .bufferedReader().readText()
            TfIdfInference.parseMlpWeights(raw).also { model = it }
        } catch (e: Exception) {
            null
        }
    }

    fun suggest(context: Context, text: String): TypePrediction? {
        val m = ensureLoaded(context) ?: return null
        return try {
            TfIdfInference.predictMlp(m, "", text)
        } catch (e: Exception) {
            null
        }
    }
}
