package com.haise.jiyu.translate

import kotlin.math.abs

/**
 * Je navzorkované okolí textu JEDNOLITÉ pozadí (skutečná nakreslená bublina), nebo pestrá
 * kresba (text vysázený přímo do obrázku)?
 *
 * Rozhoduje to o tom, čím se originál zakryje: jednolité pozadí dostane plnou výplň, která
 * text spolehlivě přemaluje, kdežto pestré dostane ZÁPLATU, která zakrývá jen tahy písmen a
 * zbytek kresby nechává prosvítat (viz [buildTextPatch] a [patchPlan]).
 *
 * Extrahováno z [OcrEngine] do čisté funkce, aby to šlo testovat bez Bitmapy a Androidu -
 * stejný důvod jako u [mergeNearbyLines] nebo [ReadingOrder].
 *
 * ## Rozhoduje NEJVĚTŠÍ odchylka - a odlehlé vzorky se ZÁMĚRNĚ neignorují
 * Tohle už jednou ustoupilo percentilu a byla to chyba, kterou je potřeba mít zapsanou, aby
 * ji nikdo neudělal podruhé.
 *
 * Motiv byl rozumný: prstenec se vzorkuje jen pár pixelů od OCR boxu (viz [OcrEngine]) a ten
 * box občas kraj písmene ořízne, takže vzorek padne rovnou na tah písma. Změřeno na zařízení
 * (PunctuationBlockProbeTest): replika "SURVIVOR..." uprostřed čistě BÍLÉ bubliny vyšla jako
 * `bgUniform=false` a dostala záplatu, která z principu nedočistí všechno - odtud hlášené
 * zbytky originálu pod přeloženým textem. Podmínka proto začala brát 85. percentil odchylek,
 * aby hrst vzorků na písmenu verdikt nepřehodila.
 *
 * Rozbilo to přesně to, kvůli čemu celá záplata vznikla. Vodovková bitevní scéna z Vagabonda
 * je barevně DOCELA jednotná: většina prstence padne do tolerance a mimo ni je jen menšina
 * (kmen stromu, tmavý terén pod popiskem). S percentilem tedy prošla jako "jednolité pozadí",
 * dostala plnou výplň - a přes kresbu se rozlila placka. Uživatel to hlásil okamžitě a
 * doslova: původní vykreslení bylo o dost lepší.
 *
 * Podstatné je, PROČ to nejde doladit jinou mezí: z pouhých vzorků je "pár odlehlých tmavých
 * hodnot" **nerozlišitelné** mezi tahem písmene a tmavým detailem kresby. Percentil tedy
 * nemůže být ten mechanismus, ať dostane jakýkoliv práh. Kdo bude chtít zbytky originálu
 * v bublině dořešit, musí sáhnout jinam - typicky na to, KDE se prstenec vzorkuje, aby na
 * písmeno nesahal vůbec (OCR box je jen aproximace otisku, viz [buildTextPatch]) - a musí to
 * změřit na skutečné stránce, ne na syntetické replice.
 *
 * Cena, kterou tahle přísnost stojí, je známá a vědomě přijatá: v bublině může pod překladem
 * zůstat drobný zbytek originálu. Placka přes kresbu je horší.
 */
internal fun isBackgroundUniform(
    samples: List<IntArray>,
    threshold: Int = UNIFORM_COLOR_THRESHOLD,
): Boolean {
    if (samples.size < 2) return true

    val avgR = samples.sumOf { it[0] } / samples.size
    val avgG = samples.sumOf { it[1] } / samples.size
    val avgB = samples.sumOf { it[2] } / samples.size

    // Stačí JEDINÝ vzorek mimo toleranci - viz komentář výše k tomu, proč se odlehlé hodnoty
    // nesmí odfiltrovat.
    return samples.all { s ->
        maxOf(abs(s[0] - avgR), abs(s[1] - avgG), abs(s[2] - avgB)) <= threshold
    }
}

/** Kolik smí být rozdíl mezi vzorkem a průměrem, aby ještě šlo o tutéž barvu pozadí. */
internal const val UNIFORM_COLOR_THRESHOLD = 45
