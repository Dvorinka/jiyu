package com.haise.jiyu.ui.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haise.jiyu.R
import com.haise.jiyu.translate.BubbleShapePoint
import com.haise.jiyu.translate.BubbleType
import com.haise.jiyu.translate.LineMetrics
import com.haise.jiyu.translate.PositionedTranslationBlock
import com.haise.jiyu.translate.TextMeasurement
import com.haise.jiyu.translate.TranslatedBlock
import com.haise.jiyu.translate.averageArgb
import com.haise.jiyu.translate.fitFontSizeToBox
import com.haise.jiyu.translate.largestInscribedRect
import com.haise.jiyu.translate.layoutTranslationBlocks
import com.haise.jiyu.translate.matchOriginalCase
import com.haise.jiyu.translate.snapBubbleBg

// ── Translation overlay - sdíleno mezi MangaReader (ReaderPager.kt) a WebtoonReader.kt ──
//
// Dřív měl WebtoonPage vlastní, skoro řádek-po-řádku duplicitní kopii týhle logiky (jiná
// souřadnicová soustava - měřený `size: IntSize` místo `imageRect: Rect` - ale stejný bleed/
// clip-shape/snap-bg/AutoFitTranslatedText postup). Riziko: oprava (a na tomhle kódu se ladí
// často, viz docs/superpowers/specs/2026-07-24-bubble-shape-and-font-design.md) by se snadno
// aplikovala jen na jednu kopii. Teď obě cesty volají [TranslationOverlay]/[BubbleOverlayLayer].

/** Malý přesah kolem přeloženého boxu, aby nikde neprosvítal kousek originálu za okrajem OCR boxu. */
private val TRANSLATION_BOX_BLEED = 2.dp

/**
 * Plně neprůhledné - jakákoli průhlednost nechá prosvítat "ducha" originálu pod výplní
 * (viz zpětná vazba uživatele: i 2 % průhlednosti dělalo viditelný šedý závoj se stínem
 * původního textu). Reference (clean scanlation appky) mají výplň 100% krycí.
 */
private const val TRANSLATION_BOX_ALPHA = 1.0f

/** Horizontální padding uvnitř přeloženého boxu - sdíleno mezi voláním `.padding(horizontal = ...)` a [AutoFitTranslatedText], aby fitter měřil text proti stejné šířce, jakou Text ve skutečnosti dostane. */
private val TRANSLATION_TEXT_HORIZONTAL_PADDING = 4.dp

/**
 * Jak velký podíl vepsaného obdélníku (viz [largestInscribedRect]) se skutečně použije na text.
 * Obrys bubliny bývá nakreslený znatelně tlustou linkou a text nalepený těsně na ni vypadá
 * špatně, i když technicky nepřetéká - tenhle odstup dělá výsledek vizuálně podobný tomu, jak
 * sází text skutečný lettering v originále.
 */
private const val INSCRIBED_TEXT_AREA_FACTOR = 0.92f

/**
 * Skutečně vykreslený obdélník obrázku uvnitř Boxu dané velikosti, podle stejné logiky,
 * jakou používá Coil/Compose Image k vykreslení Painteru (viz [ContentScale.computeScaleFactor]
 * + výchozí [Alignment.Center]). Bez tohohle by se OCR frakce (0..1, vztažené ke skutečným
 * pixelům staženého obrázku) mapovaly na `containerSize` Boxu - ten je ale typicky
 * `fillMaxSize()` přes celou obrazovku, zatímco obrázek (kromě ContentScale.FillBounds/Crop)
 * uvnitř něj sedí menší a vycentrovaný (letterbox mezery nahoře/dole nebo po stranách) -
 * bubliny by pak driftovaly tím víc, čím dál od středu stránky, přesně jak hlásil uživatel
 * (překlady mimo originální bubliny) - viz docs/superpowers/specs/2026-07-24-bubble-shape-and-font-design.md.
 *
 * Vrácené hodnoty jsou v "Dp-value" jednotkách (Float bez skutečné density konverze) -
 * [ContentScale.computeScaleFactor] pracuje jen s poměry stran, takže smíchání pixelů
 * (intrinsicSizePx) a Dp hodnot (containerSizeDp) je bezpečné, dokud se použije konzistentně.
 */
