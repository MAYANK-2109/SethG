package com.sethg.app.data.local

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.sethg.app.domain.model.MaterialCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp

/**
 * On-device ML price model (assets/price_model.tflite, trained by
 * ml/price/train_price_model.py). Predicts the ₹/kg buying rate for a category
 * at a location as the 10th / 50th / 90th percentile — wide where prices really
 * vary (cable), narrow where they don't (batteries).
 *
 * Returns null if the model isn't bundled or the category has no training data,
 * so callers fall back to the city/zone rate table.
 */
@Singleton
class PricePredictor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    data class Prediction(val lowPerKg: Double, val midPerKg: Double, val highPerKg: Double, val trainingRows: Int)

    private data class Features(
        val categories: List<String>,
        val zones: List<String>,
        val category_alias: Map<String, String>,
        val lat_mean: Double, val lat_std: Double,
        val lon_mean: Double, val lon_std: Double
    )
    private data class Meta(
        val version: Int,
        val features: Features,
        val training_rows: Map<String, Int>,
        val categories_with_data: List<String>
    )

    private companion object {
        const val TAG = "PricePredictor"
        const val MODEL_ASSET = "price_model.tflite"
        const val META_ASSET = "price_model_meta.json"
    }

    private val loaded: Pair<Interpreter, Meta>? by lazy {
        try {
            val meta = context.assets.open(META_ASSET).bufferedReader().use { gson.fromJson(it, Meta::class.java) }
            Interpreter(loadModel()) to meta
        } catch (e: Exception) {
            Log.w(TAG, "Price model not available, using rate table", e)
            null
        }
    }

    fun predict(category: MaterialCategory, zone: Zone, lat: Double, lon: Double): Prediction? {
        val (interpreter, meta) = loaded ?: return null
        val f = meta.features
        val name = f.category_alias[category.name] ?: category.name
        if (name !in meta.categories_with_data) return null

        // Same feature layout as featurize() in train_price_model.py
        val input = FloatArray(f.categories.size + f.zones.size + 2)
        input[f.categories.indexOf(name)] = 1f
        input[f.categories.size + f.zones.indexOf(zone.name)] = 1f
        input[input.size - 2] = ((lat - f.lat_mean) / f.lat_std).toFloat()
        input[input.size - 1] = ((lon - f.lon_mean) / f.lon_std).toFloat()

        val output = Array(1) { FloatArray(3) }
        synchronized(interpreter) { interpreter.run(arrayOf(input), output) }
        val (low, mid, high) = output[0].sorted().map { exp(it.toDouble()) }   // log(₹/kg) → ₹/kg
        return Prediction(low, mid, high, meta.training_rows.values.sum())
    }

    private fun loadModel(): MappedByteBuffer =
        context.assets.openFd(MODEL_ASSET).use { fd ->
            FileInputStream(fd.fileDescriptor).channel.use { channel ->
                channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }
}
