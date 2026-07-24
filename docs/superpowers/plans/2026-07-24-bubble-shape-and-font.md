# Detekce tvaru bubliny + font podle stylu — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Přeložený box v manga čtečce ať vizuálně kopíruje skutečný obrys bubliny (flood-fill detekce podle barvy, ne heuristický odhad z OCR textu) a písmo ať se stylem přizpůsobí typu bubliny (tučné na křik, kurzíva na myšlenku/šepot).

**Architecture:** Nová čistá (JVM-testovatelná) `BubbleShapeDetector` komponenta běží nad abstrakcí pixelů (`PixelSource`), volá se z `OcrEngine.kt` (kde už je načtená bitmapa stránky) hned po existujícím vzorkování barvy pozadí. Výsledný tvar (24 vzorkovaných bodů obrysu) se propaguje přes `RawTextBlock` → `TranslatedBlock` → Room cache (JSON, zpětně kompatibilní) → `TranslationLayout.kt` (obchází starou heuristiku, když je tvar k dispozici) → `ReaderScreen.kt` (Compose `Shape` clip + font podle `BubbleType`).

**Tech Stack:** Kotlin, Jetpack Compose, JUnit (čisté JVM testy bez Robolectru pro nové algoritmy), Room (beze změny schématu), Google Fonts OFL (Comic Neue Italic/Bold Italic).

## Global Constraints

- Žádná změna Room schématu - cache zůstává opaque `blocksJson TEXT` sloupec (`TranslatedPageEntity`).
- Žádná změna `MangaSource` rozhraní ani DI grafu (`SourceManager`, `AppModule`).
- Zpětná kompatibilita cache: staré záznamy bez `"shape"`/`"type"` JSON polí se deserializují s `shape = null`, `bubbleType = BubbleType.SPEECH` - stejný vzor jako existující `disp`/`bg`/`sfx`/`lc` pole v `TranslateRepository.deserialize()`.
- Nové algoritmy (flood-fill) musí být testovatelné čistým JVM testem (`org.junit.Test`, žádný Robolectric) - pracují nad abstrakcí pixelů, ne nad `android.graphics.Bitmap` přímo.
- Fonty: Comic Neue Italic/Bold Italic, stejný zdroj a licence (OFL) jako už stažené `comic_neue_regular.ttf`/`comic_neue_bold.ttf` - `https://raw.githubusercontent.com/google/fonts/main/ofl/comicneue/`.
- Komentáře v kódu česky, stejným stylem jako zbytek souborů (vysvětlují PROČ, ne CO).
- Beze změny existující `TranslationLayoutTest.kt` očekávání pro bloky bez tvaru (`shape == null`) - tahle sada testů dál pokrývá starou heuristiku beze změny.

---

### Task 1: `BubbleShapeDetector` - čistý flood-fill algoritmus

**Files:**
- Create: `app/src/main/kotlin/com/haise/jiyu/translate/BubbleShapeDetector.kt`
- Test: `app/src/test/kotlin/com/haise/jiyu/translate/BubbleShapeDetectorTest.kt`

**Interfaces:**
- Produces: `fun interface PixelSource { fun colorAt(x: Int, y: Int): Int }`
- Produces: `data class BubbleShapePoint(val yF: Float, val leftF: Float, val rightF: Float)`
- Produces: `object BubbleShapeDetector { fun detectShape(source: PixelSource, width: Int, height: Int, seeds: List<Pair<Int, Int>>, bgColorArgb: Int, colorDistanceThreshold: Int = 40, maxAreaFraction: Float = 0.25f): List<BubbleShapePoint>? }`

- [ ] **Step 1: Napsat padající testy**

Vytvoř `app/src/test/kotlin/com/haise/jiyu/translate/BubbleShapeDetectorTest.kt`:

```kotlin
package com.haise.jiyu.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Čistý JVM test [BubbleShapeDetector] (žádná Android/Bitmap závislost) - syntetický
 * PixelSource kreslí jednoduché tvary do IntArray a ověřuje, že flood-fill najde
 * očekávaný obrys / správně selže na moc velké nebo neplatné ploše.
 */
class BubbleShapeDetectorTest {

    private class FakeCanvas(val width: Int, val height: Int, fill: Int) : PixelSource {
        val pixels = IntArray(width * height) { fill }
        override fun colorAt(x: Int, y: Int): Int = pixels[y * width + x]
        fun fillRect(left: Int, top: Int, right: Int, bottom: Int, color: Int) {
            for (y in top..bottom) for (x in left..right) pixels[y * width + x] = color
        }
    }

    private val BG = 0xFFCCCCCC.toInt()
    private val ART = 0xFF000000.toInt()

    @Test
    fun `detects bounding box of a solid rectangle bubble`() {
        val canvas = FakeCanvas(100, 60, ART)
        canvas.fillRect(20, 10, 80, 50, BG)

        val shape = BubbleShapeDetector.detectShape(
            source = canvas,
            width = 100,
            height = 60,
            seeds = listOf(50 to 30),
            bgColorArgb = BG,
        )

        assertNotNull(shape)
        val left = shape!!.minOf { it.leftF }
        val right = shape.maxOf { it.rightF }
        val top = shape.minOf { it.yF }
        val bottom = shape.maxOf { it.yF }
        assertEquals(0.20f, left, 0.02f)
        assertEquals(0.80f, right, 0.02f)
        assertEquals(10f / 60f, top, 0.02f)
        assertEquals(50f / 60f, bottom, 0.02f)
    }

    @Test
    fun `returns null when no seed matches background color`() {
        val canvas = FakeCanvas(100, 60, ART)
        canvas.fillRect(20, 10, 80, 50, BG)

        // Seed sedí uvnitř obdélníku, ale bgColorArgb neodpovídá ničemu na plátně.
        val shape = BubbleShapeDetector.detectShape(
            source = canvas,
            width = 100,
            height = 60,
            seeds = listOf(50 to 30),
            bgColorArgb = 0xFFFF00FF.toInt(),
        )

        assertNull(shape)
    }

    @Test
    fun `returns null when flood fill leaks past the area cap`() {
        // Skoro celé plátno je "pozadí" - žádná uzavřená bublina, flood-fill by se
        // rozlil přes většinu stránky (simuluje SFX text přímo na kresbě bez bubliny).
        val canvas = FakeCanvas(100, 60, BG)

        val shape = BubbleShapeDetector.detectShape(
            source = canvas,
            width = 100,
            height = 60,
            seeds = listOf(50 to 30),
            bgColorArgb = BG,
            maxAreaFraction = 0.25f,
        )

        assertNull(shape)
    }

    @Test
    fun `sampled points are ordered from top to bottom`() {
        val canvas = FakeCanvas(100, 60, ART)
        canvas.fillRect(20, 10, 80, 50, BG)

        val shape = BubbleShapeDetector.detectShape(
            source = canvas,
            width = 100,
            height = 60,
            seeds = listOf(50 to 30),
            bgColorArgb = BG,
        )

        assertNotNull(shape)
        for (i in 1 until shape!!.size) {
            assertTrue(shape[i].yF >= shape[i - 1].yF)
        }
    }

    @Test
    fun `ignores invalid seeds outside the canvas`() {
        val canvas = FakeCanvas(100, 60, ART)
        canvas.fillRect(20, 10, 80, 50, BG)

        val shape = BubbleShapeDetector.detectShape(
            source = canvas,
            width = 100,
            height = 60,
            seeds = listOf(-5 to -5, 50 to 30), // první seed mimo plátno, druhý platný
            bgColorArgb = BG,
        )

        assertNotNull(shape)
    }
}
```

