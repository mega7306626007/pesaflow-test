package com.pesaflow.app.data.ml

import android.content.Context

// Android glue for the trained category scorer. Same contract as the type
// assist: suggestions only, never writes, never overrides a human or a
// high-confidence rule. The scorer sees merchant text alone — no amounts,
// no history — so its bar for action is high (0.8).
object MlCategoryAssist {
    const val ACT_THRESHOLD = 0.8f

    @Volatile
    private var model: PreparedTypeModel? = null

    fun ensureLoaded(context: Context): PreparedTypeModel? {
        model?.let { return it }
        return try {
            val raw = context.assets.open("ml/category_weights.json")
                .bufferedReader().readText()
            PreparedTypeModel(TfIdfInference.parseWeights(raw)).also { model = it }
        } catch (e: Exception) {
            null
        }
    }

    /** Top suggestion at or above threshold, else null. */
    fun suggest(context: Context, merchant: String, threshold: Float = ACT_THRESHOLD): TypePrediction? {
        val m = ensureLoaded(context) ?: return null
        return try {
            val pred = TfIdfInference.predict(m, "", merchant)
            if (pred.confidence >= threshold) pred else null
        } catch (e: Exception) {
            null
        }
    }
}
