package com.sethg.app.data.local

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device check that a lot photo actually shows e-waste. Runs fully offline
 * using ML Kit's bundled general-purpose labeler (~400 labels).
 *
 * That model has no labels for circuit boards, cables or batteries, so we
 * can't *require* an e-waste label without rejecting genuine lots. Instead:
 *   1. an electronics label (Computer, Mobile phone, …)  → accept
 *   2. the top label is clearly not e-waste (food, people,
 *      animals, plants, clothes, paper, vehicles …)       → reject, ask for another photo
 *   3. anything else                                      → accept as UNCERTAIN (kept for review)
 *
 * Swap in a custom e-waste TFLite model later for a positive e-waste check.
 */
@Singleton
class EWasteDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    enum class Verdict { EWASTE, UNCERTAIN, NOT_EWASTE }

    data class Result(val verdict: Verdict, val topLabel: String?, val confidence: Float)

    companion object {
        private const val ACCEPT_CONFIDENCE = 0.5f
        private const val REJECT_CONFIDENCE = 0.6f

        private val EWASTE_LABELS = setOf(
            "Computer", "Mobile phone", "Television", "Subwoofer", "Junk", "Metal"
        )

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

    suspend fun check(photo: File): Result {
        val labels = label(InputImage.fromFilePath(context, Uri.fromFile(photo)))
            .sortedByDescending { it.confidence }

        labels.firstOrNull { it.text in EWASTE_LABELS && it.confidence >= ACCEPT_CONFIDENCE }
            ?.let { return Result(Verdict.EWASTE, it.text, it.confidence) }

        val top = labels.firstOrNull()
        if (top != null && top.text in NOT_EWASTE_LABELS && top.confidence >= REJECT_CONFIDENCE) {
            return Result(Verdict.NOT_EWASTE, top.text, top.confidence)
        }
        return Result(Verdict.UNCERTAIN, top?.text, top?.confidence ?: 0f)
    }

    private suspend fun label(image: InputImage): List<ImageLabel> =
        suspendCancellableCoroutine { cont ->
            labeler.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
}