- [ ] **Step 2: Spustit testy a ověřit, že selžou**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.haise.jiyu.translate.BubbleShapeDetectorTest" --console=plain`
Expected: FAIL s chybou kompilace (`BubbleShapeDetector`/`PixelSource`/`BubbleShapePoint` neexistují).

- [ ] **Step 3: Implementovat `BubbleShapeDetector.kt`**

Vytvoř `app/src/main/kotlin/com/haise/jiyu/translate/BubbleShapeDetector.kt`:

```kotlin
package com.haise.jiyu.translate

import java.util.ArrayDeque

/**
 * Abstrakce nad zdrojem pixelů - viz spec docs/superpowers/specs/2026-07-24-bubble-shape-and-font-design.md.
 * Odděluje algoritmus od android.graphics.Bitmap, aby šel testovat čistým JVM testem.
 */
fun interface PixelSource {
    /** ARGB pixel na (x, y); mimo hranice smí vrátit cokoliv, volající si hranice hlídá sám. */
    fun colorAt(x: Int, y: Int): Int
}

/** Jeden vzorkovaný bod obrysu bubliny - normalizované (0..1) souřadnice jako zbytek kódu (leftF/topF). */
data class BubbleShapePoint(val yF: Float, val leftF: Float, val rightF: Float)

/**
 * Najde skutečný obrys bubliny flood-fillem od bodů na jejím pozadí (NE od středu OCR
 * textu - ten často padne na tmavý pixel písma, ne na pozadí; volající by měl posílat
 * body, o kterých už ví, že leží na pozadí - viz OcrEngine.ringSeeds).
 */
object BubbleShapeDetector {

    private const val SAMPLE_COUNT = 24
    private val NEIGHBOR_OFFSETS = arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

    /**
     * BFS flood-fill (fronta, ne rekurze - kvůli velkým bublinám a JVM stack limitu).
     * @return null když detekce selhala/vypadá nedůvěryhodně (žádný platný seed, nebo
     *   navštívená plocha přesáhla [maxAreaFraction] celé stránky - typicky text přímo
     *   na kresbě/SFX bez uzavřeného pozadí) - volající pak použije starý heuristický obdélník.
     */
    fun detectShape(
        source: PixelSource,
        width: Int,
        height: Int,
        seeds: List<Pair<Int, Int>>,
        bgColorArgb: Int,
        colorDistanceThreshold: Int = 40,
        maxAreaFraction: Float = 0.25f,
    ): List<BubbleShapePoint>? {
        if (width <= 0 || height <= 0) return null
        val maxArea = (width.toLong() * height.toLong() * maxAreaFraction).toLong()

        val visited = HashSet<Long>()
        fun key(x: Int, y: Int) = x.toLong() * height.toLong() + y.toLong()

        val validSeeds = seeds.filter { (x, y) ->
            x in 0 until width && y in 0 until height &&
                colorDistance(source.colorAt(x, y), bgColorArgb) < colorDistanceThreshold
        }
        if (validSeeds.isEmpty()) return null

        val queue = ArrayDeque<Pair<Int, Int>>()
        val rowMinMax = HashMap<Int, IntArray>() // y -> [minX, maxX]

        for ((sx, sy) in validSeeds) {
            if (visited.add(key(sx, sy))) queue.add(sx to sy)
        }

        var area = 0L
        while (queue.isNotEmpty()) {
            val (x, y) = queue.poll()
            area++
            if (area > maxArea) return null

            val minMax = rowMinMax.getOrPut(y) { intArrayOf(x, x) }
            if (x < minMax[0]) minMax[0] = x
            if (x > minMax[1]) minMax[1] = x

            for ((dx, dy) in NEIGHBOR_OFFSETS) {
                val nx = x + dx
                val ny = y + dy
                if (nx !in 0 until width || ny !in 0 until height) continue
                if (!visited.add(key(nx, ny))) continue
                if (colorDistance(source.colorAt(nx, ny), bgColorArgb) >= colorDistanceThreshold) continue
                queue.add(nx to ny)
            }
        }

        if (rowMinMax.isEmpty()) return null

        val sortedRows = rowMinMax.keys.sorted()
        val topY = sortedRows.first()
        val bottomY = sortedRows.last()
        if (bottomY <= topY) return null

        return (0 until SAMPLE_COUNT).map { i ->
            val frac = i / (SAMPLE_COUNT - 1).toFloat()
            val targetY = (topY + frac * (bottomY - topY)).toInt().coerceIn(topY, bottomY)
            val nearestY = nearestRowWithData(sortedRows, targetY)
            val minMax = rowMinMax.getValue(nearestY)
            BubbleShapePoint(
                yF = nearestY / height.toFloat(),
                leftF = minMax[0] / width.toFloat(),
                rightF = minMax[1] / width.toFloat(),
            )
        }
    }

    /** Binární hledání nejbližšího řádku s daty - flood-fill nemusí vyplnit úplně každý řádek u šikmých okrajů bubliny. */
    private fun nearestRowWithData(sortedRows: List<Int>, target: Int): Int {
        var lo = 0
        var hi = sortedRows.size - 1
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (sortedRows[mid] < target) lo = mid + 1 else hi = mid
        }
        if (lo > 0 && Math.abs(sortedRows[lo - 1] - target) <= Math.abs(sortedRows[lo] - target)) return sortedRows[lo - 1]
        return sortedRows[lo]
    }

    private fun colorDistance(a: Int, b: Int): Double {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return Math.sqrt((dr * dr + dg * dg + db * db).toDouble())
    }
}
```

