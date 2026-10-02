package com.pesaflow.app.data.ml

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// On-device inference for the Python-trained TF-IDF + LogReg artifacts.
// Pure Kotlin, zero new dependencies, fully offline. Replicates the
// training pipeline exactly: lowercase word tokenization (uni+bigrams),
// raw-count TF × exported IDF, L2 norm, W·x + b, softmax.
// A prediction NEVER writes the ledger — it only routes rows to review.

@Serializable
data class TypeWeights(
    val format: String = "",
    val labels: List<String> = emptyList(),
    val vocab: List<String> = emptyList(),
    val idf: List<Double> = emptyList(),
    val coef: List<List<Double>> = emptyList(),
    val intercept: List<Double> = emptyList()
)

/** 2-layer MLP weights: same TF-IDF front end, ReLU hidden, softmax out. */
@Serializable
data class MlpWeights(
    val format: String = "",
    val labels: List<String> = emptyList(),
    val vocab: List<String> = emptyList(),
    val idf: List<Double> = emptyList(),
    val w1: List<List<Double>> = emptyList(),
    val b1: List<Double> = emptyList(),
    val w2: List<List<Double>> = emptyList(),
    val b2: List<Double> = emptyList()
)

data class TypePrediction(
    val label: String,
    val confidence: Float,
    val scores: Map<String, Float>
)

class PreparedTypeModel(val weights: TypeWeights) {
    val index: Map<String, Int> = weights.vocab.withIndex()
        .associate { it.value to it.index }
}

object TfIdfInference {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseWeights(raw: String): TypeWeights =
        json.decodeFromString(TypeWeights.serializer(), raw)

    fun parseMlpWeights(raw: String): MlpWeights =
        json.decodeFromString(MlpWeights.serializer(), raw)

    private val tokenRegex = Regex("\\b\\w[\\w']*\\b")

    fun tokenize(text: String): List<String> {
        val words = tokenRegex.findAll(text.lowercase()).map { it.value }.toList()
        val out = ArrayList<String>(words.size * 2)
        out.addAll(words)
        for (i in 0 until words.size - 1) out.add(words[i] + " " + words[i + 1])
        return out
    }

    fun predict(model: PreparedTypeModel, sender: String, text: String): TypePrediction {
        val w = model.weights
        val counts = HashMap<Int, Double>()
        var total = 0
        for (t in tokenize("$sender $text")) {
            val i = model.index[t] ?: continue
            counts[i] = (counts[i] ?: 0.0) + 1.0
            total++
        }
        // tf * idf, then L2 normalize — mirrors sklearn TfidfVectorizer.
        var norm = 0.0
        val vals = HashMap<Int, Double>(counts.size)
        for ((i, tf) in counts) {
            if (i >= w.idf.size) continue
            val v = tf * w.idf[i]
            vals[i] = v
            norm += v * v
        }
        norm = kotlin.math.sqrt(norm)
        val n = w.labels.size
        val logits = DoubleArray(n) { c -> if (c < w.intercept.size) w.intercept[c] else 0.0 }
        if (norm > 0) {
            for ((i, v) in vals) {
                val x = v / norm
                for (c in 0 until n) {
                    if (c < w.coef.size && i < w.coef[c].size) logits[c] += w.coef[c][i] * x
                }
            }
        }
        val max = logits.maxOrNull() ?: 0.0
        var sum = 0.0
        val probs = DoubleArray(n) { c ->
            kotlin.math.exp(logits[c] - max).also { sum += it }
        }
        var best = 0
        val scores = LinkedHashMap<String, Float>(n)
        for (c in 0 until n) {
            val p = (probs[c] / sum).toFloat()
            scores[w.labels[c]] = p
            if (p > scores[w.labels[best]]!!) best = c
        }
        return TypePrediction(w.labels[best], scores[w.labels[best]]!!, scores)
    }

    /** Shared TF-IDF front end, returned as a normalized dense vector. */
    fun featurize(
        vocabIndex: Map<String, Int>,
        idf: List<Double>,
        sender: String,
        text: String
    ): DoubleArray {
        val counts = HashMap<Int, Double>()
        for (t in tokenize("$sender $text")) {
            val i = vocabIndex[t] ?: continue
            counts[i] = (counts[i] ?: 0.0) + 1.0
        }
        var norm = 0.0
        val vals = HashMap<Int, Double>(counts.size)
        for ((i, tf) in counts) {
            if (i >= idf.size) continue
            val v = tf * idf[i]
            vals[i] = v
            norm += v * v
        }
        norm = kotlin.math.sqrt(norm)
        // Size to the largest referenced index; callers slice to vocab size.
        val size = (vals.keys.maxOrNull() ?: -1) + 1
        val out = DoubleArray(size)
        if (norm > 0) {
            for ((i, v) in vals) out[i] = v / norm
        }
        return out
    }

    /** 2-layer MLP forward pass: ReLU hidden, softmax output. */
    fun predictMlp(model: MlpWeights, sender: String, text: String): TypePrediction {
        val vocabIndex = model.vocab.withIndex().associate { it.value to it.index }
        val x = featurize(vocabIndex, model.idf, sender, text)
        val hiddenSize = model.b1.size
        val hidden = DoubleArray(hiddenSize) { h ->
            var s = if (h < model.b1.size) model.b1[h] else 0.0
            if (h < model.w1.size) {
                val row = model.w1[h]
                val lim = minOf(x.size, row.size)
                for (i in 0 until lim) s += row[i] * x[i]
            }
            if (s < 0) 0.0 else s
        }
        val n = model.labels.size
        val logits = DoubleArray(n) { c ->
            var s = if (c < model.b2.size) model.b2[c] else 0.0
            if (c < model.w2.size) {
                val row = model.w2[c]
                val lim = minOf(hidden.size, row.size)
                for (h in 0 until lim) s += row[h] * hidden[h]
            }
            s
        }
        val max = logits.maxOrNull() ?: 0.0
        var sum = 0.0
        val probs = DoubleArray(n) { c ->
            kotlin.math.exp(logits[c] - max).also { sum += it }
        }
        var best = 0
        val scores = LinkedHashMap<String, Float>(n)
        for (c in 0 until n) {
            val p = (probs[c] / sum).toFloat()
            scores[model.labels[c]] = p
            if (p > scores[model.labels[best]]!!) best = c
        }
        return TypePrediction(model.labels[best], scores[model.labels[best]]!!, scores)
    }
}
