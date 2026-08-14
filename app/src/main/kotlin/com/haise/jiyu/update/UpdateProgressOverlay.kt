package com.haise.jiyu.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haise.jiyu.ui.theme.AccentLight
import com.haise.jiyu.ui.theme.DeepSpace
import com.haise.jiyu.ui.theme.GlowViolet
import com.haise.jiyu.ui.theme.TextPrimary
import com.haise.jiyu.ui.theme.TextSecondary
import compose.icons.TablerIcons
import compose.icons.tablericons.X
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Globální celoobrazovkový overlay pro stahování aktualizace - viz [ApkUpdateInstaller]
 * pro proč stav žije mimo obrazovku Nastavení. Vložit jednou nekam vysoko ve stromu
 * (MainActivity), stejně jako CloudflareChallengeHost.
 */
@Composable
fun UpdateProgressOverlay(installer: ApkUpdateInstaller) {
    val visible by installer.overlayVisible.collectAsState()
    val state by installer.downloadState.collectAsState()

    // Neresitelne selhani nema smysl drzet v teto obrazovce - schovej overlay a
    // necht uzivatele padnout zpet do Nastaveni, kde uz existuje Retry tlacitko.
    LaunchedEffect(state) {
        if (state is UpdateDownloadState.Failed) installer.dismissOverlay()
    }

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepSpace.copy(alpha = 0.97f)),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(
                onClick = { installer.dismissOverlay() },
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            ) {
                Icon(TablerIcons.X, contentDescription = null, tint = TextSecondary)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val current = state
                val fraction = when (current) {
                    is UpdateDownloadState.Downloading -> if (current.progress >= 0) current.progress / 100f else -1f
                    UpdateDownloadState.ReadyToInstall -> 1f
                    else -> -1f
                }
                CrystalAssembly(
                    progress = fraction,
                    modifier = Modifier.size(260.dp),
                )
                Spacer(Modifier.height(30.dp))
                val label = when (current) {
                    is UpdateDownloadState.Downloading ->
                        if (current.progress >= 0) "Stahování aktualizace… ${current.progress} %" else "Stahování aktualizace…"
                    UpdateDownloadState.ReadyToInstall -> "Otevírám instalaci…"
                    else -> "Připravuji stahování…"
                }
                Text(
                    label,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.3.sp,
                )

                // Vlasova linka presneho postupu - krystal nese naladu, ale procenta se z nej
                // odhadnout nedaji; u neurciteho postupu (fraction < 0) se nekresli vubec,
                // prazdna/nehybna lista by falesne tvrdila, ze stahovani nezacalo.
                if (fraction >= 0f) {
                    val barFraction by animateFloatAsState(
                        targetValue = fraction.coerceIn(0f, 1f),
                        animationSpec = tween(700, easing = FastOutSlowInEasing),
                        label = "bar",
                    )
                    Spacer(Modifier.height(18.dp))
                    Box(
                        modifier = Modifier
                            .width(190.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(TextSecondary.copy(alpha = 0.16f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(barFraction)
                                .fillMaxHeight()
                                .background(Brush.horizontalGradient(listOf(GlowViolet, AccentLight))),
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Geometrie
// ─────────────────────────────────────────────────────────────────────────────

private class Vec3(val x: Float, val y: Float, val z: Float)

/**
 * Dvanáct vrcholů ikosaedru na jednotkové kouli. Kanonická konstrukce ze tří vzájemně
 * kolmých zlatých obdélníků - proto se v souřadnicích střídá 0 / ±1 / ±φ.
 */
private val ICO_VERTICES: List<Vec3> = run {
    val phi = (1f + sqrt(5f)) / 2f
    val norm = sqrt(1f + phi * phi)
    listOf(
        Vec3(0f, 1f, phi), Vec3(0f, -1f, phi), Vec3(0f, 1f, -phi), Vec3(0f, -1f, -phi),
        Vec3(1f, phi, 0f), Vec3(-1f, phi, 0f), Vec3(1f, -phi, 0f), Vec3(-1f, -phi, 0f),
        Vec3(phi, 0f, 1f), Vec3(phi, 0f, -1f), Vec3(-phi, 0f, 1f), Vec3(-phi, 0f, -1f),
    ).map { Vec3(it.x / norm, it.y / norm, it.z / norm) }
}

/**
 * Třicet hran. Počítají se, ne vypisují: hrana je každá dvojice vrcholů v minimální
 * vzdálenosti, takže ručně opsaný seznam by byl jen další místo, kde udělat překlep.
 * Nejbližší dvojice mají d² ≈ 1.11, nejbližší NE-hrana ≈ 2.9 - práh 1.5 je bezpečně mezi.
 */
private val ICO_EDGES: List<Pair<Int, Int>> = buildList {
    for (i in ICO_VERTICES.indices) {
        for (j in i + 1 until ICO_VERTICES.size) {
            val a = ICO_VERTICES[i]
            val b = ICO_VERTICES[j]
            val dx = a.x - b.x
            val dy = a.y - b.y
            val dz = a.z - b.z
            if (dx * dx + dy * dy + dz * dz < 1.5f) add(i to j)
        }
    }
}

/** Dvacet stěn - trojice vrcholů, kde je hranou každá ze tří dvojic. */
private val ICO_FACES: List<Triple<Int, Int, Int>> = buildList {
    val edges = ICO_EDGES.toHashSet()
    fun linked(a: Int, b: Int) = (minOf(a, b) to maxOf(a, b)) in edges
    for (i in ICO_VERTICES.indices) {
        for (j in i + 1 until ICO_VERTICES.size) {
            if (!linked(i, j)) continue
            for (k in j + 1 until ICO_VERTICES.size) {
                if (linked(j, k) && linked(i, k)) add(Triple(i, j, k))
            }
        }
    }
}

/**
 * Kamera je schválně daleko a se slabým "objektivem". Blíž by perspektiva u vrcholů
 * mířících na diváka explodovala a tvar by při rotaci vylétal z rámu.
 */
private const val CAMERA_DIST = 5.0f
private const val FOCAL = 3.0f

/**
 * Kamera během skládání pomalu najíždí. Bez toho tvar opticky ZMENŠUJE - rozprášený oblak
 * zabírá 0.93 poloměru, ale hotový krystal jen 0.56, takže by vyvrcholení bylo vizuálně
 * nejmenší moment celé animace. S dojezdem drží obraz plný na obou koncích a jen se uprostřed
 * "nadechne" (0.93 → 0.75 → 0.92).
 *
 * Dojezd startuje až od [ZOOM_START]: dřív je oblak ještě rozvalený a přiblížení by ho
 * vytlačilo z rámu (naměřeno - při startu od 0 přeteče na 1.04 poloměru).
 */
private const val ZOOM_NEAR = 0.92f
private const val ZOOM_FAR = 1.50f
private const val ZOOM_START = 0.30f

/** Bod už promítnutý do 2D. [depth] je hloubka ve view prostoru - VĚTŠÍ = dál od kamery. */
private class Projected(val pos: Offset, val depth: Float, val scale: Float)

/** Rotace kolem osy Y (yaw), pak kolem osy X (pitch). Pořadí je pevné - jinak by se osy míchaly. */
private fun rotate(v: Vec3, yaw: Float, pitch: Float): Vec3 {
    val cy = cos(yaw)
    val sy = sin(yaw)
    val x1 = v.x * cy + v.z * sy
    val z1 = -v.x * sy + v.z * cy

    val cp = cos(pitch)
    val sp = sin(pitch)
    val y2 = v.y * cp - z1 * sp
    val z2 = v.y * sp + z1 * cp

    return Vec3(x1, y2, z2)
}

private fun project(v: Vec3, cx: Float, cy: Float, radius: Float, zoom: Float): Projected {
    // coerceAtLeast chrani pred delenim skoro nulou, kdyby se bod dostal az do kamery.
    val f = FOCAL / (v.z + CAMERA_DIST).coerceAtLeast(0.4f)
    val m = f * radius * zoom
    return Projected(Offset(cx + v.x * m, cy + v.y * m), v.z, f)
}

/** Smoothstep - dojezd kamery musí začínat i končit v klidu, lineární náběh by cuknul. */
private fun smoothstep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * Kde vrchol [index] visí, dokud se krystal neskládá. Směr i poloměr jsou DETERMINISTICKÉ
 * funkce indexu (žádný Random) - jinak by se oblak při každém překreslení přeházel a místo
 * pomalého driftu by blikal. [drift] pomalu otáčí každý bod jinou rychlostí, aby oblak žil.
 */
private fun scatteredPosition(index: Int, drift: Float): Vec3 {
    val az = index * 2.399963f + drift * (0.55f + 0.30f * sin(index * 1.7f))
    val el = sin(index * 1.113f + 0.7f) * 1.15f
    val r = 1.30f + 0.30f * sin(index * 2.7f + 1.3f)
    val ce = cos(el)
    return Vec3(r * ce * cos(az), r * sin(el), r * ce * sin(az))
}

/** Atmosferická perspektiva - co je dál, je tlumenější. Bez toho je drátěný model plochý. */
private fun fog(depth: Float): Float = ((1.7f - depth) / 3.4f).coerceIn(0.16f, 1f)

// Krystal je prusvitne fialovy s bilymi odlesky. Ciste bily dratovy model cte jako
// technicky vykres, ne jako svitici hmota.
private val CrystalEdge = Color(0xFFB388FF)
private val CoreMagenta = Color(0xFFC77DFF)

/**
 * Ikosaedr, který se podle [progress] (0f–1f) skládá z rozprášeného oblaku bodů: na začátku
 * dvanáct vrcholů volně pluje v prostoru, postupně každý doletí na své místo, mezi usazenými
 * vrcholy se rozsvěcují hrany a nakonec se z drátěného modelu stane prosvícené těleso.
 * Metafora je doslovná - rozházená data se skládají v celek.
 *
 * Záporná hodnota [progress] = neurčitý postup (appka ještě nezná velikost souboru): tvar se
 * pak pomalu "dýchá" mezi rozpadem a téměř složeným stavem místo aby předstíral procenta.
 */
@Composable
private fun CrystalAssembly(progress: Float, modifier: Modifier = Modifier) {
    val indeterminate = progress < 0f
    val infinite = rememberInfiniteTransition(label = "crystal")

    // Rotace je LINEARNI - easing na nekonecne smycce dela viditelne skubnuti pri kazdem
    // navratu na zacatek. Obe osy maji nesoudelne periody, jinak se pohled po chvili opakuje
    // a telo vypada, ze se toci jen kolem jedne osy.
    val yaw by infinite.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(tween(19_000, easing = LinearEasing)),
        label = "yaw",
    )
    val pitch by infinite.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(tween(31_000, easing = LinearEasing)),
        label = "pitch",
    )
    val drift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(tween(13_000, easing = LinearEasing)),
        label = "drift",
    )
    val corePulse by infinite.animateFloat(
        initialValue = 0.74f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "corePulse",
    )
    val idleAssembly by infinite.animateFloat(
        initialValue = 0.14f,
        targetValue = 0.70f,
        animationSpec = infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "idle",
    )

    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "progress",
    )
    val assembly = if (indeterminate) idleAssembly else animatedProgress

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = min(size.width, size.height) / 2f
        val strokeW = (radius * 0.011f).coerceAtLeast(1.1f)
        val glow = (0.30f + 0.70f * assembly) * corePulse

        // ── Vrcholy: rozprášený oblak -> své místo v ikosaedru ────────────────
        val count = ICO_VERTICES.size
        val arrivals = FloatArray(count) { i ->
            // Pri neurcitem postupu leti vsechny vrcholy spolecne - stagger by v opakovanem
            // tam-a-zpet pusobil jako porucha, ne jako zamer.
            if (indeterminate) assembly else AssemblySchedule.vertexArrival(assembly, i, count)
        }
        val zoom = ZOOM_NEAR + (ZOOM_FAR - ZOOM_NEAR) *
            smoothstep((assembly - ZOOM_START) / (1f - ZOOM_START))
        val points = Array(count) { i ->
            val target = ICO_VERTICES[i]
            val loose = scatteredPosition(i, drift)
            val t = arrivals[i]
            val world = Vec3(
                loose.x + (target.x - loose.x) * t,
                loose.y + (target.y - loose.y) * t,
                loose.z + (target.z - loose.z) * t,
            )
            project(rotate(world, yaw, pitch), cx, cy, radius, zoom)
        }

        // ── 1) Ambientní záře v pozadí ────────────────────────────────────────
        // Polomer MUSI zustat uvnitr Canvasu: pri vetsim se gradient rezne o jeho hranu a
        // kolem krystalu vznikne viditelny svetly ctverec.
        val ambientR = radius * 0.98f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    GlowViolet.copy(alpha = 0.10f * glow + 0.03f),
                    GlowViolet.copy(alpha = 0.03f * glow),
                    Color.Transparent,
                ),
                center = Offset(cx, cy),
                radius = ambientR,
            ),
            radius = ambientR,
            center = Offset(cx, cy),
        )

        // ── 2) Hrany: nejdřív odvrácená polovina ──────────────────────────────
        // Rozdeleni na zadni a predni pulku (mezi ne prijde zare jadra) je to, co dela
        // prostorovy dojem - predni hrany se pak rysuji PROTI svetlu.
        val edges = ICO_EDGES.map { (a, b) ->
            Triple(a, b, (points[a].depth + points[b].depth) / 2f)
        }.sortedByDescending { it.third }

        fun drawEdge(a: Int, b: Int, midDepth: Float) {
            val strength = AssemblySchedule.edgeStrength(arrivals[a], arrivals[b])
            if (strength <= 0.01f) return
            val d = fog(midDepth)
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        CrystalEdge.copy(alpha = 0.85f * strength * d),
                        Color.White.copy(alpha = 0.55f * strength * d * glow),
                        CrystalEdge.copy(alpha = 0.85f * strength * d),
                    ),
                    start = points[a].pos,
                    end = points[b].pos,
                ),
                start = points[a].pos,
                end = points[b].pos,
                strokeWidth = strokeW * (0.65f + 0.55f * d),
                cap = StrokeCap.Round,
            )
        }

        edges.filter { it.third > 0f }.forEach { (a, b, d) -> drawEdge(a, b, d) }

        // ── 3) Záře jádra MEZI vrstvami ───────────────────────────────────────
        // Zare musi zustat mensi nez samotny krystal - jinak prezari dratenou strukturu,
        // ktera je tu hlavni, a zbyde z toho svitici koule s parem carek kolem.
        val coreR = radius * (0.09f + 0.14f * assembly)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    CoreMagenta.copy(alpha = 0.55f * glow),
                    GlowViolet.copy(alpha = 0.40f * glow),
                    Color.Transparent,
                ),
                center = Offset(cx, cy),
                radius = coreR * 2.0f,
            ),
            radius = coreR * 2.0f,
            center = Offset(cx, cy),
        )

        // ── 4) Stěny - jen přivrácené a až v poslední třetině ──────────────────
        // Do te doby jsou vrcholy jeste na cestě a vyplň mezi nimi by kreslila nesmyslné
        // trojúhelníky napříč prostorem. Konvexní těleso => přivrácená je stěna, jejíž
        // těžiště leží na straně kamery (záporná hloubka).
        val faceAlpha = ((assembly - 0.70f) / 0.30f).coerceIn(0f, 1f)
        if (faceAlpha > 0.01f) {
            ICO_FACES.forEach { (i, j, k) ->
                val midDepth = (points[i].depth + points[j].depth + points[k].depth) / 3f
                if (midDepth >= 0f) return@forEach
                val path = Path().apply {
                    moveTo(points[i].pos.x, points[i].pos.y)
                    lineTo(points[j].pos.x, points[j].pos.y)
                    lineTo(points[k].pos.x, points[k].pos.y)
                    close()
                }
                // Cim vic je stena natocena ke kamere, tim min svitit - presne naopak nez
                // difuzni svetlo. Je to fresnel: sklo se rozsviti na okrajich, ne uprostred.
                val rim = (midDepth / -1f).coerceIn(0f, 1f)
                drawPath(
                    path = path,
                    color = GlowViolet.copy(alpha = 0.16f * faceAlpha * (1f - rim * 0.75f)),
                )
            }
        }

        // ── 5) Přivrácená polovina hran ───────────────────────────────────────
        edges.filter { it.third <= 0f }.forEach { (a, b, d) -> drawEdge(a, b, d) }

        // ── 6) Vrcholy ────────────────────────────────────────────────────────
        // Kresli se odzadu dopredu, aby blizsi vrchol prekryl vzdalenejsi. Body jsou male
        // a JASNE - velka poloprusvitna tecka na tmavem pozadi zesedne a cte se jako prach
        // na obrazovce, ne jako svetlo.
        points.indices.sortedByDescending { points[it].depth }.forEach { i ->
            val p = points[i]
            val d = fog(p.depth)
            // Nedorazeny vrchol je jen tise plujici castice, usazeny svitit naplno.
            val landed = arrivals[i]
            val dotR = (radius * 0.017f * p.scale * (0.75f + 0.45f * d)).coerceAtLeast(1f)
            val bright = (0.35f + 0.65f * landed) * d
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.95f * bright),
                        AccentLight.copy(alpha = 0.55f * bright),
                        Color.Transparent,
                    ),
                    center = p.pos,
                    radius = dotR * 3.4f,
                ),
                radius = dotR * 3.4f,
                center = p.pos,
            )
        }

        // ── 7) Přepálené jádro ────────────────────────────────────────────────
        // Maly tvrdy zdroj svetla. Bez nej je stred jen mekka skvrna, ktera pri vysokem jasu
        // cte jako seda, ne jako svitici hmota.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.95f * glow),
                    Color.White.copy(alpha = 0.45f * glow),
                    Color.Transparent,
                ),
                center = Offset(cx, cy),
                radius = coreR * 0.42f,
            ),
            radius = coreR * 0.42f,
            center = Offset(cx, cy),
        )
    }
}

private const val TWO_PI = (2.0 * Math.PI).toFloat()