- [ ] **Step 4: Spustit testy a ověřit, že projdou**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.haise.jiyu.translate.BubbleShapeDetectorTest" --console=plain`
Expected: PASS (5 testů)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/translate/BubbleShapeDetector.kt app/src/test/kotlin/com/haise/jiyu/translate/BubbleShapeDetectorTest.kt
git commit -m "Feat: BubbleShapeDetector - flood-fill detekce tvaru bubliny (čistý JVM algoritmus)"
```

---

### Task 2: Napojení do `OcrEngine.kt`

**Files:**
- Modify: `app/src/main/kotlin/com/haise/jiyu/translate/OcrEngine.kt`
- Test: `app/src/test/kotlin/com/haise/jiyu/translate/OcrRingSeedsTest.kt`

**Interfaces:**
- Consumes: `BubbleShapeDetector.detectShape(...)`, `PixelSource`, `BubbleShapePoint` (Task 1)
- Produces: `data class RawTextBlock(..., val shape: List<BubbleShapePoint>? = null)`
- Produces: `internal fun ringSeeds(leftF: Float, topF: Float, rightF: Float, bottomF: Float, w: Int, h: Int, margin: Int = 4): List<Pair<Int, Int>>`

- [ ] **Step 1: Napsat padající test pro `ringSeeds`**

`ringSeeds` je čistá funkce (jen Float/Int vstupy, žádný Bitmap) - testovatelná bez Robolectru.
Vytvoř `app/src/test/kotlin/com/haise/jiyu/translate/OcrRingSeedsTest.kt`:

```kotlin
package com.haise.jiyu.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrRingSeedsTest {

    @Test
    fun `produces four seed points around the block with margin`() {
        val seeds = ringSeeds(leftF = 0.2f, topF = 0.1f, rightF = 0.8f, bottomF = 0.5f, w = 100, h = 60, margin = 4)

        assertEquals(4, seeds.size)
        // Top mid: x = (20+80)/2 = 50, y = 10 - 4 = 6
        assertTrue(seeds.contains(50 to 6))
        // Bottom mid: x = 50, y = 30 + 4 = 34
        assertTrue(seeds.contains(50 to 34))
        // Left mid: x = 20 - 4 = 16, y = (6+30)/2 = 18
        assertTrue(seeds.contains(16 to 18))
        // Right mid: x = 80 + 4 = 84, y = 18
        assertTrue(seeds.contains(84 to 18))
    }

    @Test
    fun `clamps seeds to canvas bounds near edges`() {
        val seeds = ringSeeds(leftF = 0f, topF = 0f, rightF = 0.1f, bottomF = 0.1f, w = 100, h = 60, margin = 4)

        seeds.forEach { (x, y) ->
            assertTrue("x=$x out of bounds", x in 0..99)
            assertTrue("y=$y out of bounds", y in 0..59)
        }
    }
}
```

- [ ] **Step 2: Spustit test a ověřit, že selže**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.haise.jiyu.translate.OcrRingSeedsTest" --console=plain`
Expected: FAIL (`ringSeeds` neexistuje)

- [ ] **Step 3: Přidat `shape` pole, `ringSeeds`, `BitmapPixelSource` a napojit do `recognize()`**

V `app/src/main/kotlin/com/haise/jiyu/translate/OcrEngine.kt`:

Uprav `RawTextBlock` (přidej pole `shape`):

```kotlin
data class RawTextBlock(
    val text: String,
    val leftF: Float,
    val topF: Float,
    val rightF: Float,
    val bottomF: Float,
    /** Kolik původních ML Kit "lines" bylo sloučeno do tohoto bloku - viz [OcrEngine.mergeNearbyLines]. */
    val lineCount: Int = 1,
    /** Barva pozadí bubliny nasamplovaná z bitmapy - viz [OcrEngine.sampleBackgroundColor]. */
    val bgColorArgb: Int = DEFAULT_BUBBLE_BG_ARGB,
    /** Skutečný obrys bubliny (flood-fill) - viz [BubbleShapeDetector]. Null = detekce selhala, render použije heuristický obdélník. */
    val shape: List<BubbleShapePoint>? = null,
)

/** Čistá funkce (bez Bitmap) - body na obvodu OCR boxu s okrajem [margin], odkud je bezpečné startovat flood-fill (jsou to body na pozadí bubliny, ne na textu). Testováno v OcrRingSeedsTest. */
internal fun ringSeeds(leftF: Float, topF: Float, rightF: Float, bottomF: Float, w: Int, h: Int, margin: Int = 4): List<Pair<Int, Int>> {
    val left = (leftF * w).toInt()
    val top = (topF * h).toInt()
    val right = (rightF * w).toInt()
    val bottom = (bottomF * h).toInt()
    val midX = ((left + right) / 2).coerceIn(0, w - 1)
    val midY = ((top + bottom) / 2).coerceIn(0, h - 1)
    return listOf(
        midX to (top - margin).coerceIn(0, h - 1),
        midX to (bottom + margin).coerceIn(0, h - 1),
        (left - margin).coerceIn(0, w - 1) to midY,
        (right + margin).coerceIn(0, w - 1) to midY,
    )
}

/** Obaluje Bitmap do [PixelSource] pro [BubbleShapeDetector] - jediné místo, kde algoritmus vidí Android typ. */
private class BitmapPixelSource(private val bitmap: Bitmap) : PixelSource {
    override fun colorAt(x: Int, y: Int): Int = bitmap.getPixel(x, y)
}
```

V `recognize()` nahraď poslední řádek:

```kotlin
        // Sampling barvy pozadí potřebuje ještě živou bitmapu, proto běží tady a ne až
        // v TranslateRepository, kam se bitmapa vůbec nedostane (jen relativní souřadnice).
        mergeNearbyLines(lines).map { it.copy(bgColorArgb = sampleBackgroundColor(bitmap, it)) }
```

za:

```kotlin
        // Sampling barvy pozadí i detekce tvaru bubliny potřebují ještě živou bitmapu,
        // proto běží tady a ne až v TranslateRepository, kam se bitmapa vůbec nedostane
        // (jen relativní souřadnice).
        val pixelSource = BitmapPixelSource(bitmap)
        mergeNearbyLines(lines).map { block ->
            val bg = sampleBackgroundColor(bitmap, block)
            val shape = BubbleShapeDetector.detectShape(
                source = pixelSource,
                width = bitmap.width,
                height = bitmap.height,
                seeds = ringSeeds(block.leftF, block.topF, block.rightF, block.bottomF, bitmap.width, bitmap.height),
                bgColorArgb = bg,
            )
            block.copy(bgColorArgb = bg, shape = shape)
        }
```

- [ ] **Step 4: Spustit testy a ověřit, že projdou**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.haise.jiyu.translate.OcrRingSeedsTest" --console=plain`
Expected: PASS (2 testy)

Run: `./gradlew.bat :app:compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL (nová `recognize()` implementace se nedá pokrýt čistým JVM testem - závisí na ML Kit + Bitmap; ověří se živě na emulátoru v Task 6)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/translate/OcrEngine.kt app/src/test/kotlin/com/haise/jiyu/translate/OcrRingSeedsTest.kt
git commit -m "Feat: napojit BubbleShapeDetector do OcrEngine.recognize()"
```

---

### Task 3: `TranslatedBlock` + `TranslateRepository` - propagace tvaru, cache, migrace

**Files:**
- Modify: `app/src/main/kotlin/com/haise/jiyu/translate/TranslatedBlock.kt`
- Modify: `app/src/main/kotlin/com/haise/jiyu/translate/OcrEngine.kt` (přidat `detectShapesOnly`)
- Modify: `app/src/main/kotlin/com/haise/jiyu/translate/TranslateRepository.kt`
- Test: `app/src/test/kotlin/com/haise/jiyu/translate/TranslatedBlockSerializationTest.kt`

