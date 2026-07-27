package com.haise.jiyu.translate

/**
 * Slučování OCR řádků do bublinových bloků - extrahováno z [OcrEngine], aby šlo testovat čistě
 * (bez Bitmap/Androidu), stejný důvod jako [ReadingOrder]/[BubbleShapeAnalysis].
 *
 * Bez [hasWallBetween] vetovalo sloučení pouze geometrii ([shouldMerge]) - dvě GEOMETRICKY
 * blízké, ale VIZUÁLNĚ oddělené bubliny/captions (jiná bublina vedle, jiný barevný box na
 * stránce s reklamou) se tak sloučily do jednoho bloku: jeden zmizel beze zbytku (viz uživatelská
 * zpětná vazba - "HOW DID YOU MANAGE..." bublina úplně chyběla) a druhý na stránce s reklamou
 * na anime vznikl jako jedna přebujelá barevná placka přes půl stránky. [hasWallBetween] tomu
 * brání kontrolou pixelů MEZI kandidáty - přes jednu bublinu jde vždycky rovná čára stejné
 * barvy pozadí, mezi dvěma RŮZNÝMI bublinami/boxy je vždycky někde obrys nebo jiná barva.
 */

/**
 * Řádky stejné bubliny mívají mezeru mnohem menší než výška písma; mezi bublinami bývá mezera
 * srovnatelná s výškou písma nebo větší. Čistě geometrický odhad - žádná záruka, proto
 * [hasWallBetween] jako druhá, vizuální pojistka v [mergeNearbyLines].
 */
internal fun shouldMerge(a: RawTextBlock, b: RawTextBlock): Boolean {
    val avgHeight = ((a.bottomF - a.topF) + (b.bottomF - b.topF)) / 2f
    if (avgHeight <= 0f) return false

    val verticalGap = maxOf(0f, maxOf(a.topF, b.topF) - minOf(a.bottomF, b.bottomF))
    val horizontalOverlap = minOf(a.rightF, b.rightF) - maxOf(a.leftF, b.leftF)
    val horizontalGap = maxOf(0f, maxOf(a.leftF, b.leftF) - minOf(a.rightF, b.rightF))

    return verticalGap < avgHeight * 0.9f && (horizontalOverlap > 0f || horizontalGap < avgHeight * 1.8f)
}

/**
 * Spojí OCR řádky, které leží blízko sebe (viz [shouldMerge]) A mezi kterými není vizuální
 * "zeď" (viz [noWallBetween], výchozí hodnota `{ _, _ -> true }` = stará čistě geometrická
 * logika, používaná v testech bez Bitmapy) do jednoho bloku - to bývá jedna bublina s víc
 * řádky. Union-Find nad dvojicovým testem: O(n²), ale n (řádků na stránku) bývá v řádu
 * jednotek až nízkých desítek, takže to není problém výkonu.
 */
internal fun mergeNearbyLines(
    lines: List<RawTextBlock>,
    noWallBetween: (RawTextBlock, RawTextBlock) -> Boolean = { _, _ -> true },
): List<RawTextBlock> {
    if (lines.isEmpty()) return emptyList()
    val parent = IntArray(lines.size) { it }
    fun find(x: Int): Int {
        var r = x
        while (parent[r] != r) r = parent[r]
        var c = x
        while (parent[c] != r) { val next = parent[c]; parent[c] = r; c = next }
        return r
    }
    fun union(a: Int, b: Int) {
        val ra = find(a); val rb = find(b)
        if (ra != rb) parent[ra] = rb
    }

    for (i in lines.indices) {
        for (j in i + 1 until lines.size) {
            if (shouldMerge(lines[i], lines[j]) && noWallBetween(lines[i], lines[j])) union(i, j)
        }
    }

    return lines.indices.groupBy { find(it) }.map { (_, idxs) ->
        val group = idxs.map { lines[it] }.sortedWith(compareBy({ it.topF }, { it.leftF }))
        RawTextBlock(
            text = group.joinToString(" ") { it.text },
            leftF = group.minOf { it.leftF },
            topF = group.minOf { it.topF },
            rightF = group.maxOf { it.rightF },
            bottomF = group.maxOf { it.bottomF },
            lineCount = group.size,
        )
    }
}

private fun colorDistance(a: Int, b: Int): Double {
    val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
    val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
    val db = (a and 0xFF) - (b and 0xFF)
    return Math.sqrt((dr * dr + dg * dg + db * db).toDouble())
}

/** Průměrná barva prstence bodů kolem bloku (viz [ringSeeds]) - "co je vlastní pozadí tohohle řádku". */
private fun ringColor(source: PixelSource, width: Int, height: Int, block: RawTextBlock): Int {
    val seeds = ringSeeds(block.leftF, block.topF, block.rightF, block.bottomF, width, height)
        .filter { (x, y) -> x in 0 until width && y in 0 until height }
    if (seeds.isEmpty()) return source.colorAt((width / 2).coerceIn(0, width - 1), (height / 2).coerceIn(0, height - 1))
    val colors = seeds.map { (x, y) -> source.colorAt(x, y) }
    val r = colors.sumOf { (it shr 16) and 0xFF } / colors.size
    val g = colors.sumOf { (it shr 8) and 0xFF } / colors.size
    val b = colors.sumOf { it and 0xFF } / colors.size
    return (r shl 16) or (g shl 8) or b
}

/**
 * True, když cesta mezi [a] a [b] (vzorkovaná uprostřed úsečky spojující jejich středy, mimo
 * dosah samotného textu na obou koncích) protíná barvu, která neodpovídá pozadí ANI jednoho
 * z bloků - to je skutečná hranice (obrys bubliny, jiný barevný box, kus kresby mezi nimi),
 * ne pokračování téže bubliny. Volající (viz [mergeNearbyLines]) tohle bere jako veto proti
 * sloučení, i když geometrie ([shouldMerge]) sloučení jinak dovoluje.
 */
fun hasWallBetween(
    source: PixelSource,
    width: Int,
    height: Int,
    a: RawTextBlock,
    b: RawTextBlock,
    colorDistanceThreshold: Int = 40,
): Boolean {
    if (width <= 0 || height <= 0) return false

    val aColor = ringColor(source, width, height, a)
    val bColor = ringColor(source, width, height, b)

    val acx = (a.leftF + a.rightF) / 2f * width
    val acy = (a.topF + a.bottomF) / 2f * height
    val bcx = (b.leftF + b.rightF) / 2f * width
    val bcy = (b.topF + b.bottomF) / 2f * height

    // Jen prostřední úsek úsečky (t=0.3..0.7) - blízko konců bychom snadno vzorkovali
    // ještě uvnitř samotného textu jednoho z bloků, ne skutečnou mezeru mezi nimi.
    val gapFractions = listOf(0.3f, 0.4f, 0.5f, 0.6f, 0.7f)
    for (t in gapFractions) {
        val x = (acx + (bcx - acx) * t).toInt().coerceIn(0, width - 1)
        val y = (acy + (bcy - acy) * t).toInt().coerceIn(0, height - 1)
        val c = source.colorAt(x, y)
        val distA = colorDistance(c, aColor)
        val distB = colorDistance(c, bColor)
        if (distA >= colorDistanceThreshold && distB >= colorDistanceThreshold) return true
    }
    return false
}
