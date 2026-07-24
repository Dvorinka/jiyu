package com.haise.jiyu.translate

/**
 * Vypočtená pozice přeloženého bloku po expanzi směrem k okolnímu volnému
 * prostoru - viz [layoutTranslationBlocks].
 *
 * [minTopF]: horní hranice vykreslovaného boxu. Rovna [topF] pro jednořádkové bloky
 * (bez expanze nahoru - dřívější pokus tohle dělal i pro jednořádkové bloky podle
 * nejbližšího souseda, což riskovalo zbytečně vysoký box bez skutečného důvodu).
 * U bloků sloučených z víc OCR řádků (viz [TranslatedBlock.lineCount]) se posune nahoru
 * o odhad výšky jednoho řádku, protože ML Kit u víceřádkového stylizovaného písma
 * občas nahlásí boundingBox kratší, než je skutečná výška bubliny (chybí horní řádek) -
 * expanze je navíc omezená nejbližším sousedem nad blokem, aby nikdy nezasáhla cizí text.
 */
data class PositionedTranslationBlock(
    val block: TranslatedBlock,
    val leftF: Float,
    val topF: Float,
    val rightF: Float,
    val maxBottomF: Float,
    val minTopF: Float = topF,
)

/**
 * OCR bounding box je většinou příliš těsný na to, aby se do něj vešel český
 * překlad (bývá delší než originál) - bez úpravy text buď přeteče přes sousední
 * bublinu (žádný limit výšky), nebo se zalomí do zbytečně mnoha úzkých řádků
 * (šířka svázaná na originál). Tahle funkce každému bloku "půjčí" volný prostor
 * kolem sebe - vodorovně symetricky kolem středu původního textu až po půlku
 * mezery k nejbližšímu sousednímu bloku ve stejné "řadě", svisle dolů až k
 * nejbližšímu bloku pod sebou - aby renderer (viz AutoFitTranslatedText v
 * ReaderScreen.kt) měl s čím pracovat při volbě šířky/velikosti písma bez
 * kolize se sousedy.
 */
