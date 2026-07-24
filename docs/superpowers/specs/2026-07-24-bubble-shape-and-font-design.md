# Detekce tvaru bubliny + font podle stylu

Datum: 2026-07-24
Stav: schváleno uživatelem, jde se do implementace

## Problém

Přeložený box v čtečce (viz `TranslationLayout.kt` + `ReaderScreen.kt`) je odvozený
čistě z OCR bounding boxu textu a heuristicky rozšířený směrem k nejbližším sousedním
blokům (`layoutTranslationBlocks`). Na reálných stránkách to selhává dvěma způsoby:

1. Když poblíž není žádný jiný blok, co by expanzi omezil, box se roztáhne skoro přes
   celou šířku/výšku stránky bez ohledu na to, kde skutečně bublina končí - box pak
   překrývá kus kresby, co s bublinou vůbec nesouvisí.
2. Tvar boxu je vždy obdélník se zaoblenými rohy (`RoundedCornerShape(3.dp)`), i když
   skutečná bublina má "ocas", oválný nebo mrakovitý obrys - výsledek nevypadá jako
   bublina komiksu.

Font je navíc napevno jeden (`ComicNeueFamily`, Regular/Bold podle toho, jestli Compose
`Text` dostane `FontWeight.Bold` - ve skutečnosti se dnes vždy používá jen Normal váha,
viz `AutoFitTranslatedText`), bez ohledu na typ bubliny (`BubbleClassifier.BubbleType`).

## Cíl

- Box, který appka vykreslí přes originální text, ať **vizuálně kopíruje skutečný obrys
  bubliny** (ne odhad z OCR + heuristika), a nikdy nepřesahuje za její hranice.
- Písmo ať se stylem přibližuje typu bubliny (křik = tučné, myšlenka/šepot = kurzíva),
  ne jeden univerzální řez pro všechno.

## Mimo rozsah

- Oprava `"[UNTRANSLATED]"` bublin (selhání překladu na straně Gemini/promptu) - to je
  samostatný problém kvality překladu, neřeší se tady.
- Skutečné vizuální rozpoznání konkrétního fontu z obrázku (analýza tloušťky
  tahu/sklonu písma) - nespolehlivé na nízkém rozlišení OCR výřezu, nahrazeno mapováním
  podle `BubbleType`, který už `BubbleClassifier` počítá.
- Zalomení textu podle obrysu bubliny (text by "obtékal" tvar) - text zůstává jednoduchý
  auto-fit blok uvnitř ohraničujícího obdélníku tvaru; jen POZADÍ boxu (barva/tvar) kopíruje
  bublinu.

## Architektura

### 1. `BubbleShapeDetector` (nový soubor `translate/BubbleShapeDetector.kt`)

Čistě funkční, testovatelná bez Androidu/Robolectric - pracuje nad abstrakcí pixelů,
ne nad `android.graphics.Bitmap` přímo:

```kotlin
fun interface PixelSource {
    /** ARGB pixel na (x, y); mimo hranice smí vrátit cokoliv, volající si hranice hlídá sám. */
    fun colorAt(x: Int, y: Int): Int
}

data class BubbleShapePoint(val yF: Float, val leftF: Float, val rightF: Float)

/** null = detekce selhala/vypadá nedůvěryhodně - volající použije starý heuristický obdélník. */
fun detectShape(
    source: PixelSource,
    width: Int,
    height: Int,
    seeds: List<Pair<Int, Int>>,    // pixelové souřadnice, viz "Seed body" níže
    bgColorArgb: Int,
    colorDistanceThreshold: Int = 40, // eukleidovská vzdálenost v RGB (0..441), viz níže
    maxAreaFraction: Float = 0.25f,   // pojistka: flood-fill nesmí zabrat > 25 % celé stránky
): List<BubbleShapePoint>?
```

**Seed body:** NE střed OCR textového boxu (často padne přímo na tmavý pixel písma,
ne na pozadí). Místo toho znovu použít stejné body prstence, které už počítá
`sampleBackgroundColor` v `OcrEngine.kt` (perimetr pár pixelů kolem OCR boxu) - jsou to
zaručeně body na pozadí bubliny, ne na textu, takže je bezpečné z nich rovnou startovat
BFS. Pokud i tak žádný seed bod neprojde prahovým testem (např. barva pozadí byla
nasamplována nespolehlivě), `detectShape` rovnou vrátí `null`.

