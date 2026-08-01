package com.haise.jiyu.translate

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Měří, jak se pod zdrojovým jazykem "Auto" chovají SKUTEČNÉ ML Kit modely - tohle unit test
 * udělat nemůže, protože ML Kit potřebuje zařízení.
 *
 * Proč to vzniklo: práh [AUTO_CONFIDENT_CHARS] (kolik nebílých znaků stačí, aby se průchod
 * považoval za jistý a další modely se už nespouštěly) byl při psaní opravy jen ODHAD.
 * Celá logika výběru stojí na předpokladu, že latinkový model na japonské stránce vrátí
 * málo znaků - a to se dá potvrdit jedině změřením.
 *
 * Naměřené hodnoty vypisuje do logcatu pod značkou "AutoLangProbe".
 */
@RunWith(AndroidJUnit4::class)
class AutoLanguageOnDeviceTest {

    private val japaneseRecognizer by lazy { TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()) }
    private val chineseRecognizer by lazy { TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()) }
    private val koreanRecognizer by lazy { TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()) }
    private val latinRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private fun recognizerFor(language: String) = when (language) {
        "Japanese" -> japaneseRecognizer
        "Chinese", "Chinese (Traditional)" -> chineseRecognizer
        "Korean" -> koreanRecognizer
        else -> latinRecognizer
    }

    /** Stránka s textem - bílé pozadí, černé písmo, několik řádků pod sebou. */
    private fun pageWith(lines: List<String>, textSize: Float = 64f): Bitmap {
        val bmp = Bitmap.createBitmap(900, 1200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply {
            color = Color.BLACK
            this.textSize = textSize
            isAntiAlias = true
        }
        var y = 160f
        lines.forEach { line ->
            canvas.drawText(line, 60f, y, paint)
            y += textSize * 1.8f
        }
        return bmp
    }

    private suspend fun blocksFrom(language: String, bitmap: Bitmap): List<RawTextBlock> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = suspendCancellableCoroutine { cont ->
            recognizerFor(language).process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        return result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            if (line.text.isBlank()) return@mapNotNull null
            RawTextBlock(text = line.text, leftF = 0f, topF = 0f, rightF = 1f, bottomF = 1f)
        }
    }

    private fun chars(blocks: List<RawTextBlock>) =
        blocks.sumOf { b -> b.text.count { !it.isWhitespace() } }

    /** Vypíše, kolik znaků našel KAŽDÝ model - to je ten údaj, kvůli kterému test vznikl. */
    private fun probe(label: String, bitmap: Bitmap): Map<String, Int> = runBlocking {
        val counts = AUTO_CANDIDATE_LANGUAGES.associateWith { chars(blocksFrom(it, bitmap)) }
        Log.i("AutoLangProbe", "$label -> $counts")
        counts
    }

    @Test
    fun measureJapanesePage() {
        val bitmap = pageWith(
            listOf(
                "こんにちは、元気ですか",
                "今日はいい天気ですね",
                "また明日会いましょう",
            ),
        )
        val counts = probe("JAPONSKA STRANKA", bitmap)

        val (winner, _) = runBlocking {
            resolveAutoLanguage { candidate -> blocksFrom(candidate, bitmap) }
        }
        Log.i("AutoLangProbe", "JAPONSKA STRANKA vitez -> $winner")

        assertEquals("na japonske strance musi vyhrat japonsky model", "Japanese", winner)
        assertTrue(
            "latinka nesmi na japonske strance prekrocit prah $AUTO_CONFIDENT_CHARS " +
                "(namereno ${counts["English"]}) - jinak by se dalsi modely vubec nespustily",
            (counts["English"] ?: 0) < AUTO_CONFIDENT_CHARS,
        )
    }

    @Test
    fun measureLatinPage() {
        val bitmap = pageWith(
            listOf(
                "WHAT ARE YOU DOING HERE",
                "I told you to stay back",
                "We need to move right now",
            ),
        )
        val counts = probe("LATINKOVA STRANKA", bitmap)

        val (winner, _) = runBlocking {
            resolveAutoLanguage { candidate -> blocksFrom(candidate, bitmap) }
        }
        Log.i("AutoLangProbe", "LATINKOVA STRANKA vitez -> $winner")

        assertEquals("bezna anglicka stranka se ma vyresit hned prvnim modelem", "English", winner)
        assertTrue(
            "anglicka stranka musi prah pohodlne prekrocit (nameteno ${counts["English"]})",
            (counts["English"] ?: 0) >= AUTO_CONFIDENT_CHARS,
        )
    }

    @Test
    fun measureKoreanPage() {
        val bitmap = pageWith(listOf("안녕하세요 반갑습니다", "오늘 날씨가 좋네요"))
        probe("KOREJSKA STRANKA", bitmap)

        val (winner, blocks) = runBlocking {
            resolveAutoLanguage { candidate -> blocksFrom(candidate, bitmap) }
        }
        Log.i("AutoLangProbe", "KOREJSKA STRANKA vitez -> $winner")
        assertTrue("korejska stranka musi neco najit", blocks.isNotEmpty())
    }

    @Test
    fun anEmptyPageResolvesWithoutCrashing() {
        val bitmap = pageWith(emptyList())
        val (winner, blocks) = runBlocking {
            resolveAutoLanguage { candidate -> blocksFrom(candidate, bitmap) }
        }
        Log.i("AutoLangProbe", "PRAZDNA STRANKA vitez -> $winner, bloku=${blocks.size}")
        assertTrue(blocks.isEmpty())
    }
}