**Interfaces:**
- Consumes: `BubbleShapePoint` (Task 1), `RawTextBlock.shape`, `ringSeeds` (Task 2)
- Produces: `data class TranslatedBlock(..., val shape: List<BubbleShapePoint>? = null, val bubbleType: BubbleType = BubbleType.SPEECH)`
- Produces: `suspend fun OcrEngine.detectShapesOnly(pageUrl: String, blocks: List<TranslatedBlock>): List<TranslatedBlock>`
- Produces: `fun List<TranslatedBlock>.serialize(): String`, `fun TranslatedPageEntity.deserialize(): List<TranslatedBlock>` (rozšířené o `shape`/`type`, zpětně kompatibilní)

- [ ] **Step 1: Napsat padající test serializace**

Vytvoř `app/src/test/kotlin/com/haise/jiyu/translate/TranslatedBlockSerializationTest.kt`. Tenhle test volá `serialize()`/`deserialize()`, které jsou dnes `private` v `TranslateRepository` - proto testujeme přes `org.json` přímo stejným formátem (round-trip na úrovni JSON, ne přes samotnou private funkci):

```kotlin
package com.haise.jiyu.translate

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ověřuje zpětnou kompatibilitu JSON cache formátu pro nová pole "shape"/"type" -
 * starý záznam bez těchto polí se musí deserializovat na shape=null, bubbleType=SPEECH.
 */
class TranslatedBlockSerializationTest {

    @Test
    fun `old cache entry without shape or type fields deserializes with safe defaults`() {
        val oldFormatJson = JSONArray().put(
            JSONObject().apply {
                put("orig", "Hello")
                put("trans", "Ahoj")
                put("disp", "Ahoj")
                put("bg", -1)
                put("sfx", false)
                put("lc", 1)
                put("l", 0.1); put("t", 0.1); put("r", 0.5); put("b", 0.2)
            }
        ).toString()

        val blocks = deserializeForTest(oldFormatJson)

        assertEquals(1, blocks.size)
        assertNull(blocks[0].shape)
        assertEquals(BubbleType.SPEECH, blocks[0].bubbleType)
    }

    @Test
    fun `shape and type round-trip through serialize and deserialize`() {
        val original = TranslatedBlock(
            originalText = "Hi", translatedText = "Ahoj",
            leftF = 0.1f, topF = 0.1f, rightF = 0.5f, bottomF = 0.3f,
            shape = listOf(BubbleShapePoint(0.1f, 0.15f, 0.45f), BubbleShapePoint(0.3f, 0.12f, 0.48f)),
            bubbleType = BubbleType.SHOUT,
        )

        val json = serializeForTest(listOf(original))
        val roundTripped = deserializeForTest(json)

        assertEquals(1, roundTripped.size)
        assertEquals(BubbleType.SHOUT, roundTripped[0].bubbleType)
        assertEquals(2, roundTripped[0].shape!!.size)
        assertEquals(0.15f, roundTripped[0].shape!![0].leftF, 0.001f)
    }

    // Kopie formátu z TranslateRepository.serialize()/deserialize() - ty jsou private,
    // tenhle test ověřuje kontrakt JSON formátu, ne implementaci samotnou.
    private fun serializeForTest(blocks: List<TranslatedBlock>): String = JSONArray().also { arr ->
        blocks.forEach { b ->
            arr.put(JSONObject().apply {
                put("orig", b.originalText); put("trans", b.translatedText); put("disp", b.displayText)
                put("bg", b.bgColorArgb); put("sfx", b.isSfx); put("lc", b.lineCount); put("type", b.bubbleType.name)
                b.shape?.let { shape ->
                    put("shape", JSONArray().apply {
                        shape.forEach { p -> put(JSONArray().apply { put(p.yF.toDouble()); put(p.leftF.toDouble()); put(p.rightF.toDouble()) }) }
                    })
                }
                put("l", b.leftF.toDouble()); put("t", b.topF.toDouble()); put("r", b.rightF.toDouble()); put("b", b.bottomF.toDouble())
            })
        }
    }.toString()

    private fun deserializeForTest(json: String): List<TranslatedBlock> {
        val arr = JSONArray(json)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val translated = o.getString("trans")
            val shapeArr = o.optJSONArray("shape")
            val shape = if (shapeArr != null) List(shapeArr.length()) { j ->
                val p = shapeArr.getJSONArray(j)
                BubbleShapePoint(p.getDouble(0).toFloat(), p.getDouble(1).toFloat(), p.getDouble(2).toFloat())
            } else null
            TranslatedBlock(
                originalText = o.getString("orig"), translatedText = translated,
                leftF = o.getDouble("l").toFloat(), topF = o.getDouble("t").toFloat(),
                rightF = o.getDouble("r").toFloat(), bottomF = o.getDouble("b").toFloat(),
                displayText = o.optString("disp", translated),
                bgColorArgb = if (o.has("bg")) o.getInt("bg") else DEFAULT_BUBBLE_BG_ARGB,
                isSfx = o.optBoolean("sfx", false), lineCount = o.optInt("lc", 1),
                shape = shape,
                bubbleType = try { BubbleType.valueOf(o.optString("type", "SPEECH")) } catch (e: Exception) { BubbleType.SPEECH },
            )
        }
    }
}
```

- [ ] **Step 2: Spustit test a ověřit, že selže**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.haise.jiyu.translate.TranslatedBlockSerializationTest" --console=plain`
Expected: FAIL (kompilace - `TranslatedBlock` ještě nemá `shape`/`bubbleType`)

- [ ] **Step 3: Přidat pole do `TranslatedBlock.kt`**

V `app/src/main/kotlin/com/haise/jiyu/translate/TranslatedBlock.kt`, do `data class TranslatedBlock` přidej za `lineCount`:

```kotlin
    /** Skutečný obrys bubliny (flood-fill) - viz [BubbleShapeDetector]. Null = detekce selhala/starý cache formát, render použije heuristický obdélník z [layoutTranslationBlocks]. */
    val shape: List<BubbleShapePoint>? = null,
    /** Typ bubliny (SPEECH/SHOUT/THOUGHT/...) - viz [BubbleClassifier]. Určuje řez písma v ReaderScreen.kt (fontFamilyFor). */
    val bubbleType: BubbleType = BubbleType.SPEECH,
```

- [ ] **Step 4: Přidat `detectShapesOnly` do `OcrEngine.kt`**

V `app/src/main/kotlin/com/haise/jiyu/translate/OcrEngine.kt`, uvnitř třídy `OcrEngine` (za `recognize()`):

```kotlin
    /**
     * Dopočítá jen tvar bubliny pro už přeložené bloky ze starého cache formátu
     * (shape == null), bez nového OCR/ML Kit volání - viz TranslateRepository.getCachedPage
     * migrace. Blok, který už tvar má, nebo je SFX (nemá box vůbec), se přeskočí beze změny.
     */
    suspend fun detectShapesOnly(pageUrl: String, blocks: List<TranslatedBlock>): List<TranslatedBlock> = withContext(Dispatchers.IO) {
        val bitmap = loadBitmap(pageUrl) ?: return@withContext blocks
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return@withContext blocks
        val pixelSource = BitmapPixelSource(bitmap)
        blocks.map { tb ->
            if (tb.shape != null || tb.isSfx) return@map tb
            val shape = BubbleShapeDetector.detectShape(
                source = pixelSource,
                width = w,
                height = h,
                seeds = ringSeeds(tb.leftF, tb.topF, tb.rightF, tb.bottomF, w, h),
                bgColorArgb = tb.bgColorArgb,
            )
            tb.copy(shape = shape)
        }
    }
```