fun layoutTranslationBlocks(blocks: List<TranslatedBlock>): List<PositionedTranslationBlock> {
    fun verticallyOverlaps(a: TranslatedBlock, b: TranslatedBlock) = a.topF < b.bottomF && a.bottomF > b.topF

    val positioned = blocks.map { b ->
        val leftNeighbor = blocks.filter { it !== b && verticallyOverlaps(it, b) && it.rightF <= b.leftF + 0.001f }
            .maxByOrNull { it.rightF }
        val rightNeighbor = blocks.filter { it !== b && verticallyOverlaps(it, b) && it.leftF >= b.rightF - 0.001f }
            .minByOrNull { it.leftF }

        // Bez souseda by expandLimit spadl na 0f/1f (okraj celé stránky) - box teď fyzicky
        // vyplňuje celý vypočtený prostor (viz ReaderScreen.kt .heightIn/.width), takže
        // "žádný soused = roztáhni se přes půl stránky" už není neškodné, ale viditelná chyba.
        // Strop 3x vlastní OCR rozměr dá dost místa na kompresi překladu, ne ale celou stránku.
        val ownWidth = b.rightF - b.leftF
        val expandLimitLeft = leftNeighbor?.let { (b.leftF + it.rightF) / 2f } ?: (b.leftF - ownWidth * 3f).coerceAtLeast(0f)
        val expandLimitRight = rightNeighbor?.let { (b.rightF + it.leftF) / 2f } ?: (b.rightF + ownWidth * 3f).coerceAtMost(1f)

        // Symetrická expanze kolem středu originálu - vizuálně stabilnější než nezávislé
        // roztažení každou stranou zvlášť (bublina pak "nesedí" mimo střed originálu).
        val center = (b.leftF + b.rightF) / 2f
        val halfWidth = minOf(center - expandLimitLeft, expandLimitRight - center)
            .coerceAtLeast((b.rightF - b.leftF) / 2f)
        val finalLeft = (center - halfWidth).coerceIn(0f, b.leftF)
        val finalRight = (center + halfWidth).coerceIn(b.rightF, 1f)

        fun horizontallyOverlaps(o: TranslatedBlock) = o.leftF < finalRight && o.rightF > finalLeft
        val belowNeighbor = blocks.filter { it !== b && horizontallyOverlaps(it) && it.topF >= b.bottomF - 0.001f }
            .minByOrNull { it.topF }
        // Strop odvozený z výšky JEDNOHO řádku (ne z celé výšky bloku) - u bloku sloučeného
        // z 5 OCR řádků by "3x vlastní výška" znamenalo 15 řádků volného místa, což je
        // přesně to, co způsobilo box přetékající přes zbytek stránky až za sousední SFX.
        val avgLineHeightForCap = (b.bottomF - b.topF) / b.lineCount.coerceAtLeast(1)
        val maxBottom = (belowNeighbor?.let { it.topF - 0.005f } ?: (b.bottomF + avgLineHeightForCap * 2f))
            .coerceAtLeast(b.bottomF).coerceIn(0f, 1f)

        // Jen víceřádkové bloky (viz doc komentář [PositionedTranslationBlock.minTopF]) -
        // jednořádkový box se nikdy neroztáhne nahoru, i kdyby měl nad sebou volný prostor.
        val minTop = if (b.lineCount > 1) {
            val aboveNeighbor = blocks.filter { it !== b && horizontallyOverlaps(it) && it.bottomF <= b.topF + 0.001f }
                .maxByOrNull { it.bottomF }
            val expandLimitTop = aboveNeighbor?.let { (b.topF + it.bottomF) / 2f } ?: 0f
            val avgLineHeight = (b.bottomF - b.topF) / b.lineCount
            (b.topF - avgLineHeight * 0.6f).coerceAtLeast(expandLimitTop).coerceIn(0f, b.topF)
        } else {
            b.topF
        }

        PositionedTranslationBlock(
            block = b,
            leftF = finalLeft,
            topF = b.topF,
            rightF = finalRight,
            maxBottomF = maxBottom,
            minTopF = minTop,
        )
    }.toMutableList()

    // Řádková heuristika výše nezachytí diagonálně sousedící bloky (jeden začíná výš,
    // ale je posunutý vpravo mimo "stejnou řadu") - po prvotní expanzi ještě jednou
    // projdeme všechny dvojice a případný přesah zmenšíme, přednostně svisle (zkrácením
    // maxBottomF horního bloku), a teprve když by to zmenšilo box pod jeho původní OCR
    // rozměr, vodorovně (posunutím sdílené hranice na střed přesahu).
    repeat(2) {
        for (i in positioned.indices) {
            for (j in positioned.indices) {
                if (i == j) continue
                val a = positioned[i]; val b = positioned[j]
                val overlapX = minOf(a.rightF, b.rightF) - maxOf(a.leftF, b.leftF)
                val overlapY = minOf(a.maxBottomF, b.maxBottomF) - maxOf(a.minTopF, b.minTopF)
                if (overlapX <= 0f || overlapY <= 0f) continue

                val upperIdx = if (a.minTopF <= b.minTopF) i else j
                val lowerIdx = if (a.minTopF <= b.minTopF) j else i
                val upper = positioned[upperIdx]
                val lower = positioned[lowerIdx]
                val shrunkBottom = lower.minTopF - 0.003f

                if (shrunkBottom >= upper.block.bottomF && shrunkBottom < upper.maxBottomF) {
                    positioned[upperIdx] = upper.copy(maxBottomF = shrunkBottom)
                } else {
                    val leftIdx = if (a.leftF <= b.leftF) i else j
                    val rightIdx = if (a.leftF <= b.leftF) j else i
                    val leftB = positioned[leftIdx]
                    val rightB = positioned[rightIdx]
                    val split = (leftB.rightF + rightB.leftF) / 2f
                    positioned[leftIdx] = leftB.copy(rightF = split.coerceAtLeast(leftB.block.rightF))
                    positioned[rightIdx] = rightB.copy(leftF = split.coerceAtMost(rightB.block.leftF))
                }
            }
        }
    }

    return positioned
}