**Práh barvy:** eukleidovská vzdálenost `sqrt(dR² + dG² + dB²)` mezi `colorAt(x,y)` a
`bgColorArgb`, práh `40` (z max. možných `441`) jako výchozí konzervativní hodnota -
dost citlivé na zachycení tmavého obrysu bubliny (typicky černá kontura, vzdálenost od
světlého pozadí bývá > 150), dost tolerantní na jemný gradient/antialiasing uvnitř
bubliny. Hodnota je parametr funkce, laditelná bez API změny.

Algoritmus: BFS (frontou, ne rekurzí - kvůli velkým bublinám a Kotlin/JVM stack limitu)
ze seed bodů, sousední pixel se přidá do fronty, pokud jeho barva projde prahovým testem
výše. Pro každý navštívený řádek `y` se průběžně eviduje min/max navštívený sloupec.

Po doběhnutí BFS:
- Pokud navštívená plocha > `maxAreaFraction` z celé stránky → **fail (null)**, něco uteklo
  mimo skutečnou bublinu (typicky text přímo na kresbě/SFX bez uzavřeného pozadí).
- Jinak z per-řádkových min/max sloupců vybereme **24 rovnoměrně rozložených vzorků**
  (od nejvyššího po nejnižší navštívený řádek) → `List<BubbleShapePoint>` (24 bodů,
  normalizované 0..1 souřadnice jako zbytek kódu `leftF`/`topF`).

`BitmapPixelSource` (malý adaptér v `OcrEngine.kt`) obalí `android.graphics.Bitmap`
do `PixelSource` pro reálné použití; v testech se použije prostý `IntArray`-backed fake.

### 2. `OcrEngine.kt` - napojení

V `recognize()`, hned po `mergeNearbyLines(...).map { it.copy(bgColorArgb = ...) }`,
přidat další `.map` krok, který zavolá `detectShape(...)` se seed body odvozenými stejným
způsobem jako `sampleBackgroundColor` (viz výše) a už nasamplovanou `bgColorArgb`.
Výsledek (nullable) se uloží do nového pole na `RawTextBlock`:

```kotlin
data class RawTextBlock(
    ...
    val shape: List<BubbleShapePoint>? = null,
)
```

Nová veřejná funkce `OcrEngine.detectShapesOnly(pageUrl, blocks: List<RawTextBlock>): List<RawTextBlock>`
pro dodatečné dopočítání tvaru u starých cache záznamů (viz migrace níže) - načte
bitmapu jednou, pro každý vstupní blok jen zavolá `detectShape` (bez OCR/ML Kit),
aby nebylo nutné znovu volat rozpoznávání textu.

### 3. `TranslatedBlock` + cache (`TranslateRepository.kt`)

`TranslatedBlock` získá `val shape: List<BubbleShapePoint>? = null`. `translateWithGemini`/
`translateWithGroq`/`sfxBlock` ho zkopírují z odpovídajícího `ClassifiedBubble.raw.shape`.

Serializace: nové pole `"shape"` jako JSON pole trojic `[yF, leftF, rightF]`, chybí-li
(starý cache formát) → `shape = null` při deserializaci (zpětná kompatibilita, žádná
migrace schématu Room databáze - pořád jen opaque `blocksJson TEXT`).

**Migrace starých záznamů:** `TranslateRepository.getCachedPage()` po deserializaci
zkontroluje, jestli aspoň jeden ne-SFX blok má `shape == null`. Pokud ano, zavolá (mimo
hlavní vlákno, jednou) `ocrEngine.detectShapesOnly(pageUrl, blocks)`, výsledek znovu
serializuje a přepíše cache záznam (`dao.upsert`) - text/překlad se needituje, jen se
doplní tvar. Další zobrazení stejné stránky už je rychlé (tvar je v cache).

### 4. `TranslationLayout.kt` - zjednodušení