- [ ] **Step 5: Rozšířit serializaci a migraci v `TranslateRepository.kt`**

Nahraď `serialize()`:

```kotlin
    private fun List<TranslatedBlock>.serialize(): String = JSONArray().also { arr ->
        forEach { b ->
            arr.put(JSONObject().apply {
                put("orig", b.originalText)
                put("trans", b.translatedText)
                put("disp", b.displayText)
                put("bg", b.bgColorArgb)
                put("sfx", b.isSfx)
                put("lc", b.lineCount)
                put("type", b.bubbleType.name)
                b.shape?.let { shape ->
                    put("shape", JSONArray().apply {
                        shape.forEach { p ->
                            put(JSONArray().apply { put(p.yF.toDouble()); put(p.leftF.toDouble()); put(p.rightF.toDouble()) })
                        }
                    })
                }
                // put(String, float) na Android org.json.JSONObject neexistuje (jen desktopová
                // verze knihovny) -> NoSuchMethodError za běhu. Double overload existuje vždy.
                put("l", b.leftF.toDouble())
                put("t", b.topF.toDouble())
                put("r", b.rightF.toDouble())
                put("b", b.bottomF.toDouble())
            })
        }
    }.toString()
```

Nahraď `deserialize()`:

```kotlin
    /** disp/bg/sfx/lc/shape/type chybí ve starších cache záznamech - optXxx s výchozí hodnotou stejnou jako [TranslatedBlock] defaults, ať se nic nerozbije. */
    private fun TranslatedPageEntity.deserialize(): List<TranslatedBlock> = try {
        val arr = JSONArray(blocksJson)
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val translated = o.getString("trans")
            val shapeArr = o.optJSONArray("shape")
            val shape = if (shapeArr != null) {
                List(shapeArr.length()) { j ->
                    val p = shapeArr.getJSONArray(j)
                    BubbleShapePoint(yF = p.getDouble(0).toFloat(), leftF = p.getDouble(1).toFloat(), rightF = p.getDouble(2).toFloat())
                }
            } else null
            TranslatedBlock(
                originalText = o.getString("orig"),
                translatedText = translated,
                leftF = o.getDouble("l").toFloat(),
                topF = o.getDouble("t").toFloat(),
                rightF = o.getDouble("r").toFloat(),
                bottomF = o.getDouble("b").toFloat(),
                displayText = o.optString("disp", translated),
                bgColorArgb = if (o.has("bg")) o.getInt("bg") else DEFAULT_BUBBLE_BG_ARGB,
                isSfx = o.optBoolean("sfx", false),
                lineCount = o.optInt("lc", 1),
                shape = shape,
                bubbleType = try { BubbleType.valueOf(o.optString("type", "SPEECH")) } catch (e: Exception) { BubbleType.SPEECH },
            )
        }
    } catch (e: Exception) { emptyList() }
```

Uprav `getCachedPage` (přidej nepovinný `pageUrl` parametr, který spustí jednorázovou migraci tvaru u starých záznamů):

```kotlin
    /**
     * Vrátí výsledek z Room cache bez volání překladového API; null = není v cache.
     * @param pageUrl když je zadané a cache záznam ještě nemá dopočítaný tvar bubliny
     *   (starý formát), dopočítá se tvar (bez nového OCR/překladu) a cache se přepíše -
     *   viz OcrEngine.detectShapesOnly. Bez pageUrl (starší volající, co ho nemají po ruce)
     *   se migrace přeskočí a bloky zůstanou s shape=null (heuristický fallback v layoutu).
     */
    suspend fun getCachedPage(
        chapterId: String,
        pageIndex: Int,
        targetLanguage: String,
        sourceLanguage: String = "Auto",
        pageUrl: String? = null,
    ): List<TranslatedBlock>? {
        val id = cacheId(chapterId, pageIndex, targetLanguage, sourceLanguage)
        val cached = dao.getById(id)?.deserialize() ?: return null
        if (pageUrl == null) return cached

        val needsShapeMigration = cached.any { !it.isSfx && it.shape == null }
        if (!needsShapeMigration) return cached

        val migrated = ocrEngine.detectShapesOnly(pageUrl, cached)
        dao.upsert(TranslatedPageEntity(id = id, blocksJson = migrated.serialize()))
        return migrated
    }
```

Uprav volání v `translatePage()` (ať migraci reálně spustí, protože `pageUrl` už tam je k dispozici):

```kotlin
        getCachedPage(chapterId, pageIndex, targetLanguage, sourceLanguage, pageUrl)?.let { return it }
```

Nakonec dopl​ň `shape`/`bubbleType` do všech tří míst, kde se `TranslatedBlock` vytváří z `ClassifiedBubble` (`translateWithGemini`, `translateWithGroq`, `sfxBlock`) - v každém z nich přidej do konstruktoru:

```kotlin
                shape = c.raw.shape,
                bubbleType = c.bubbleType,
```

(Pozor: v `translateWithGemini`/`translateWithGroq` je proměnná `c: ClassifiedBubble` uvnitř `classified.mapIndexedNotNull`/`classified.map` lambdy; v `sfxBlock(c: ClassifiedBubble)` je `c` přímo parametr funkce - všude platí `c.raw.shape` a `c.bubbleType`.)

- [ ] **Step 6: Spustit testy a ověřit, že projdou**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.haise.jiyu.translate.TranslatedBlockSerializationTest" --console=plain`
Expected: PASS (2 testy)

Run: `./gradlew.bat :app:compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/translate/TranslatedBlock.kt app/src/main/kotlin/com/haise/jiyu/translate/OcrEngine.kt app/src/main/kotlin/com/haise/jiyu/translate/TranslateRepository.kt app/src/test/kotlin/com/haise/jiyu/translate/TranslatedBlockSerializationTest.kt
git commit -m "Feat: propagace tvaru bubliny do TranslatedBlock, cache + migrace starych zaznamu"
```

---

### Task 4: `TranslationLayout.kt` - přeskočit heuristiku, když je tvar k dispozici

**Files:**
- Modify: `app/src/main/kotlin/com/haise/jiyu/translate/TranslationLayout.kt`
- Modify: `app/src/test/kotlin/com/haise/jiyu/translate/TranslationLayoutTest.kt` (jen přidat nové testy, existující beze změny)

**Interfaces:**
- Consumes: `TranslatedBlock.shape` (Task 3)
- Produces: `fun layoutTranslationBlocks(blocks: List<TranslatedBlock>): List<PositionedTranslationBlock>` (stejná signatura, nové chování pro `shape != null`)

- [ ] **Step 1: Napsat padající testy pro shape-based layout**

Přidej do `app/src/test/kotlin/com/haise/jiyu/translate/TranslationLayoutTest.kt` (za existující `block(...)` helper, needit stávající testy):

