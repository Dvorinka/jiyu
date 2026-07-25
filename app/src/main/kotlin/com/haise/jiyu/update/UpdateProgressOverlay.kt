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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
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
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

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
                GlassBloom(
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

                // Vlasova linka presneho postupu - kvet nese naladu, ale procenta se z tvaru
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

/**
 * Jedna vrstva plátků. Květ je složený ze tří vrstev, které se otevírají postupně (viz
 * [BloomSchedule]) a hlavně se ROZVÍRAJÍ V PROSTORU: [closedElevationDeg] je téměř svisle
 * vzhůru (poupě), [openElevationDeg] až za vodorovnou rovinu (rozkvetlá hvězda). Právě
 * přechod z vysokého poupěte do plochého květu dělá dojem skutečného rozkvétání - plátky,
 * které se jen prodlužují v ploše, čtou jako rostoucí hvězdička.
 *
 * @param openElevationDeg > 90° = plátek se sklopí POD vodorovnou rovinu (vnější vrstva).
 * @param litness kolik světla z jádra na vrstvu dopadá (vnitřní korunka je zdroji nejblíž).
 * @param spinFactor relativní rychlost a směr rotace (negativní = proti smyslu ostatních).
 */
private data class PetalLayer(
    val count: Int,
    val lengthFactor: Float,
    val widthFactor: Float,
    val closedElevationDeg: Float,
    val openElevationDeg: Float,
    val curl: Float,
    val angleOffsetDeg: Float,
    val spinFactor: Float,
    val litness: Float,
)

private val PETAL_LAYERS = listOf(
    // Vnejsi vrstva se sklopi az za vodorovnou rovinu (95°) - v referenci prave tyhle
    // plátky visi mirne dolu pod kvetem a delaji mu "podnoz".
    PetalLayer(11, 1.00f, 0.200f, closedElevationDeg = 15f, openElevationDeg = 95f, curl = 0.10f, angleOffsetDeg = 0f, spinFactor = 1.00f, litness = 0.55f),
    PetalLayer(9, 0.74f, 0.240f, closedElevationDeg = 9f, openElevationDeg = 76f, curl = -0.13f, angleOffsetDeg = 19f, spinFactor = -0.60f, litness = 0.80f),
    // Vnitrni korunka zustava vzprimenejsi (50°) a obepina jadro jako kosicek.
    PetalLayer(7, 0.50f, 0.280f, closedElevationDeg = 5f, openElevationDeg = 50f, curl = 0.18f, angleOffsetDeg = 33f, spinFactor = 0.36f, litness = 1.00f),
)

/**
 * Sklon "kamery": jak zploštělá je vodorovná rovina květu při projekci do 2D. 1.0 = pohled
 * přesně shora (kruh), 0.0 = přesně z boku (úsečka). 0.42 odpovídá nadhledu ~25°, tedy
 * stejnému pohledu jako v referenci.
 */
private const val PLANE_TILT = 0.54f

/** Kolik z výšky plátku nad rovinou se promítne do svislého posunu na obrazovce. */
private const val HEIGHT_SCALE = 0.85f

// Krystal je PRUSVITNY a syte fialovy - ne cerny. Cerne telo cte jako kamen nebo papir,
// prusvitna fialova s bilymi odlesky jako brouseny amethyst (viz reference).
private val CrystalLit = Color(0xFF7B3FB8)
private val CrystalMid = Color(0xFF2A1049)
private val CrystalDeep = Color(0xFF120619)
private val CrystalTip = Color(0xFF07030D)

/**
 * Jadro neni ciste fialove - prechod do svetle purpurove dela dojem rozzhavene hmoty
 * (cista fialova pri vysokem jasu zesedne a poupe vypada matne, ne svitici).
 */
private val CoreMagenta = Color(0xFFC77DFF)

/**
 * Jeden plátek už promítnutý do 2D obrazovky. Sbírá se do seznamu, protože plátky se musí
 * kreslit v pořadí od nejvzdálenějšího k nejbližšímu ([depth]) - bez toho by plátky ze zadní
 * strany květu překrývaly ty přední a prostorový dojem se rozpadne.
 */
private class ProjectedPetal(
    val depth: Float,
    val base: Offset,
    val tip: Offset,
    val halfWidth: Float,
    val curlOffset: Float,
    val litness: Float,
)

/**
 * Skleněný krystalický květ, který se rozvíjí podle [progress] (0f–1f): z vysokého zavřeného
 * poupěte přes rozvírající se kalich až do plné hvězdy, přičemž jádro uvnitř postupně žhne -
 * od nenápadného zrnka přes svítící orb a rotující vír až po přepálený bílý bod
 * s krystalickými odlesky.
 *
 * Záporná hodnota [progress] = neurčitý postup (appka ještě nezná velikost souboru) - květ se
 * pak plynule "dýchá" mezi poloprázdným a téměř otevřeným stavem místo sledování hodnoty.
 */
@Composable
private fun GlassBloom(progress: Float, modifier: Modifier = Modifier) {
    val indeterminate = progress < 0f
    val infinite = rememberInfiniteTransition(label = "glassBloom")

    val spinDeg by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(64_000, easing = LinearEasing)),
        label = "spin",
    )
    // Nadechnuti celeho tvaru - bez nej pusobi kvet jako staticky obrazek, ktery se jen otaci.
    val breath by infinite.animateFloat(
        initialValue = 0.975f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(tween(3400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    val corePulse by infinite.animateFloat(
        initialValue = 0.80f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "corePulse",
    )
    val idlePulse by infinite.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.80f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "idlePulse",
    )
    // Vir v jadru se otaci vyrazne rychleji nez kvet - kontrast rychlosti dela dojem energie
    // uvnitr, ne jen dalsi rotujici grafiky.
    val vortexDeg by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
        label = "vortex",
    )
    val moteWave by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing)),
        label = "moteWave",
    )

    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "progress",
    )
    val bloomProgress = if (indeterminate) idlePulse else animatedProgress
    val glow = (if (indeterminate) idlePulse else 0.22f + animatedProgress * 0.78f) * corePulse

    Canvas(modifier = modifier) {
        val maxRadius = min(size.width, size.height) / 2f * breath * 0.92f
        // Zavrene poupe je vysoke a roste vzhuru - posuneme ho niz, aby v ramci plochy sedelo
        // opticky na stredu misto aby "vylezalo" nahoru z ramu.
        val anchor = Offset(size.width / 2f, size.height / 2f + maxRadius * 0.17f * (1f - bloomProgress))
        val strokeW = (maxRadius * 0.008f).coerceAtLeast(1f)

        /**
         * Plátek jako uzavřená křivka z kubických Bézierů - jedna hrana vypouklá, druhá vydutá,
         * špička odkloněná a mírně TUPÁ (ostré jehly čtou jako trny, ne jako krystal). Krystal
         * vzniká čtveřicí přesvětlení: osvícená hrana, odvrácená hrana, podélný hřeben brusu
         * a světelný bod na samotné špičce.
         */
        fun drawPetal(p: ProjectedPetal) {
            val dx = p.tip.x - p.base.x
            val dy = p.tip.y - p.base.y
            val len = hypot(dx, dy)
            if (len < 0.75f) return
            val dir = Offset(dx / len, dy / len)
            val perp = Offset(-dir.y, dir.x)

            val hw = p.halfWidth
            val tipMid = p.tip + perp * p.curlOffset
            val tipHalf = hw * 0.13f
            val tipA = tipMid + perp * tipHalf
            val tipB = tipMid - perp * tipHalf

            val a1 = p.base + dir * (len * 0.10f) + perp * (hw * 1.00f)
            val a2 = p.base + dir * (len * 0.58f) + perp * (hw * 0.82f)
            val b1 = p.base + dir * (len * 0.60f) - perp * (hw * 0.58f)
            val b2 = p.base + dir * (len * 0.12f) - perp * (hw * 0.76f)

            val body = Path().apply {
                moveTo(p.base.x, p.base.y)
                cubicTo(a1.x, a1.y, a2.x, a2.y, tipA.x, tipA.y)
                quadraticBezierTo(tipMid.x, tipMid.y, tipB.x, tipB.y)
                cubicTo(b1.x, b1.y, b2.x, b2.y, p.base.x, p.base.y)
                close()
            }

            // Prusvitne telo: rozzarena zakladna u jadra -> syta fialova -> tmavy hrot. Alfa
            // pod 1 je zamer - prekryvajici se plátky pak prosvitaji jeden skrz druhy, presne
            // jak se chova brouseny krystal.
            drawPath(
                path = body,
                brush = Brush.linearGradient(
                    0.00f to CrystalLit.copy(alpha = (0.26f + 0.38f * glow) * p.litness),
                    0.20f to CrystalMid.copy(alpha = 0.90f),
                    0.52f to CrystalDeep.copy(alpha = 0.92f),
                    1.00f to CrystalTip.copy(alpha = 0.80f),
                    start = p.base,
                    end = tipMid,
                ),
            )

            // Osvicena hrana - nejsilnejsi presvetleni, slabne ke spicce
            drawPath(
                path = Path().apply {
                    moveTo(p.base.x, p.base.y)
                    cubicTo(a1.x, a1.y, a2.x, a2.y, tipA.x, tipA.y)
                },
                brush = Brush.linearGradient(
                    0.00f to Color.White.copy(alpha = (0.24f + 0.52f * glow) * p.litness),
                    0.62f to Color.White.copy(alpha = 0.12f * p.litness),
                    1.00f to Color.Transparent,
                    start = p.base,
                    end = tipA,
                ),
                style = Stroke(width = strokeW * 1.35f),
            )

            // Odvracena hrana - jen naznak, aby plátek nesplyval s pozadim u obrysu
            drawPath(
                path = Path().apply {
                    moveTo(tipB.x, tipB.y)
                    cubicTo(b1.x, b1.y, b2.x, b2.y, p.base.x, p.base.y)
                },
                brush = Brush.linearGradient(
                    0.00f to Color.Transparent,
                    1.00f to AccentLight.copy(alpha = 0.26f * glow * p.litness),
                    start = tipB,
                    end = p.base,
                ),
                style = Stroke(width = strokeW * 0.85f),
            )

            // Hreben brusu podel osy - tenka svetla linka, ktera z plochy udela facetu
            val spineStart = p.base + dir * (len * 0.14f)
            drawPath(
                path = Path().apply {
                    moveTo(spineStart.x, spineStart.y)
                    lineTo(tipMid.x, tipMid.y)
                },
                brush = Brush.linearGradient(
                    0.00f to Color.White.copy(alpha = (0.20f + 0.24f * glow) * p.litness),
                    0.80f to Color.White.copy(alpha = 0.06f * p.litness),
                    1.00f to Color.Transparent,
                    start = spineStart,
                    end = tipMid,
                ),
                style = Stroke(width = strokeW * 0.9f),
            )

            // Svetelny bod na spicce - brouseny hrot chyta svetlo. Skaluje se DRUHOU mocninou
            // osvetleni: na tmavych vnejsich plátcich by linearni jas dal poloprusvitnou
            // sedou tecku, ktera cte jako prach na obrazovce, ne jako odlesk.
            val tipGlint = 0.55f * glow * p.litness * p.litness
            if (tipGlint > 0.04f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = tipGlint), Color.Transparent),
                        center = tipMid,
                        radius = strokeW * 1.9f,
                    ),
                    radius = strokeW * 1.9f,
                    center = tipMid,
                )
            }
        }

        // ── Projekce vsech plátku do 2D ───────────────────────────────────────
        val petals = ArrayList<ProjectedPetal>(PETAL_LAYERS.sumOf { it.count })
        PETAL_LAYERS.forEachIndexed { layerIndex, layer ->
            val openness = if (indeterminate) {
                BloomSchedule.MIN_OPENNESS + (1f - BloomSchedule.MIN_OPENNESS) * bloomProgress
            } else {
                BloomSchedule.layerOpenness(bloomProgress, layerIndex, PETAL_LAYERS.size)
            }

            // Elevace: 0° = svisle vzhuru (poupe), 90° = vodorovne, >90° = sklopene dolu
            val elevDeg = layer.closedElevationDeg +
                (layer.openElevationDeg - layer.closedElevationDeg) * openness
            val elevRad = Math.toRadians(elevDeg.toDouble())
            val sinE = sin(elevRad).toFloat()
            val cosE = cos(elevRad).toFloat()

            for (i in 0 until layer.count) {
                val azDeg = (360f / layer.count) * i +
                    layer.angleOffsetDeg +
                    spinDeg * layer.spinFactor
                val azRad = Math.toRadians(azDeg.toDouble())
                val cosA = cos(azRad).toFloat()
                val sinA = sin(azRad).toFloat()

                // Deterministicka variace delky - naprosto stejne dlouhe plátky vypadaji
                // strojove; par procent rozdilu udela dojem rostleho tvaru.
                val jitter = 1f + 0.10f * sin(i * 2.399f + layerIndex)
                val len = maxRadius * layer.lengthFactor * (0.36f + 0.64f * openness) * jitter

                // Vodorovny dosah a vyska nad rovinou -> projekce na obrazovku
                val rh = len * sinE
                val h = len * cosE
                val tip = Offset(
                    anchor.x + rh * cosA,
                    anchor.y + rh * sinA * PLANE_TILT - h * HEIGHT_SCALE,
                )

                // Plátky odvracene od kamery jsou tmavsi (atmosfericka perspektiva) - bez toho
                // vypada kvet jako plochy ornament, i kdyz geometrie je prostorova.
                val facing = (sinA + 1f) / 2f
                val depthLit = 0.68f + 0.32f * facing

                petals += ProjectedPetal(
                    depth = sinA,
                    base = anchor,
                    tip = tip,
                    halfWidth = len * layer.widthFactor * (1f + (1f - openness) * 0.55f),
                    curlOffset = len * layer.curl * openness,
                    litness = layer.litness * depthLit,
                )
            }
        }

        // ── 1) Ambientni zare v pozadi ────────────────────────────────────────
        // Radius MUSI zustat v ramci Canvasu: pri vetsim se gradient rezne o jeho hranu a
        // kolem kvetu vznikne viditelny svetly ctverec.
        val ambientRadius = min(size.width, size.height) / 2f * 0.98f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    GlowViolet.copy(alpha = 0.11f * glow + 0.03f),
                    GlowViolet.copy(alpha = 0.03f * glow),
                    Color.Transparent,
                ),
                center = anchor,
                radius = ambientRadius,
            ),
            radius = ambientRadius,
            center = anchor,
        )

        // ── 2) Zadni polovina plátku (odvracena od kamery) ────────────────────
        val sorted = petals.sortedBy { it.depth }
        sorted.filter { it.depth <= 0f }.forEach { drawPetal(it) }

        // ── 3) Zare jadra MEZI vrstvami - predni plátky se pak rysuji jako tmava silueta
        // PROTI svetlu, coz dela nejvetsi cast prostoroveho dojmu.
        val glowRadius = maxRadius * (0.16f + 0.40f * glow)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    CoreMagenta.copy(alpha = 0.60f * glow),
                    GlowViolet.copy(alpha = 0.45f * glow),
                    Color.Transparent,
                ),
                center = anchor,
                radius = glowRadius,
            ),
            radius = glowRadius,
            center = anchor,
        )

        // ── 4) Predni polovina plátku ─────────────────────────────────────────
        sorted.filter { it.depth > 0f }.forEach { drawPetal(it) }

        // ── 5) Jadro: zrnko -> orb -> vir -> prepaleny bod ────────────────────
        val coreR = maxRadius * (0.08f + 0.13f * glow)
        scale(scaleX = 0.72f, scaleY = 1f, pivot = anchor) {
            drawCircle(
                brush = Brush.radialGradient(
                    0.00f to Color.White.copy(alpha = 0.96f * glow),
                    0.30f to CoreMagenta.copy(alpha = 0.92f * glow),
                    0.64f to GlowViolet.copy(alpha = 0.55f * glow),
                    1.00f to Color.Transparent,
                    center = anchor,
                    radius = coreR,
                ),
                radius = coreR,
                center = anchor,
            )
        }

        // Vir - objevi se az kdyz je kvet rozevreny natolik, ze je jadro videt, a na uplnem
        // konci ustoupi prepalenemu bodu (viz storyboard reference).
        val vortexAlpha = ((bloomProgress - 0.40f) / 0.22f).coerceIn(0f, 1f) *
            (1f - ((bloomProgress - 0.90f) / 0.10f).coerceIn(0f, 1f))
        if (vortexAlpha > 0.01f) {
            val vr = maxRadius * 0.23f
            for (arm in 0 until 3) {
                val spiral = Path()
                var t = 0.10f
                var first = true
                while (t <= 1.001f) {
                    val ang = Math.toRadians((vortexDeg + arm * 120f + t * 300f).toDouble())
                    val rr = vr * t
                    val x = anchor.x + cos(ang).toFloat() * rr
                    val y = anchor.y + sin(ang).toFloat() * rr * 0.80f
                    if (first) {
                        spiral.moveTo(x, y)
                        first = false
                    } else {
                        spiral.lineTo(x, y)
                    }
                    t += 0.035f
                }
                drawPath(
                    path = spiral,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.85f * vortexAlpha * glow),
                            CoreMagenta.copy(alpha = 0.60f * vortexAlpha * glow),
                            Color.Transparent,
                        ),
                        center = anchor,
                        radius = vr,
                    ),
                    style = Stroke(width = strokeW * 1.5f),
                )
            }
        }

        // Prepalene horke jadro - maly tvrdy zdroj svetla. Bez nej je poupe jen mekka skvrna,
        // ktera pri vysokem jasu cte jako sediva, ne jako svitici hmota.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.98f * glow),
                    Color.White.copy(alpha = 0.50f * glow),
                    Color.Transparent,
                ),
                center = anchor,
                radius = coreR * 0.46f,
            ),
            radius = coreR * 0.46f,
            center = anchor,
        )
        // Bodovy odlesk mimo stred - musi byt maly a ostry (mekky vetsi kruh na svetlem jadru
        // zesedne a cte se jako smitko, ne odlesk).
        val specR = (coreR * 0.085f).coerceAtLeast(0.8f)
        val specC = anchor + Offset(-coreR * 0.26f, -coreR * 0.38f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.95f * glow), Color.Transparent),
                center = specC,
                radius = specR * 2.2f,
            ),
            radius = specR * 2.2f,
            center = specC,
        )

        // ── 6) Krystalicke odlesky u jadra na konci rozkvetu ──────────────────
        val fragAlpha = ((bloomProgress - 0.76f) / 0.24f).coerceIn(0f, 1f)
        if (fragAlpha > 0.01f) {
            for (k in 0 until 5) {
                val ang = Math.toRadians((vortexDeg * 0.35f + k * 72f).toDouble())
                val d = maxRadius * (0.15f + 0.055f * (k % 3))
                val c = anchor + Offset(cos(ang).toFloat() * d, sin(ang).toFloat() * d * 0.78f)
                val a = 0.85f * fragAlpha * glow
                // Zablesk je NATOCENY podle sve pozice a ma jedno rameno delsi - osove
                // zarovnany kriz stejnych ramen cte doslova jako znak "+", ne jako jiskra.
                val rot = ang.toFloat() + k * 0.7f
                val long = strokeW * (3.2f + 1.1f * (k % 2))
                val short = long * 0.42f
                val u = Offset(cos(rot), sin(rot))
                val v = Offset(-u.y, u.x)
                drawLine(
                    color = Color.White.copy(alpha = a),
                    start = c - u * long,
                    end = c + u * long,
                    strokeWidth = strokeW * 0.6f,
                )
                drawLine(
                    color = Color.White.copy(alpha = a * 0.7f),
                    start = c - v * short,
                    end = c + v * short,
                    strokeWidth = strokeW * 0.5f,
                )
            }
        }

        // ── 7) Svetelne prasinky v prostoru kolem kvetu ───────────────────────
        // Prasinky musi byt male a JASNE, ne velke a poloprusvitne - poloprusvitna tecka na
        // tmavem pozadi zesedne a cte se jako prach/sum na obrazovce, ne jako svetlo.
        val moteCount = 5
        val moteR = (maxRadius * 0.0045f).coerceAtLeast(0.9f)
        for (i in 0 until moteCount) {
            val ang = Math.toRadians((spinDeg * 0.22f + (360f / moteCount) * i + i * 13f).toDouble())
            val dist = maxRadius * (0.48f + 0.11f * (i % 5)) * (0.55f + 0.45f * bloomProgress)
            val twinkle = (0.25f + 0.75f * ((sin(moteWave + i * 0.9f) + 1f) / 2f)) * glow
            val c = anchor + Offset(
                cos(ang).toFloat() * dist,
                sin(ang).toFloat() * dist * PLANE_TILT - maxRadius * 0.10f,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.85f * twinkle),
                        AccentLight.copy(alpha = 0.35f * twinkle),
                        Color.Transparent,
                    ),
                    center = c,
                    radius = moteR * 3.2f,
                ),
                radius = moteR * 3.2f,
                center = c,
            )
        }
    }
}