Když `block.shape != null`, **neprobíhá** heuristická expanze (`layoutTranslationBlocks`)
vůbec - souřadnice pro vykreslení se vezmou přímo z ohraničujícího obdélníku bodů tvaru
(min/max `leftF`/`rightF` přes všechny body, `topF`/`bottomF` z prvního/posledního bodu).
Heuristická expanze (současný kód beze změny) zůstává jako **fallback** jen pro bloky
s `shape == null` (detekce selhala nebo blok je starý a ještě nedoběhla migrace).

### 5. `ReaderScreen.kt` - vykreslení

Nový `Shape` (Compose `androidx.compose.ui.graphics.Shape`), např. `BubbleClipShape(points: List<BubbleShapePoint>)`
implementovaný přes `GenericShape { size, _ -> ... }` - poskládá `Path` z levých bodů
shora dolů a pravých bodů zdola nahoru (uzavřený polygon), souřadnice bodů se škálují
podle `size` boxu. Pro `shape == null` blok se použije beze změny současný
`RoundedCornerShape(3.dp)`.

`WebtoonPage`/`TranslationOverlay`: `.background(color, shapeOrRoundedRect)` místo
pevného `RoundedCornerShape(3.dp)`; pozice/rozměry boxu se počítají z ohraničujícího
obdélníku tvaru (bod 4), ne z `pos.leftF/rightF/minTopF/maxBottomF`.

### 6. Font podle typu bubliny

`BubbleType` se dnes počítá v `BubbleClassifier`, ale nikam k rendereru se nepropaguje -
`TranslatedBlock` ho nemá. Přidat `val bubbleType: BubbleType = BubbleType.SPEECH` do
`TranslatedBlock` (serializovat jako string, chybí-li ve starém cache → `SPEECH`).

Nové fonty (stejná rodina Comic Neue, OFL licence, stažení stejným způsobem jako
`comic_neue_regular.ttf`/`comic_neue_bold.ttf` z Google Fonts GitHub repa):
`comic_neue_italic.ttf`, `comic_neue_bold_italic.ttf`.

Mapování v `AutoFitTranslatedText` (nahrazuje pevné `ComicNeueFamily`):

| BubbleType           | Font                        |
|-----------------------|------------------------------|
| SHOUT                 | Comic Neue Bold              |
| THOUGHT, WHISPER      | Comic Neue Italic             |
| SPEECH, NARRATION, SYSTEM, SFX (nepoužije se, SFX box nemá) | Comic Neue Regular |

## Zpracování chyb / fallbacky

- `detectShape` selže (plocha moc velká) → `shape = null` → beze změny současné chování
  (heuristický obdélník). Žádná bublina tedy nezůstane nevykreslená.
- Chybějící/poškozený JSON `"shape"` pole při deserializaci → `null`, stejná zpětná
  kompatibilita jako u `disp`/`bg`/`sfx`/`lc` dříve.
- Migrace starého cache záznamu (dopočet tvaru) selže (stránka nedostupná apod.) →
  ponechat `shape = null` na dotčených blocích, zůstává heuristický fallback, žádný pád.

## Testování

- Unit testy `BubbleShapeDetectorTest.kt` (čistý JVM, žádný Robolectric) - syntetický
  `IntArray`-backed `PixelSource` s nakresleným oválem/obdélníkem známé barvy a pozice,
  ověří že vrácené body odpovídají očekávanému obrysu (±tolerance) a že plocha přesahující
  `maxAreaFraction` vrátí `null`.
- `TranslationLayoutTest.kt` - přidat případ pro blok se `shape != null` (přeskočí
  heuristiku), ponechat existující testy pro `shape == null` fallback beze změny.
- Živé ověření na emulátoru na konkrétním screenshotu z tohoto zadání (manga s bublinou
  co dřív přetékala přes půl stránky) - vizuální kontrola, že box kopíruje tvar bubliny.

## Rollout

Beze změny Room schématu (pořád opaque `blocksJson`), beze změny veřejného API zdrojů
(`MangaSource`). Bezpečné nasadit v běžném "Feat:" commitu bez migrace databáze.