```kotlin
    private fun blockWithShape(shape: List<BubbleShapePoint>, text: String = "x") =
        TranslatedBlock(
            originalText = text, translatedText = text,
            leftF = shape.minOf { it.leftF }, topF = shape.first().yF,
            rightF = shape.maxOf { it.rightF }, bottomF = shape.last().yF,
            shape = shape,
        )

    @Test
    fun `block with shape uses shape bounding box and skips heuristic expansion`() {
        val shape = listOf(
            BubbleShapePoint(0.20f, 0.30f, 0.60f),
            BubbleShapePoint(0.25f, 0.22f, 0.68f),
            BubbleShapePoint(0.30f, 0.25f, 0.65f),
        )
        val positioned = layoutTranslationBlocks(listOf(blockWithShape(shape)))

        assertEquals(1, positioned.size)
        val pos = positioned[0]
        // Ohraničující obdélník tvaru, ŽÁDNÁ heuristická expanze k okrajům stránky.
        assertEquals(0.22f, pos.leftF, 0.001f)
        assertEquals(0.68f, pos.rightF, 0.001f)
        assertEquals(0.20f, pos.minTopF, 0.001f)
        assertEquals(0.30f, pos.maxBottomF, 0.001f)
    }

    @Test
    fun `blocks with and without shape can coexist in the same page`() {
        val shape = listOf(BubbleShapePoint(0.10f, 0.10f, 0.30f), BubbleShapePoint(0.15f, 0.10f, 0.30f))
        val plain = block(0.60f, 0.60f, 0.80f, 0.65f)
        val positioned = layoutTranslationBlocks(listOf(blockWithShape(shape), plain))

        assertEquals(2, positioned.size)
        // Blok bez tvaru pořád projde starou heuristikou (vodorovně beze zbytku sousedů -> až k okrajům).
        val plainPositioned = positioned.first { it.block === plain }
        assertEquals(0f, plainPositioned.leftF)
        assertEquals(1f, plainPositioned.rightF)
    }
```

Přidej import na začátek souboru (za existující importy): `import org.junit.Assert.assertEquals` už tam pravděpodobně je (zkontroluj, ať se import neduplikuje).

- [ ] **Step 2: Spustit testy a ověřit, že selžou**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.haise.jiyu.translate.TranslationLayoutTest" --console=plain`
Expected: FAIL (nové testy - shape-based blok dnes projde stejnou heuristikou jako všechno ostatní, hodnoty nesedí)

- [ ] **Step 3: Rozdělit `layoutTranslationBlocks` na dispatcher + `layoutHeuristic`**

V `app/src/main/kotlin/com/haise/jiyu/translate/TranslationLayout.kt` přejmenuj současné tělo funkce
`layoutTranslationBlocks` na `private fun layoutHeuristic(blocks: List<TranslatedBlock>): List<PositionedTranslationBlock>`
(beze změny obsahu) a nad ni přidej nový veřejný dispatcher:

```kotlin
/**
 * Bloky se skutečně detekovaným tvarem bubliny (viz [BubbleShapeDetector]) použijí přímo
 * ohraničující obdélník tohohle tvaru - žádná heuristická expanze k sousedům/okrajům
 * stránky, protože už víme přesně, kde bublina končí. Bloky bez tvaru (detekce selhala,
 * nebo starý cache záznam ještě nedoběhl migrací) projdou beze změny starou heuristikou
 * ([layoutHeuristic]) - viz spec docs/superpowers/specs/2026-07-24-bubble-shape-and-font-design.md.
 */
fun layoutTranslationBlocks(blocks: List<TranslatedBlock>): List<PositionedTranslationBlock> {
    val shapeBased = blocks.filter { it.shape != null }
    val heuristicBased = blocks.filter { it.shape == null }

    val shapePositioned = shapeBased.map { b ->
        val shape = b.shape!!
        PositionedTranslationBlock(
            block = b,
            leftF = shape.minOf { it.leftF },
            topF = shape.first().yF,
            rightF = shape.maxOf { it.rightF },
            maxBottomF = shape.last().yF,
            minTopF = shape.first().yF,
        )
    }

    return shapePositioned + layoutHeuristic(heuristicBased)
}
```

(`shape.first().yF`/`shape.last().yF` fungují, protože `BubbleShapeDetector.detectShape` vrací body seřazené shora dolů - ověřeno v `BubbleShapeDetectorTest."sampled points are ordered from top to bottom"`.)

- [ ] **Step 4: Spustit testy a ověřit, že projdou**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.haise.jiyu.translate.TranslationLayoutTest" --console=plain`
Expected: PASS (všechny existující + 2 nové testy)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/translate/TranslationLayout.kt app/src/test/kotlin/com/haise/jiyu/translate/TranslationLayoutTest.kt
git commit -m "Feat: layoutTranslationBlocks pouzije presny tvar bubliny misto heuristiky, kdyz je k dispozici"
```

---

### Task 5: Fonty podle typu bubliny

**Files:**
- Create: `app/src/main/res/font/comic_neue_italic.ttf`
- Create: `app/src/main/res/font/comic_neue_bold_italic.ttf`
- Modify: `app/src/main/kotlin/com/haise/jiyu/ui/reader/ReaderScreen.kt`

**Interfaces:**
- Consumes: `TranslatedBlock.bubbleType` (Task 3), `BubbleType` enum (`com.haise.jiyu.translate.BubbleType`, už existuje)
- Produces: `private fun fontFamilyFor(bubbleType: BubbleType): FontFamily`
- Modifies: `AutoFitTranslatedText(text, bgColorArgb, boxWidth, maxHeight, textScale, bubbleType: BubbleType)` (nový povinný parametr)

- [ ] **Step 1: Stáhnout fonty**

```bash
curl -L -o "app/src/main/res/font/comic_neue_italic.ttf" "https://raw.githubusercontent.com/google/fonts/main/ofl/comicneue/ComicNeue-Italic.ttf"
curl -L -o "app/src/main/res/font/comic_neue_bold_italic.ttf" "https://raw.githubusercontent.com/google/fonts/main/ofl/comicneue/ComicNeue-BoldItalic.ttf"
```

Ověř, že soubory nejsou prázdné/HTML chybová stránka: `file app/src/main/res/font/comic_neue_italic.ttf app/src/main/res/font/comic_neue_bold_italic.ttf` musí ukázat `TrueType Font data` u obou (ne `HTML document` nebo 0 bytes).

- [ ] **Step 2: Nahradit `ComicNeueFamily` mapováním podle `BubbleType`**

V `app/src/main/kotlin/com/haise/jiyu/ui/reader/ReaderScreen.kt` přidej import (pokud tam ještě není):

```kotlin
import androidx.compose.ui.text.font.FontStyle
import com.haise.jiyu.translate.BubbleType
```

Nahraď:

```kotlin
/** Comic Neue - komiksové písmo s plnou podporou české diakritiky (ř,ž,č,š,ě,ň,ť,ů...), ne systémový font, který v malé bublině vypadá jako titulky, ne jako lettering. */
private val ComicNeueFamily = FontFamily(
    Font(R.font.comic_neue_regular, FontWeight.Normal),
    Font(R.font.comic_neue_bold, FontWeight.Bold),
)
```

za:

```kotlin
/**
 * Comic Neue - komiksové písmo s plnou podporou české diakritiky (ř,ž,č,š,ě,ň,ť,ů...), ne
 * systémový font, který v malé bublině vypadá jako titulky, ne jako lettering. Různé řezy
 * podle typu bubliny (viz BubbleType/fontFamilyFor) místo jednoho univerzálního - skutečná
 * vizuální analýza stylu písma z nízkorozlišeného OCR výřezu by byla nespolehlivá (viz spec
 * docs/superpowers/specs/2026-07-24-bubble-shape-and-font-design.md), tohle je praktičtější
 * přiblížení "co nejpodobnějšího originálu" fontu.
 */
