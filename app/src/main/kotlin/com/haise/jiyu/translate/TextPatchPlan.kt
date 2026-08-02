package com.haise.jiyu.translate

/** Obdélník v normalizovaných (0..1) souřadnicích stránky. */
data class PatchRect(
    val leftF: Float,
    val topF: Float,
    val rightF: Float,
    val bottomF: Float,
)

/**
 * Obdélník, přes který se bublina fyzicky vykreslí - a tedy i jaký kus stránky musí zakrýt.
 *
 * Existuje kvůli JEDINÉMU pravidlu: záplata (viz [TextPatchProvider]) se kreslí přesně přes
 * tenhle obdélník, takže se musí přes něj i POČÍTAT. Dokud si obojí odvozovalo souřadnice
 * samo, rozešlo se to - a rozdíl nebyl neškodný posun, ale roztažení (viz [patchPlan]).
 */
fun renderBoxRect(pos: PositionedTranslationBlock): PatchRect =
    PatchRect(
        leftF = pos.leftF,
        topF = pos.minTopF,
        rightF = pos.rightF,
        bottomF = pos.maxBottomF,
    )

/**
 * Které bloky chtějí záplatu pozadí a přes jaký obdélník stránky se má spočítat.
 * Klíčem je pozice bloku v [positioned] (ne v původním seznamu bloků).
 *
 * ## Proč se obdélník bere z rozvržení, ne z OCR boxu
 * Záplata se vykresluje přes celý box bubliny, ale počítala se z OCR boxu samotného textu.
 * Ten je vždycky menší - u bubliny s detekovaným tvarem je box celý obrys balónku, u textu
 * na kresbě ho [layoutTranslationBlocks] roztahuje do stran - takže se malá záplata natáhla
 * přes velkou plochu. Zbytky tahů, které inpainting nedočistil, se tím ZVĚTŠILY a posunuly
 * mimo své místo: uživatel je hlásil jako rozmazaný cizí text prosvítající uprostřed
 * přeložené bubliny. Naměřeno na nahlášené stránce: řádkování zbytku 88 px proti 62 px
 * v originále, tedy zvětšení 1,4x.
 *
 * ## Proč bublina s detekovaným tvarem záplatu nedostává
 * Flood-fill najde obrys jen tam, kde je uvnitř souvislá plocha jedné barvy - to je definice
 * skutečné nakreslené bubliny. Jednolitá výplň oříznutá tvarem je tam od originálu
 * k nerozeznání, takže záplata nemá co zlepšit a jen riskuje artefakty. Záplata vznikla pro
 * text ležící PŘÍMO NA KRESBĚ, kde žádný obrys není (viz [buildTextPatch]).
 *
 * Že takové bublině vyjde `bgUniform = false`, není spor: prstenec vzorků se bere kolem OCR
 * boxu, a když text vyplňuje balónek skoro celý, prstenec zasáhne černý obrys. Uvnitř obrysu
 * je pak pořád jedna barva.
 */
fun patchPlan(positioned: List<PositionedTranslationBlock>): Map<Int, PatchRect> =
    positioned.withIndex()
        .filter { (_, pos) -> pos.block.needsPatch() }
        .associate { (index, pos) -> index to renderBoxRect(pos) }

private fun TranslatedBlock.needsPatch(): Boolean =
    !isSfx && !isUntranslated && !bgUniform && shape == null