fun imageDisplayRect(intrinsicSizePx: Size, containerSizeDp: Size, contentScale: ContentScale): Rect {
    if (intrinsicSizePx.width <= 0f || intrinsicSizePx.height <= 0f ||
        containerSizeDp.width <= 0f || containerSizeDp.height <= 0f
    ) {
        return Rect(Offset.Zero, containerSizeDp)
    }
    val scaleFactor = contentScale.computeScaleFactor(intrinsicSizePx, containerSizeDp)
    val scaledWidth = intrinsicSizePx.width * scaleFactor.scaleX
    val scaledHeight = intrinsicSizePx.height * scaleFactor.scaleY
    val offsetX = (containerSizeDp.width - scaledWidth) / 2f
    val offsetY = (containerSizeDp.height - scaledHeight) / 2f
    return Rect(Offset(offsetX, offsetY), Size(scaledWidth, scaledHeight))
}

/**
 * Vrstva všech (ne-SFX) přeložených bublin jedné stránky - jediné místo, odkud se volá
 * [TranslationOverlay], ať pro manga (MangaReader v ReaderPager.kt) nebo webtoon
 * (WebtoonPage ve WebtoonReader.kt) mód.
 *
 * @param pageIndex potřeba jen pro sestavení klíče "$pageIndex:$bubbleIndex" v [flippedBubbles]
 *   (viz ReaderViewModel.toggleBubbleFlip) - bubbleIndex je pozice bubliny v [positioned], ne
 *   v původním (nefiltrovaném) `blocks`.
 */
@Composable
fun BubbleOverlayLayer(
    blocks: List<TranslatedBlock>,
    imageRect: Rect,
    textScale: Float = 1f,
    pageIndex: Int = -1,
    flippedBubbles: Set<String> = emptySet(),
    onToggleFlip: (pageIndex: Int, bubbleIndex: Int) -> Unit = { _, _ -> },
) {
    val positioned = remember(blocks) { layoutTranslationBlocks(blocks) }
    positioned.forEachIndexed { bubbleIndex, pos ->
        // isUntranslated = model vrátil UNTRANSLATED_MARKER (nečitelné OCR) - stejně jako u
        // SFX bublin appka radši nic nekreslí a nechá prosvítat originál, než aby ukázala
        // doslovný anglický placeholder tam, kde měl být český text (viz TranslateRepository).
        if (!pos.block.isSfx && !pos.block.isUntranslated) {
            TranslationOverlay(
                pos = pos,
                imageRect = imageRect,
                textScale = textScale,
                isFlipped = "$pageIndex:$bubbleIndex" in flippedBubbles,
                onTap = { onToggleFlip(pageIndex, bubbleIndex) },
            )
        }
    }
}

/**
 * Compose Shape, co kopíruje skutečný obrys bubliny z [BubbleShapePoint] seznamu místo
 * pevného zaobleného obdélníku. Body jsou v normalizovaných (0..1) souřadnicích stránky -
 * shapeTopF/shapeBottomF/leftMinF/rightMaxF (= PositionedTranslationBlock.minTopF/maxBottomF/
 * leftF/rightF pro shape-based blok, viz TranslationLayout.kt) je přemapují na velikost
 * skutečně vykresleného boxu.
 */
private class BubbleClipShape(
    private val points: List<BubbleShapePoint>,
    private val shapeTopF: Float,
    private val shapeBottomF: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        if (points.size < 2 || shapeBottomF <= shapeTopF) {
            return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        }
        val yRange = shapeBottomF - shapeTopF
        val leftMinF = points.minOf { it.leftF }
        val rightMaxF = points.maxOf { it.rightF }
        val spanF = (rightMaxF - leftMinF).coerceAtLeast(0.0001f)

        fun py(p: BubbleShapePoint) = ((p.yF - shapeTopF) / yRange) * size.height
        fun pxLeft(p: BubbleShapePoint) = ((p.leftF - leftMinF) / spanF) * size.width
        fun pxRight(p: BubbleShapePoint) = ((p.rightF - leftMinF) / spanF) * size.width

        val path = Path()
        path.moveTo(pxLeft(points.first()), py(points.first()))
        points.forEach { path.lineTo(pxLeft(it), py(it)) }
        points.asReversed().forEach { path.lineTo(pxRight(it), py(it)) }
        path.close()
        return Outline.Generic(path)
    }
}

