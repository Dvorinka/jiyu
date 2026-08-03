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
 * ## Proč se odlehlé vzorky ignorují
 * Původní verze brala NEJVĚTŠÍ odchylku od průměru: stačil jediný vzorek mimo, a celá bublina
 * se přeřadila z výplně na záplatu. A takový vzorek vzniká úplně běžně, protože prstenec se
 * vzorkuje jen [OcrEngine] pár pixelů od OCR boxu a ten box občas kraj písmene ořízne - vzorek
 * pak padne rovnou na tah písma.
 *
 * Změřeno na zařízení (PunctuationBlockProbeTest): replika "SURVIVOR..." uprostřed čistě BÍLÉ
 * bubliny vyšla jako `bgUniform=false`, přestože kolem ní žádná kresba není. Dostala tedy
 * záplatu, ta z principu nedočistí všechno - a přesně tak vypadaly nahlášené snímky, kde pod
 * přeloženým textem zůstávaly zbytky originálu.
 *
 * Proto se místo maxima bere [UNIFORM_PERCENTILE] percentil odchylek: hrst vzorků, které
 * spadly na písmeno, verdikt neovlivní, ale skutečně pestré okolí (kde je mimo tolerancí
 * většina vzorků) se pozná dál.
 */
internal fun isBackgroundUniform(
    samples: List<IntArray>,
    threshold: Int = UNIFORM_COLOR_THRESHOLD,
): Boolean {
    if (samples.size < 2) return true

    val avgR = samples.sumOf { it[0] } / samples.size
    val avgG = samples.sumOf { it[1] } / samples.size
    val avgB = samples.sumOf { it[2] } / samples.size

    val deviations = samples
        .map { s -> maxOf(abs(s[0] - avgR), abs(s[1] - avgG), abs(s[2] - avgB)) }
        .sorted()

    // Index percentilu; u velmi malých sad spadne na poslední prvek, tedy na dřívější chování -
    // z pěti vzorků se nedá nic "odlehlého" spolehlivě vyloučit.
    val index = ((deviations.size - 1) * UNIFORM_PERCENTILE / 100).coerceIn(0, deviations.size - 1)
    return deviations[index] <= threshold
}

/**
 * Kolik procent vzorků musí být uvnitř tolerance. Zbylých 15 % je rozpočet právě na ty vzorky,
 * které padly na tah písma kvůli těsnému OCR boxu - prstenec má kolem 80 vzorků rozdělených na
 * čtyři strany, takže i celý zasažený kus jedné strany se do rozpočtu vejde.
 */
private const val UNIFORM_PERCENTILE = 85

/** Kolik smí být rozdíl mezi vzorkem a průměrem, aby ještě šlo o tutéž barvu pozadí. */
internal const val UNIFORM_COLOR_THRESHOLD = 45