private val ComicNeueRegular = FontFamily(Font(R.font.comic_neue_regular, FontWeight.Normal))
private val ComicNeueBold = FontFamily(Font(R.font.comic_neue_bold, FontWeight.Bold))
private val ComicNeueItalic = FontFamily(Font(R.font.comic_neue_italic, FontWeight.Normal, FontStyle.Italic))
private val ComicNeueBoldItalic = FontFamily(Font(R.font.comic_neue_bold_italic, FontWeight.Bold, FontStyle.Italic))

private fun fontFamilyFor(bubbleType: BubbleType): FontFamily = when (bubbleType) {
    BubbleType.SHOUT -> ComicNeueBold
    BubbleType.THOUGHT, BubbleType.WHISPER -> ComicNeueItalic
    BubbleType.SPEECH, BubbleType.NARRATION, BubbleType.SYSTEM, BubbleType.SFX -> ComicNeueRegular
}
```

V `AutoFitTranslatedText` přidej parametr `bubbleType: BubbleType` a nahraď obě místa, kde se používá `ComicNeueFamily`:

```kotlin
@Composable
private fun AutoFitTranslatedText(
    text: String,
    bgColorArgb: Int,
    boxWidth: androidx.compose.ui.unit.Dp,
    maxHeight: androidx.compose.ui.unit.Dp,
    textScale: Float,
    bubbleType: BubbleType,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val fontFamily = fontFamilyFor(bubbleType)
    val baseFontSp = 11f * textScale
    val minFontSp = 6f * textScale
    val widthPx = with(density) { boxWidth.roundToPx() }.coerceAtLeast(1)
    val maxHeightPx = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)

    val fontSp = remember(text, widthPx, maxHeightPx, baseFontSp, fontFamily) {
        var fs = baseFontSp
        while (fs > minFontSp) {
            val measured = textMeasurer.measure(
                text = text,
                style = TextStyle(fontSize = fs.sp, lineHeight = (fs * 1.25f).sp, fontFamily = fontFamily),
                constraints = Constraints(maxWidth = widthPx),
            )
            if (measured.size.height <= maxHeightPx) break
            fs -= 0.5f
        }
        fs.coerceAtLeast(minFontSp)
    }

    val bg = Color(bgColorArgb)
    val luminance = 0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue
    val textColor = if (luminance < 0.5f) Color.White else Color.Black

    Text(
        text = text,
        color = textColor,
        fontSize = fontSp.sp,
        lineHeight = (fontSp * 1.25f).sp,
        fontFamily = fontFamily,
    )
}
```

Uprav obě volání `AutoFitTranslatedText(...)` (ve `WebtoonPage` a `TranslationOverlay`) - přidej `bubbleType = pos.block.bubbleType,`.

- [ ] **Step 3: Ověřit kompilaci**

Run: `./gradlew.bat :app:compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL (žádný automatizovaný test pro font rendering - vizuální věc, ověří se živě na emulátoru v Task 6)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/font/comic_neue_italic.ttf app/src/main/res/font/comic_neue_bold_italic.ttf app/src/main/kotlin/com/haise/jiyu/ui/reader/ReaderScreen.kt
git commit -m "Feat: font podle typu bubliny (tucne na krik, kurziva na myslenku/sepot)"
```

---

### Task 6: Vykreslení skutečného tvaru bubliny v `ReaderScreen.kt`

**Files:**
- Modify: `app/src/main/kotlin/com/haise/jiyu/ui/reader/ReaderScreen.kt`

**Interfaces:**
- Consumes: `PositionedTranslationBlock` (Task 4, nezměněná struktura), `fontFamilyFor`/`AutoFitTranslatedText` (Task 5)
- Produces: `private class BubbleClipShape(...) : Shape`

- [ ] **Step 1: Přidat importy**

Přidej (pokud chybí):

```kotlin
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.haise.jiyu.translate.BubbleShapePoint
```

- [ ] **Step 2: Implementovat `BubbleClipShape`**

Přidej k ostatním privátním composable pomocníkům v `ReaderScreen.kt` (např. hned před `WebtoonPage`):

```kotlin
/**
 * Compose Shape, co kopíruje skutečný obrys bubliny z [BubbleShapePoint] seznamu místo
 * pevného zaobleného obdélníku. Body jsou v normalizovaných (0..1) souřadnicích stránky -
 * shapeTopF/shapeBottomF/leftMinF/rightMaxF (= PositionedTranslationBlock.minTopF/maxBottomF/
 * leftF/rightF pro shape-based blok, viz TranslationLayout.kt) je přemapují na velikost
 * skutečně vykresleného boxu.
 */