@Composable
fun TranslationOverlay(
    pos: PositionedTranslationBlock,
    imageRect: Rect,
    textScale: Float = 1f,
    isFlipped: Boolean = false,
    onTap: () -> Unit = {},
) {
    // OCR bounding box je v zásadě vždy leftF<=rightF/topF<=bottomF, ale nejde o zaručený
    // invariant (různé OCR modely, rotace/mirror snímků atd.) - záporná šířka/výška předaná
    // do Modifier.width()/height() spadne na IllegalArgumentException přímo v Compose layout
    // fázi, mimo dosah jakéhokoliv try/catch kolem překladu, a appka tvrdě spadne.
    //
    // Frakce (leftF/topF/...) se mapují na imageRect (skutečně vykreslený obrázek), ne na
    // celý Box - viz [imageDisplayRect].
    //
    // Bubliny s detekovaným tvarem (flood-fill zná přesně vnitřek po vnitřní hranu černého
    // obrysu) NErozšiřujeme o bleed - jinak výplň přejede přes černý obrys bubliny a ten
    // zmizí. Bez tvaru (heuristický obdélník) bleed zůstává, protože tam OCR box bývá o chlup
    // těsnější než text a bez přesahu by po stranách prosvítal originál.
    val bleed = if (pos.block.shape != null) 0.dp else TRANSLATION_BOX_BLEED
    val left = (imageRect.left + imageRect.width * pos.leftF).dp - bleed
    val top  = (imageRect.top + imageRect.height * pos.minTopF).dp - bleed
    val w    = (imageRect.width * (pos.rightF - pos.leftF)).dp.coerceAtLeast(0.dp) + bleed * 2
    // maxBottomF je HORNÍ LIMIT růstu (může sahat až k dalšímu prvku na stránce, klidně přes
    // spoustu prázdného pozadí) - použít ho jako MINIMUM by box nutilo vyplnit i prázdný
    // prostor, kde žádný originál nebyl. Skutečné minimum je vlastní OCR rozsah bubliny
    // (block.bottomF, ne maxBottomF) - to jediné je potřeba zakrýt, aby nikde neprosvítal originál.
    val effectiveMinBottomF = pos.block.shape?.let { pos.maxBottomF } ?: pos.block.bottomF
    val minH = (imageRect.height * (effectiveMinBottomF - pos.minTopF)).dp.coerceAtLeast(0.dp) + bleed * 2
    val maxH = (imageRect.height * (pos.maxBottomF - pos.minTopF)).dp.coerceAtLeast(0.dp) + bleed * 2
    val clipShape = pos.block.shape?.let { BubbleClipShape(it, pos.minTopF, pos.maxBottomF) } ?: RoundedCornerShape(3.dp)
    // Svislý gradient (horní/dolní polovina vzorkovaného prstence, viz OcrEngine.sampleBackgroundColor)
    // místo jednolité barvy - obě strany se "přichytí" na bílou/černou nezávisle (snapBubbleBg),
    // takže obyčejné bubliny zůstávají plnou barvou stejně jako dřív, gradient se projeví jen
    // u barevných/stínovaných bublin, kde má reálný podklad.
    val snappedBgTop = snapBubbleBg(pos.block.bgColorArgb)
    val snappedBgBottom = snapBubbleBg(pos.block.bgColorBottomArgb)
    val displayText = matchOriginalCase(pos.block.displayText, pos.block.originalText)

    // Bezpečná plocha pro text uvnitř tvaru bubliny (viz [largestInscribedRect]) - největší
    // obdélník, který se celý vejde dovnitř obrysu. Text se sází do NĚJ, ne do celého
    // ohraničujícího obdélníku tvaru: díky tomu nemůže zasáhnout obrys ani v užších místech
    // (dvojkruhová bublina, zvlněný okraj) a nepotřebuje k tomu žádné vykreslování řádek po
    // řádku, které dřív působilo překrývající se řádky (viz uživatelská zpětná vazba).
    // Padding uvnitř plochy - obrys bubliny bývá nakreslený "tlustou" linkou a text nalepený
    // těsně na ni vypadá špatně i když technicky nepřetéká.
    val inscribed = pos.block.shape?.let { largestInscribedRect(it) }
    val textAreaWidth = inscribed
        ?.let { (imageRect.width * it.widthF * INSCRIBED_TEXT_AREA_FACTOR).dp }
        ?: w
    val textAreaHeight = inscribed
        ?.let { (imageRect.height * it.heightF * INSCRIBED_TEXT_AREA_FACTOR).dp }
        ?: maxH
    // Vepsaný obdélník nemusí být uprostřed bubliny (u složeného tvaru bývá posunutý k té
    // prostornější části) - text se musí posunout s ním, jinak by se vysázel doprostřed
    // celého tvaru, tedy mimo tu bezpečnou plochu.
    val textOffsetX = inscribed?.let {
        val boxCenterF = (pos.leftF + pos.rightF) / 2f
        val rectCenterF = (it.leftF + it.rightF) / 2f
        (imageRect.width * (rectCenterF - boxCenterF)).dp
    } ?: 0.dp
    val textOffsetY = inscribed?.let {
        val boxCenterF = (pos.minTopF + pos.maxBottomF) / 2f
        val rectCenterF = (it.topF + it.bottomF) / 2f
        (imageRect.height * (rectCenterF - boxCenterF)).dp
    } ?: 0.dp

    // Entrance animace - MutableTransitionState začíná na false a rovnou cílí na true, takže
    // AnimatedVisibility přehraje "enter" přesně jednou při prvním composnutí týhle bubliny
    // (např. když se stránka přeloží nebo se do ní scrollne/naviguje zpět) a pak už zůstává
    // viditelná, žádné "exit" se nikdy nespustí.
    val entranceState = remember {
        MutableTransitionState(false).apply { targetState = true }
    }

    AnimatedVisibility(
        visibleState = entranceState,
        enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.92f, animationSpec = tween(200)),
    ) {
        Box(
            modifier = Modifier
                .offset(x = left, y = top)
                // Pevná šířka + heightIn(min = minH, max = maxH): box musí vždy zakrýt aspoň
                // vlastní OCR rozsah bubliny (jinak prosvítá originál), smí růst výš k maxH, jen
                // když to text opravdu potřebuje (ne nutit box vyplnit celý, klidně prázdný,
                // prostor až k dalšímu prvku na stránce) - ale NIKDY přes maxH. Bez horního
                // stropu tu neexistovala žádná pojistka, kdyby fitter někdy zvolil o chlup
                // moc velké písmo (zaokrouhlení/rozdíl mezi měřením a skutečným vykreslením) -
                // box se pak fyzicky natáhl do sousedního panelu s kresbou (viz uživatelská
                // zpětná vazba - "AHA, PÁNI."/"HORSKÉ BESTIE..." přetékající do obrázku pod
                // bublinou). Krajní řádek se teď nanejvýš neúhledně ořízne, ale nikdy nezasáhne
                // cizí kresbu.
                .width(w)
                .heightIn(min = minH, max = maxH)
                .clip(clipShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(snappedBgTop).copy(alpha = TRANSLATION_BOX_ALPHA),
                            Color(snappedBgBottom).copy(alpha = TRANSLATION_BOX_ALPHA),
                        ),
                    ),
                )
                // Tap = "flip" na originál (viz ReaderViewModel.toggleBubbleFlip). Konzumuje tap
                // dřív, než se dostane k page-level gestům (tap-zóny/double-tap zoom/long-press
                // sdílení v MangaReaderu) - vědomý kompromis, přesně nad bublinou chceme flip,
                // ne zoom/navigaci.
                .pointerInput(onTap) { detectTapGestures(onTap = { onTap() }) }
                .padding(horizontal = TRANSLATION_TEXT_HORIZONTAL_PADDING, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = isFlipped,
                transitionSpec = {
                    (scaleIn(initialScale = 0.85f) + fadeIn())
                        .togetherWith(scaleOut(targetScale = 0.85f) + fadeOut())
                },
                label = "bubble-flip",
            ) { flipped ->
                AutoFitTranslatedText(
                    text = if (flipped) pos.block.originalText else displayText,
                    // Volba barvy textu (podle jasu) potřebuje JEDNU barvu, ne gradient -
                    // průměr obou stran je dost přesný odhad pro čitelnost přes celou bublinu.
                    bgColorArgb = averageArgb(snappedBgTop, snappedBgBottom),
                    boxWidth = textAreaWidth,
                    maxHeight = textAreaHeight,
                    textScale = textScale,
                    bubbleType = pos.block.bubbleType,
                    offsetX = textOffsetX,
                    offsetY = textOffsetY,
                )
            }
        }
    }
}

