package com.sethg.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device check that a lot photo actually shows e-waste. Runs fully offline.
 *
 * Two models:
 *  • assets/ewaste_classifier.tflite — our MobileNetV3 trained on e-waste
 *    (scrapyard photos, PCBs, batteries) vs household waste (bottles, cans,
 *    paper, cardboard, clothes, glass…). See ml/train.py.
 *  • ML Kit's general labeler — catches things our model never saw in training:
 *    people/selfies, food, animals, plants, vehicles, furniture.
 *
 * Verdict:
 *   ML Kit top label clearly not e-waste     → NOT_EWASTE
 *   P(e-waste) ≥ ACCEPT                      → EWASTE
 *   P(e-waste) ≤ REJECT                      → NOT_EWASTE
 *   otherwise                                → UNCERTAIN (accepted, flagged for review)
 */
@Singleton
class EWasteDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    enum class Verdict { EWASTE, UNCERTAIN, NOT_EWASTE }

    data class Result(val verdict: Verdict, val topLabel: String?, val eWasteScore: Float?)

    companion object {
        private const val TAG = "EWasteDetector"
        private const val MODEL_ASSET  = "ewaste_classifier.tflite"
        private const val LABELS_ASSET = "ewaste_labels.txt"
        private const val INPUT_SIZE   = 224

        private const val ACCEPT = 0.65f
        private const val REJECT = 0.35f
        private const val MLKIT_REJECT_CONFIDENCE = 0.6f

        private val NOT_EWASTE_LABELS = setOf(
            // People
            "Person", "Selfie", "Smile", "Laugh", "Beard", "Moustache", "Hair", "Bangs", "Skin",
            "Eyelash", "Mouth", "Ear", "Toe", "Foot", "Baby", "Muscle", "Tattoo", "Crowd", "Team",
            "Bride", "Groom", "Dude", "Grandparent",
            // Food & drink
            "Food", "Fruit", "Vegetable", "Cuisine", "Meal", "Lunch", "Supper", "Bread", "Cake",
            "Pizza", "Cookie", "Fast food", "Cheeseburger", "Hot dog", "Juice", "Coffee",
            "Cappuccino", "Cola", "Wine", "Alcohol", "Sushi", "Icing", "Pie", "Gelato", "Bento",
            "Pho", "Couscous", "Pasteles", "Eating",
            // Animals
            "Dog", "Cat", "Bird", "Pet", "Horse", "Cattle", "Bull", "Insect", "Butterfly", "Duck",
            "Bear", "Turtle", "Crocodile", "Penguin", "Herd", "Waterfowl", "Larva", "Seal",
            // Plants & outdoors
            "Plant", "Flower", "Flora", "Petal", "Flowerpot", "Garden", "Forest", "Jungle", "Beach",
            "Mountain", "Lake", "River", "Sunset", "Sky", "Waterfall", "Field", "Farm", "Sand",
            // Clothes & textiles
            "Dress", "Jeans", "Denim", "Shoe", "Sneakers", "Jacket", "Sari", "Shorts", "Tights",
            "Leggings", "Hat", "Cap", "Scarf", "Gown", "Blazer", "Textile", "Wool", "Cotton",
            // Paper & documents
            "Paper", "Newspaper", "Money", "Receipt", "Passport", "Menu", "Poster", "Comics",
            // Vehicles
            "Car", "Bus", "Bicycle", "Motorcycle", "Train", "Airplane", "Boat", "Vehicle",
            "Rickshaw", "Van", "Tire",
            // Furniture & household
            "Chair", "Couch", "Bunk bed", "Pillow", "Cushion", "Curtain", "Tableware", "Cutlery",
            "Cookware and bakeware", "Cup", "Stuffed toy", "Plush"
        )
    }

    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(0.3f).build()
    )

    // Lazily loaded; null if the model asset is missing (falls back to ML Kit only)
    private val classifier: Pair<Interpreter, Int>? by lazy {
        try {
            val labels = context.assets.open(LABELS_ASSET).bufferedReader().readLines().map { it.trim() }
            val interpreter = Interpreter(loadModel(), Interpreter.Options().setNumThreads(2))
            interpreter to labels.indexOf("ewaste")
        } catch (e: Exception) {
            Log.w(TAG, "Custom e-waste model not available, using ML Kit only", e)
            null
        }
    }

    suspend fun check(photo: File): Result {
        val labels = label(InputImage.fromFilePath(context, Uri.fromFile(photo)))
            .sortedByDescending { it.confidence }
        val top = labels.firstOrNull()

        if (top != null && top.text in NOT_EWASTE_LABELS && top.confidence >= MLKIT_REJECT_CONFIDENCE) {
            return Result(Verdict.NOT_EWASTE, top.text, null)
        }

        val score = eWasteScore(photo) ?: return Result(Verdict.UNCERTAIN, top?.text, null)
        val verdict = when {
            score >= ACCEPT -> Verdict.EWASTE
            score <= REJECT -> Verdict.NOT_EWASTE
            else            -> Verdict.UNCERTAIN
        }
        return Result(verdict, top?.text, score)
    }

    /** P(e-waste) from our TFLite model, or null if the model isn't bundled. */
    private fun eWasteScore(photo: File): Float? {
        val (interpreter, eWasteIndex) = classifier ?: return null
        if (eWasteIndex < 0) return null

        val bitmap = decodeScaled(photo) ?: return null
        val input = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3).order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        bitmap.recycle()
        // Model expects raw RGB 0–255 (preprocessing is built into the model)
        for (p in pixels) {
            input.putFloat(((p shr 16) and 0xFF).toFloat())
            input.putFloat(((p shr 8) and 0xFF).toFloat())
            input.putFloat((p and 0xFF).toFloat())
        }
        input.rewind()

        val output = Array(1) { FloatArray(interpreter.getOutputTensor(0).shape()[1]) }
        synchronized(interpreter) { interpreter.run(input, output) }
        return output[0][eWasteIndex]
    }

    /** Decode at low resolution first so large camera JPEGs don't blow memory on 2 GB phones. */
    private fun decodeScaled(photo: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(photo.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= INPUT_SIZE && bounds.outHeight / (sample * 2) >= INPUT_SIZE) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeFile(
            photo.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null
        // Same squash-resize as tf.keras image_dataset_from_directory used in training
        val scaled = Bitmap.createScaledBitmap(decoded, INPUT_SIZE, INPUT_SIZE, true)
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun loadModel(): MappedByteBuffer {
        context.assets.openFd(MODEL_ASSET).use { fd ->
            FileInputStream(fd.fileDescriptor).channel.use { channel ->
                return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }
    }

    private suspend fun label(image: InputImage): List<ImageLabel> =
        suspendCancellableCoroutine { cont ->
            labeler.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
}
