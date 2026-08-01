package com.haise.jiyu.ui.reader

import com.haise.jiyu.translate.TextPatchProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Přístup k [TextPatchProvider] přímo z composable.
 *
 * `BubbleOverlayLayer` volají dvě různé čtečky (MangaReader v ReaderPager.kt a WebtoonPage ve
 * WebtoonReader.kt) a ani jedna žádný ViewModel nedostává. Protahovat provider parametrem přes
 * celý strom by znamenalo změnit podpisy několika composable jen kvůli téhle jedné závislosti,
 * takže se bere přes EntryPoint z aplikace.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TextPatchEntryPoint {
    fun textPatchProvider(): TextPatchProvider
}