private class BubbleClipShape(
    private val points: List<BubbleShapePoint>,
    private val shapeTopF: Float,
    private val shapeBottomF: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        if (points.size < 2 || shapeBottomF <= shapeTopF) {
            return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        }
        val yRange = shapeBottomF - shapeTopF
        val leftMinF = points.minOf { it.leftF }
        val rightMaxF = points.maxOf { it.rightF }
        val spanF = (rightMaxF - leftMinF).coerceAtLeast(0.0001f)

        fun py(p: BubbleShapePoint) = ((p.yF - shapeTopF) / yRange) * size.height
        fun pxLeft(p: BubbleShapePoint) = ((p.leftF - leftMinF) / spanF) * size.width
        fun pxRight(p: BubbleShapePoint) = ((p.rightF - leftMinF) / spanF) * size.width

        val path = Path()
        path.moveTo(pxLeft(points.first()), py(points.first()))
        points.forEach { path.lineTo(pxLeft(it), py(it)) }
        points.asReversed().forEach { path.lineTo(pxRight(it), py(it)) }
        path.close()
        return Outline.Generic(path)
    }
}
```

- [ ] **Step 3: Napojit clip shape a font do `WebtoonPage`**

V `WebtoonPage` nahraď blok počítající `minHeight`/box:

```kotlin
                    val minHeight = (size.height * (pos.block.bottomF - pos.minTopF)).toInt().toDp().coerceAtLeast(0.dp) + TRANSLATION_BOX_BLEED * 2
                    val maxHeight = (size.height * (pos.maxBottomF - pos.minTopF)).toInt().toDp().coerceAtLeast(0.dp) + TRANSLATION_BOX_BLEED * 2
                    Box(
                        modifier = Modifier
                            .offset(
                                x = (size.width * pos.leftF).toInt().toDp() - TRANSLATION_BOX_BLEED,
                                y = (size.height * pos.minTopF).toInt().toDp() - TRANSLATION_BOX_BLEED,
                            )
                            .width(boxWidth)
                            .heightIn(min = minHeight)
                            .background(Color(pos.block.bgColorArgb).copy(alpha = TRANSLATION_BOX_ALPHA), RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AutoFitTranslatedText(
                            text = pos.block.displayText,
                            bgColorArgb = pos.block.bgColorArgb,
                            boxWidth = boxWidth,
                            maxHeight = maxHeight,
                            textScale = textScale,
                        )
                    }
```

za:

```kotlin
                    // Blok se skutečným tvarem bubliny má pevnou výšku přesně podle tvaru
                    // (žádný "prostor pro růst") - block.bottomF (jen OCR text, ne celá
                    // bublina) by tady byl zavádějící, viz TranslationLayout.kt.
                    val effectiveMinBottomF = pos.block.shape?.let { pos.maxBottomF } ?: pos.block.bottomF
                    val minHeight = (size.height * (effectiveMinBottomF - pos.minTopF)).toInt().toDp().coerceAtLeast(0.dp) + TRANSLATION_BOX_BLEED * 2
                    val maxHeight = (size.height * (pos.maxBottomF - pos.minTopF)).toInt().toDp().coerceAtLeast(0.dp) + TRANSLATION_BOX_BLEED * 2
                    val clipShape = pos.block.shape?.let { BubbleClipShape(it, pos.minTopF, pos.maxBottomF) } ?: RoundedCornerShape(3.dp)
                    Box(
                        modifier = Modifier
                            .offset(
                                x = (size.width * pos.leftF).toInt().toDp() - TRANSLATION_BOX_BLEED,
                                y = (size.height * pos.minTopF).toInt().toDp() - TRANSLATION_BOX_BLEED,
                            )
                            .width(boxWidth)
                            .heightIn(min = minHeight)
                            // .clip() (ne jen .background(color, shape)) - background by jinak
                            // ohraničil tvarem jen VYKRESLENÉ pozadí, ne obsah uvnitř (Text by
                            // u nepravidelného tvaru bubliny mohl přesahovat přes okraj u ostrých
                            // rohů/ocasu, protože background(color, shape) neomezuje potomky).
                            .clip(clipShape)
                            .background(Color(pos.block.bgColorArgb).copy(alpha = TRANSLATION_BOX_ALPHA))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AutoFitTranslatedText(
                            text = pos.block.displayText,
                            bgColorArgb = pos.block.bgColorArgb,
                            boxWidth = boxWidth,
                            maxHeight = maxHeight,
                            textScale = textScale,
                            bubbleType = pos.block.bubbleType,
                        )
                    }
```

- [ ] **Step 4: Napojit stejnou změnu do `TranslationOverlay`**

Aplikuj analogickou úpravu (stejný princip: `effectiveMinBottomF`, `clipShape`, `bubbleType` parametr) ve funkci `TranslationOverlay`:

```kotlin
    val left = maxWidth  * pos.leftF - TRANSLATION_BOX_BLEED
    val top  = maxHeight * pos.minTopF - TRANSLATION_BOX_BLEED
    val w    = (maxWidth  * (pos.rightF     - pos.leftF)).coerceAtLeast(0.dp) + TRANSLATION_BOX_BLEED * 2
    val effectiveMinBottomF = pos.block.shape?.let { pos.maxBottomF } ?: pos.block.bottomF
    val minH = (maxHeight * (effectiveMinBottomF - pos.minTopF)).coerceAtLeast(0.dp) + TRANSLATION_BOX_BLEED * 2
    val maxH = (maxHeight * (pos.maxBottomF - pos.minTopF)).coerceAtLeast(0.dp) + TRANSLATION_BOX_BLEED * 2
    val clipShape = pos.block.shape?.let { BubbleClipShape(it, pos.minTopF, pos.maxBottomF) } ?: RoundedCornerShape(3.dp)

    Box(
        modifier = Modifier
            .offset(x = left, y = top)
            .width(w)
            .heightIn(min = minH)
            .clip(clipShape)
            .background(Color(pos.block.bgColorArgb).copy(alpha = TRANSLATION_BOX_ALPHA))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        AutoFitTranslatedText(
            text = pos.block.displayText,
            bgColorArgb = pos.block.bgColorArgb,
            boxWidth = w,
            maxHeight = maxH,
            textScale = textScale,
            bubbleType = pos.block.bubbleType,
        )
    }
```

(Pozor: proměnná se dřív jmenovala `minH`, ne `minHeight`, v `TranslationOverlay` - zachovej původní název, jen zdroj hodnoty změň na `effectiveMinBottomF`.)

- [ ] **Step 5: Sestavit a spustit celou testovací sadu**

Run: `./gradlew.bat :app:compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL

Run: `./gradlew.bat :app:testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL (všechny testy, včetně Task 1-4 novych, projdou)

- [ ] **Step 6: Živé ověření na emulátoru**

1. `./gradlew.bat :app:assembleDebug --console=plain`
2. Nainstalovat na `jiyu_test` AVD (viz `docs`/paměť projektu pro přesný postup s JAVA_HOME/adb).
3. Otevřít mangu se stránkou, co má bublinu s "ocasem"/oválným tvarem (ne obdélníkovou), zapnout překlad.
4. Ověřit: box vizuálně kopíruje tvar bubliny (ne obdélník přes půl stránky), text v bublině typu SHOUT je tučný, v THOUGHT/WHISPER kurzívou.
5. Ověřit fallback: bublina, kde flood-fill logicky selže (SFX text přímo na kresbě), pořád zůstane bez boxu (SFX se nepřekládá) nebo dostane starý heuristický obdélník (ne pád appky).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/ui/reader/ReaderScreen.kt
git commit -m "Feat: box pro preklad kopiruje skutecny tvar bubliny (BubbleClipShape)"
```

---

## Self-Review (proveden autorem plánu)

1. **Pokrytí spec:** Problém 1 (přetahování boxu) → Task 4 (bounding box z tvaru, žádná heuristika). Problém 2 (tvar boxu) → Task 6 (`BubbleClipShape`). Font podle typu → Task 5. Migrace starých cache záznamů → Task 3 (`getCachedPage` + `detectShapesOnly`). Fallback při selhání detekce → Task 1 (`null` návrat) + Task 4/6 (větve `shape == null`). Testování → Task 1/2/3/4 mají automatizované JVM testy, Task 5/6 (čistě vizuální) mají explicitní live-test krok.
2. **Placeholder scan:** žádné TBD/TODO, všechny kroky mají skutečný kód.
3. **Konzistence typů:** `BubbleShapePoint(yF, leftF, rightF)` používaný stejně napříč Task 1/2/3/4/6. `PositionedTranslationBlock` beze změny struktury (jen jinak plněná v Task 4). `TranslatedBlock.shape`/`bubbleType` zavedené v Task 3, používané konzistentně v Task 4/5/6.
4. **Rozsah:** jeden ucelený scope (tvar + font), 6 úkolů, každý nechává projekt zkompilovatelný a otestovaný.
