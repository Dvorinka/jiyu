package com.haise.jiyu.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.haise.jiyu.R

/**
 * Inter (variabilní font, SIL OFL licence, https://github.com/google/fonts/tree/main/ofl/inter) -
 * jeden .ttf soubor pokrývá celou škálu vah přes FontVariation osu, není potřeba
 * separátní soubor pro Regular/Medium/SemiBold/Bold jako u dřívějšího Comic Neue.
 */
@OptIn(ExperimentalTextApi::class)
private fun interWeight(weight: Int) = Font(
    resId = R.font.inter_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val InterFontFamily = FontFamily(
    interWeight(400),
    interWeight(500),
    interWeight(600),
    interWeight(700),
    interWeight(800),
)

/**
 * Aplikuje Inter na všechny Material3 typografické styly - jelikož appka téměř
 * všude volá `Text(fontSize = ..., fontWeight = ...)` bez explicitního fontFamily,
 * Compose ho doplní sloučením s LocalTextStyle (= MaterialTheme.typography.bodyLarge),
 * takže tohle jedno místo pokryje celou appku bez nutnosti sahat na každý Text().
 */
val JiyuTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = InterFontFamily),
        displayMedium = displayMedium.copy(fontFamily = InterFontFamily),
        displaySmall = displaySmall.copy(fontFamily = InterFontFamily),
        headlineLarge = headlineLarge.copy(fontFamily = InterFontFamily),
        headlineMedium = headlineMedium.copy(fontFamily = InterFontFamily),
        headlineSmall = headlineSmall.copy(fontFamily = InterFontFamily),
        titleLarge = titleLarge.copy(fontFamily = InterFontFamily),
        titleMedium = titleMedium.copy(fontFamily = InterFontFamily),
        titleSmall = titleSmall.copy(fontFamily = InterFontFamily),
        bodyLarge = bodyLarge.copy(fontFamily = InterFontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = InterFontFamily),
        bodySmall = bodySmall.copy(fontFamily = InterFontFamily),
        labelLarge = labelLarge.copy(fontFamily = InterFontFamily),
        labelMedium = labelMedium.copy(fontFamily = InterFontFamily),
        labelSmall = labelSmall.copy(fontFamily = InterFontFamily),
    )
}
