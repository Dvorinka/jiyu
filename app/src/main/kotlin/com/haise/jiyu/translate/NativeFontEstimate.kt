package com.haise.jiyu.translate

/**
 * Odhadne, jak velké písmo mělo měřené místo v ORIGINÁLU, z výšky jeho OCR boxu.
 *
 * ## Co se dělo
 * Velikost písma překladu je zastropovaná velikostí písma originálu (viz [fitTextToShape],
 * parametr `preferredFontSp`) - to je správně, výsledek má vypadat jako původní lettering.
 * Jenže ta velikost se odvozovala tak, že se výška OCR boxu vydělila 1.25, jako by šlo o
 * řádkovou rozteč. OCR box ale obepíná jen samotná písmena, ne celý řádek i s mezerami.
 *
 * Následek: odhad vycházel zhruba na 0,62 násobku skutečné velikosti, takže text zůstával
 * malý i v obří bublině a nemohl povyrůst, protože strop mu to nedovolil.
 *
 * ## Změřené poměry (ne odhad z typografie)
 * Viz `NativeFontSizeOnDeviceTest` - vykreslí text o známé velikosti a porovná s tím, co
 * vrátí ML Kit:
 * ```
 *   verzálky           40px -> box 29px   (0.725)
 *   verzálky           60px -> box 44px   (0.733)
 *   smíšené s dotahy   60px -> box 63px   (1.050)
 * ```
 * Jedna konstanta stačit nemůže: u verzálek box odpovídá výšce velkého písmene, u smíšeného
 * textu sahá od horního dotahu k dolnímu. Rozlišuje se proto podle originálu, který appka zná.
 *
 * Text bez malých písmen (verzálky, číslice, CJK) spadá do první kategorie - u japonštiny a
 * korejštiny žádná velikostní varianta písmen neexistuje a box odpovídá celé výšce znaku.
 *
 * Nadhodnotit odhad je bezpečnější než podhodnotit: je to jen STROP, a když se text při téhle
 * velikosti do bubliny nevejde, sazba ho stejně zmenší.
 */
internal fun estimateNativeFontPx(boxHeightPx: Float, originalText: String): Float {
    val ratio = if (originalText.any { it.isLowerCase() }) {
        MIXED_CASE_BOX_TO_FONT
    } else {
        ALL_CAPS_BOX_TO_FONT
    }
    return (boxHeightPx.coerceAtLeast(MIN_BOX_HEIGHT_PX)) / ratio
}

/** Verzálky: OCR box odpovídá výšce velkého písmene. Průměr změřených 0.725 a 0.733. */
private const val ALL_CAPS_BOX_TO_FONT = 0.73f

/** Smíšený text: box sahá od horního dotahu k dolnímu, tedy přes celou výšku písma i víc. */
private const val MIXED_CASE_BOX_TO_FONT = 1.05f

/** Pojistka proti nulové/záporné výšce z pokaženého OCR boxu - odhad nikdy nesmí vyjít nula. */
private const val MIN_BOX_HEIGHT_PX = 1f