/**
 * Comic Neue - komiksové písmo s plnou podporou české diakritiky (ř,ž,č,š,ě,ň,ť,ů...), ne
 * systémový font, který v malé bublině vypadá jako titulky, ne jako lettering. Různé řezy
 * podle typu bubliny (viz BubbleType/fontFamilyFor) místo jednoho univerzálního - skutečná
 * vizuální analýza stylu písma z nízkorozlišeného OCR výřezu by byla nespolehlivá (viz spec
 * docs/superpowers/specs/2026-07-24-bubble-shape-and-font-design.md), tohle je praktičtější
 * přiblížení "co nejpodobnějšího originálu" fontu.
 */
private val ComicNeueRegular = FontFamily(Font(R.font.comic_neue_regular, FontWeight.Normal))
private val ComicNeueBold = FontFamily(Font(R.font.comic_neue_bold, FontWeight.Bold))
private val ComicNeueItalic = FontFamily(Font(R.font.comic_neue_italic, FontWeight.Normal, FontStyle.Italic))
private val ComicNeueBoldItalic = FontFamily(Font(R.font.comic_neue_bold_italic, FontWeight.Bold, FontStyle.Italic))

private fun fontFamilyFor(bubbleType: BubbleType): FontFamily = when (bubbleType) {
    BubbleType.SHOUT -> ComicNeueBold
    BubbleType.THOUGHT, BubbleType.WHISPER -> ComicNeueItalic
    BubbleType.SPEECH, BubbleType.NARRATION, BubbleType.SYSTEM, BubbleType.SFX -> ComicNeueRegular
}

