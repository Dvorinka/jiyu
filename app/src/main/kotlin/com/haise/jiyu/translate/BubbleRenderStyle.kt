package com.haise.jiyu.translate

/**
 * Čisté (bez Androidu) pomocné funkce pro grafické doladění přeložené bubliny, aby výplň
 * splynula s originální kresbou místo aby vypadala jako nalepený štítek - viz reference
 * "clean scanlation" appek. Odděleno od ReaderScreen.kt kvůli JVM testovatelnosti
 * (BubbleRenderStyleTest) bez Compose/Androidu.
 */

/**
 * "Přichytí" nasamplovanou barvu pozadí bubliny k čistě bílé/černé, když je dost blízko.
 * Vzorkování prstence kolem OCR textu (viz OcrEngine.sampleBackgroundColor) vrátí u bílé
 * bubliny typicky "skoro bílou" (třeba #F2F2F2 kvůli antialiasingu okraje písma) - vyplnit
 * tím čistě bílou bublinu nechá viditelný našedlý obdélníkový flek. Přichycením na #FFFFFF
 * (resp. #000000 u tmavých bublin) výplň zmizí do kresby. Alfa kanál zůstává zachovaný.
 *
 * Práh 0.86 / 0.14 je záměrně blízko krajům - barevné bubliny (shout boxy, system okna)
 * mají luminanci ve středu rozsahu a nesmí se přichytit, jinak by ztratily barvu.
 */
internal fun snapBubbleBg(argb: Int): Int {
    val a = (argb ushr 24) and 0xFF
    val r = (argb ushr 16) and 0xFF
    val g = (argb ushr 8) and 0xFF
    val b = argb and 0xFF
    val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
    return when {
        luminance >= 0.86 -> (a shl 24) or 0x00FFFFFF // čistě bílá, zachovej alfu
        luminance <= 0.14 -> (a shl 24)               // čistě černá, zachovej alfu
        else -> argb
    }
}

/**
 * Sjednotí velikost písmen přeloženého textu s originálem, aby lettering vypadal jako
 * v původní bublině. Komiksový/manga lettering je konvenčně VELKÝMI PÍSMENY (viz reference
 * fotky: "IT'S OBVIOUS.", "I BET THEY'LL SAY..."), proto:
 *
 *  - Latinkový originál s převahou malých písmen (výjimka - třeba mixed-case webtoon
 *    naraceace) → necháme překlad tak, jak přišel (smíšený casing).
 *  - Cokoliv jiného (verzálkový latinkový originál NEBO CJK originál, kde velikost písmen
 *    vůbec neexistuje) → verzálky, což odpovídá klasickému komiksovému/manga letteringu.
 *
 * [text] je displayText (s měkkými rozdělovníky ze slabičného dělení) - uppercase je
 * neovlivní. Kotlin String.uppercase() zvládá i českou diakritiku (ř→Ř, ž→Ž, ů→Ů...).
 */
internal fun matchOriginalCase(text: String, original: String): String {
    val latinLetters = original.filter { it in 'a'..'z' || it in 'A'..'Z' }
    if (latinLetters.length >= 3) {
        val lower = latinLetters.count { it in 'a'..'z' }
        if (lower.toFloat() / latinLetters.length > 0.5f) return text
    }
    return text.uppercase()
}
