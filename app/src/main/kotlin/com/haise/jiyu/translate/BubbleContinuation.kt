package com.haise.jiyu.translate

import kotlin.math.max
import kotlin.math.min

/**
 * Které bubliny jsou POKRAČOVÁNÍM věty z bubliny předchozí.
 *
 * Proč to existuje: model dostával bubliny jako plochý seznam textů a neměl jak poznat, že
 * dvě z nich tvoří jednu repliku. Kaskádová věta rozdělená do dvou bublin (úvodní citoslovce
 * nahoře, zbytek dole) se pak překládala po kouscích - každá půlka bez druhé, takže z první
 * vypadl úvod nebo se druhá přeložila jako samostatná věta a návaznost se ztratila.
 *
 * Návaznost se počítá tady a modelu se předává jako fakt (viz [GeminiUltraPrompt.buildUserPrompt]).
 * Spoléhat na to, že si ji odvodí sám z pořadí, nestačí: v JEDNÉ dávce jde i několik stránek
 * najednou, takže sousední položky seznamu spolu vůbec nemusí souviset.
 *
 * Rozhoduje se ze dvou nezávislých signálů, které musí platit ZÁROVEŇ:
 *  1) předchozí text nekončí koncem věty (končí čárkou, výpustkou, nebo interpunkci nemá vůbec),
 *  2) tahle bublina leží POD ní a vodorovně se s ní překrývá, tedy patří do stejného sloupce
 *     dialogu - dvě bubliny vedle sebe bývají dva různí mluvčí, ne jedna věta.
 *
 * @return indexy bublin, které navazují na tu bezprostředně předchozí
 */
internal fun detectContinuations(bubbles: List<ClassifiedBubble>): Set<Int> {
    val result = mutableSetOf<Int>()
    for (i in 1 until bubbles.size) {
        val previous = bubbles[i - 1]
        val current = bubbles[i]
        // Zvuk do věty nepatří - ani jako její začátek, ani jako pokračování.
        if (previous.isSfx || current.isSfx) continue
        if (endsSentence(previous.raw.text)) continue
        if (!isStackedBelow(previous.raw, current.raw)) continue
        result += i
    }
    return result
}

/**
 * Končí text koncem věty? Bere v úvahu i CJK interpunkci - japonská "。" nebo uzavírací "」"
 * ukončují větu stejně jako latinská tečka, jen se na ni nepodobají.
 *
 * Výpustka ("...") se schválně NEPOČÍTÁ jako konec: v komiksu skoro vždy znamená, že věta
 * pokračuje ve vedlejší bublině.
 */
private fun endsSentence(text: String): Boolean {
    val trimmed = text.trim().trimEnd('"', '\'', '”', '’', ')', ']')
    if (trimmed.isEmpty()) return true
    if (trimmed.endsWith("...") || trimmed.endsWith("…")) return false
    return trimmed.last() in SENTENCE_END_CHARS
}

/**
 * Leží [current] pod [previous] a ve stejném sloupci? Vodorovný překryv se měří vůči té užší
 * z obou bublin, aby úzká navazující bublina pod širokou pořád prošla.
 */
private fun isStackedBelow(previous: RawTextBlock, current: RawTextBlock): Boolean {
    if (current.topF < previous.bottomF - VERTICAL_TOLERANCE_F) return false
    val gap = current.topF - previous.bottomF
    if (gap > MAX_VERTICAL_GAP_F) return false

    val overlap = min(previous.rightF, current.rightF) - max(previous.leftF, current.leftF)
    if (overlap <= 0f) return false
    val narrower = min(previous.rightF - previous.leftF, current.rightF - current.leftF)
    if (narrower <= 0f) return false
    return overlap / narrower >= MIN_HORIZONTAL_OVERLAP_RATIO
}

/** Tečka, otazník, vykřičník - a jejich CJK protějšky včetně uzavíracích uvozovek. */
private val SENTENCE_END_CHARS = setOf(
    '.', '!', '?', '。', '！', '？', '」', '』', '”',
)

/** Bubliny se smí o kousek překrývat (OCR box bývá volnější než kresba). */
private const val VERTICAL_TOLERANCE_F = 0.02f

/**
 * Jak daleko od sebe smí být, aby ještě šlo o jednu repliku. Zhruba desetina výšky stránky -
 * dál už je to jiný panel, ne pokračování věty.
 */
private const val MAX_VERTICAL_GAP_F = 0.10f

/**
 * Kolik z užší bubliny se musí vodorovně překrývat, aby šlo o stejný sloupec dialogu.
 *
 * Dřív tu byla 0,35 - odhad, který mlčky předpokládal, že dvě bubliny téže repliky leží zhruba
 * pod sebou. U kaskádové ("sněhulákové") bubliny to neplatí: laloky jsou ZÁMĚRNĚ posunuté do
 * stran, právě to jim dává ten schodovitý tvar. Změřeno na nahlášené stránce (1440x3120):
 * horní lalok x=0.321..0.540, spodní x=0.501..0.815, tedy překryv 0,039 = **0,178** užšího
 * z nich. Podmínka neprošla, model se o návaznosti nedozvěděl a obě půlky jedné věty přeložil
 * odděleně - přesně to, kvůli čemu [detectContinuations] vzniklo.
 *
 * Snížení je bezpečné, protože rozlišování nestojí na tomhle čísle: většinu práce odvede
 * [endsSentence] (replika jiného mluvčího skoro vždy končí tečkou/otazníkem) a strop
 * [MAX_VERTICAL_GAP_F]. Chybné spojení navíc nic nerozbije - text zůstává ve své bublině
 * (viz „železná pravidla" v [GeminiUltraPrompt]), model jen dostane kontext navíc. Chybějící
 * spojení naopak větu roztrhne vejpůl, což uživatel nahlásil.
 */
private const val MIN_HORIZONTAL_OVERLAP_RATIO = 0.15f