/**
 * Přeložený text (čeština) bývá delší než originál (JP/KR/EN) - bez úpravy velikosti písma
 * by buď přetekl přes sousední bublinu, nebo by ho Compose tvrdě uřízl. Tady najdeme
 * největší velikost, která se ještě vejde do zadané plochy (viz [fitFontSizeToBox]).
 *
 * Plocha ([boxWidth] x [maxHeight]) je u bublin se skutečným tvarem největší obdélník vepsaný
 * DOVNITŘ obrysu (viz [largestInscribedRect] a volající) - text tak fyzicky nemůže zasáhnout
 * obrys, i kdyby byla bublina jakkoli nepravidelná, a nepotřebuje k tomu žádné vykreslování
 * po jednotlivých řádcích (ten dřívější přístup působil překrývající se řádky).
 *
 * Text se vždy sází jako JEDEN normální blok - řádkování tak řeší Compose, ne vlastní
 * dopočítávání pozic.
 */
@Composable
private fun AutoFitTranslatedText(
    text: String,
    bgColorArgb: Int,
    boxWidth: androidx.compose.ui.unit.Dp,
    maxHeight: androidx.compose.ui.unit.Dp,
    textScale: Float,
    bubbleType: BubbleType,
    offsetX: androidx.compose.ui.unit.Dp = 0.dp,
    offsetY: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val fontFamily = fontFamilyFor(bubbleType)
    val maxFontSp = 36f * textScale
    val minFontSp = 6f * textScale
    // boxWidth je šířka VNĚJŠÍHO Boxu (viz volající) - Text uvnitř má reálně k dispozici
    // o horizontal padding Boxu (4.dp na každé straně) míň. Bez týhle korekce fitter vybíral
    // velikost písma, která se vejde do PLNÉ šířky boxu, ale skutečný Text dostal od Compose
    // užší constraint - řádek, co se těsně vešel do měření, se pak u reálného vykreslení
    // zalomil jinam a poslední slovo bylo uříznuté o okraj bubliny.
    val widthPx = (with(density) { boxWidth.roundToPx() } - with(density) { (TRANSLATION_TEXT_HORIZONTAL_PADDING * 2).roundToPx() }).coerceAtLeast(1)
    val maxHeightPx = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)

    val fitResult = remember(text, widthPx, maxHeightPx, maxFontSp, fontFamily) {
        fitFontSizeToBox(
            minFontSp = minFontSp,
            maxFontSp = maxFontSp,
            boxWidthPx = widthPx.toFloat(),
            maxHeightPx = maxHeightPx.toFloat(),
            measure = { fontSp, maxWidthPx ->
                // Rezerva na obrys (viz StrokedTranslatedText/STROKE_WIDTH_FACTOR) - obrys se
                // kreslí kolem stejného textu ve stejné velikosti, takže vizuálně "vykousne"
                // trochu místa navíc kolem glyphů. Bez rezervy by fitter vybral velikost, co
                // se vejde jen do samotné výplně (Fill), a obrys by pak u okrajů bubliny přetekl.
                val strokeReservePx = with(density) { maxOf(2.dp.toPx(), fontSp.sp.toPx() * STROKE_WIDTH_FACTOR) }
                val style = TextStyle(fontSize = fontSp.sp, lineHeight = (fontSp * 1.25f).sp, fontFamily = fontFamily)
                val constraintWidth = (maxWidthPx - strokeReservePx).toInt().coerceAtLeast(1)
                val measured = textMeasurer.measure(text = text, style = style, constraints = Constraints(maxWidth = constraintWidth))
                val lines = (0 until measured.lineCount).map { i ->
                    LineMetrics(
                        widthPx = measured.getLineRight(i) - measured.getLineLeft(i),
                        topPx = measured.getLineTop(i),
                        bottomPx = measured.getLineBottom(i),
                    )
                }
                // Nejdelší JEDNOTLIVÉ slovo měřené BEZ šířkového omezení - jinak by ho Compose
                // sám zalomil a naměřená šířka by byla vždycky menší než limit, takže by
                // kontrola v fitFontSizeToBox nikdy nic nezachytila. Tohle je jediná obrana
                // proti tomu, aby se slovo rozsekalo uprostřed po písmenech ("KDYBYCH" ->
                // "KDYB"/"YCH", viz uživatelská zpětná vazba).
                val longestWordWidthPx = text.split(' ', '\n')
                    .filter { it.isNotBlank() }
                    .maxOfOrNull { word ->
                        textMeasurer.measure(text = word, style = style, softWrap = false).size.width.toFloat()
                    } ?: 0f
                TextMeasurement(
                    totalHeightPx = measured.size.height + strokeReservePx,
                    lines = lines,
                    longestWordWidthPx = longestWordWidthPx + strokeReservePx,
                )
            },
        )
    }

    // Vzorkovaná barva pozadí bubliny (viz TranslatedBlock.bgColorArgb) může být i tmavá
    // (stínovaný/černý shout box) - černý text na černém pozadí by byl nečitelný, proto
    // volíme barvu textu (a opačnou barvu obrysu) podle jasu (luminance) pozadí, ne napevno.
    val bg = Color(bgColorArgb)
    val luminance = 0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue
    val textColor = if (luminance < 0.5f) Color.White else Color.Black
    val strokeColor = if (luminance < 0.5f) Color.Black else Color.White

    Box(
        modifier = Modifier.width(boxWidth).offset(x = offsetX, y = offsetY),
        contentAlignment = Alignment.Center,
    ) {
        StrokedTranslatedText(
            text = text,
            fontSp = fitResult.fontSp,
            fontFamily = fontFamily,
            textColor = textColor,
            strokeColor = strokeColor,
        )
    }
}

/** Podíl velikosti písma použitý jako šířka obrysu (viz [StrokedTranslatedText]), s dolní hranicí 2.dp pro malá písmena, kde by procentuální obrys byl neviditelně tenký. */
private const val STROKE_WIDTH_FACTOR = 0.12f

/**
 * Vykreslí text DVAKRÁT přes sebe - nejdřív obrysovou vrstvu (opačná barva než výplň podle
 * jasu pozadí), pak výplň navrch - pro čitelnost přes komplexní/vzorované pozadí bubliny.
 * [TextStyle.drawStyle] umí jen JEDEN styl na jedno volání Text (buď Fill, nebo Stroke), takže
 * obrys+výplň v jednom Text nejde - dvě překrývající se Text vrstvy jsou jednodušší a
 * spolehlivější než snaha o "vlastní" kreslení přes Canvas/drawWithContent.
 */
@Composable
private fun StrokedTranslatedText(
    text: String,
    fontSp: Float,
    fontFamily: FontFamily,
    textColor: Color,
    strokeColor: Color,
) {
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { maxOf(2.dp.toPx(), fontSp.sp.toPx() * STROKE_WIDTH_FACTOR) }

    Box(contentAlignment = Alignment.Center) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            style = TextStyle(
                color = strokeColor,
                fontSize = fontSp.sp,
                lineHeight = (fontSp * 1.25f).sp,
                fontFamily = fontFamily,
                drawStyle = Stroke(width = strokeWidthPx, join = StrokeJoin.Round),
            ),
        )
        Text(
            text = text,
            color = textColor,
            fontSize = fontSp.sp,
            lineHeight = (fontSp * 1.25f).sp,
            fontFamily = fontFamily,
            // Každý řádek vlastní vycentrovaný (ne jen blok jako celek) - víceřádkový text
            // v bublině je jinak zarovnaný doleva a krajní řádky lepí/přetékají oblý okraj
            // bubliny (viz "K VEČEŘI..." uříznuté "K"). Centrování per-řádek odpovídá
            // klasickému komiksovému letteringu.
            textAlign = TextAlign.Center,
        )
    }
}
