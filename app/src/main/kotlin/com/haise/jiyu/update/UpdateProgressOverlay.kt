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
 * Jedna vrstva plátků. Květ je složený ze tří vrstev různé velikosti, které se otevírají
 * postupně (viz [BloomSchedule]) a rotují různou rychlostí i směrem - právě ten rozdílný
 * pohyb dělá paralaxu a dojem hloubky. Jedna vrstva stejných hrotů = plochá "hvězdička".
 *
 * @param litness kolik světla z jádra na vrstvu dopadá (přední vrstva je nejblíž zdroji).
 * @param spinFactor relativní rychlost a směr rotace (negativní = proti smyslu ostatních).
 */
private data class PetalLayer(
    val count: Int,
    val lengthFactor: Float,
    val widthFactor: Float,
    val curl: Float,
    val angleOffsetDeg: Float,
    val spinFactor: Float,
    val litness: Float,
)

private val PETAL_LAYERS = listOf(
    PetalLayer(count = 11, lengthFactor = 1.00f, widthFactor = 0.250f, curl = 0.12f, angleOffsetDeg = 0f, spinFactor = 1.00f, litness = 0.50f),
    PetalLayer(count = 9, lengthFactor = 0.73f, widthFactor = 0.300f, curl = -0.16f, angleOffsetDeg = 17f, spinFactor = -0.62f, litness = 0.80f),
    PetalLayer(count = 6, lengthFactor = 0.47f, widthFactor = 0.360f, curl = 0.22f, angleOffsetDeg = 31f, spinFactor = 0.38f, litness = 1.00f),
)

// Sklo se nekresli jednou barvou - telo plátku jde od fialove osvicene zakladny pres
// tmave purpurove sklo az k temer cerne spicce, jinak tvar ztraci objem a vypada jako
// plochy vystrizek.
private val GlassMid = Color(0xFF2A1E44)
private val GlassDeep = Color(0xFF120D1F)
private val GlassTip = Color(0xFF07050C)

/**
 * Jadro neni ciste fialove - prechod do svetle purpurove dela dojem rozzhavene hmoty
 * (cista fialova pri vysokem jasu zesedne a poupe vypada matne, ne svitici).
 */
private val CoreMagenta = Color(0xFFC77DFF)

/**
 * Skleněný květ, který se rozvíjí podle [progress] (0f–1f) a uvnitř kterého sílí fialová
 * záře. Záporná hodnota = neurčitý postup (appka ještě nezná velikost souboru) - v tom
 * případě se květ jemně "dýchá" mezi poloprázdným a téměř otevřeným stavem místo
 * sledování konkrétní hodnoty.
 *
 * Kreslí se v pořadí pozadí → zadní vrstvy → záře jádra → přední vrstva → jádro, aby se
 * přední plátky rýsovaly jako tmavá silueta PROTI záři. Tohle prolnutí dělá největší část
 * dojmu hloubky; při kreslení jádra až nakonec (nad všemi plátky) se efekt ztratí.
 */
@Composable
private fun GlassBloom(progress: Float, modifier: Modifier = Modifier) {
    val indeterminate = progress < 0f
    val infinite = rememberInfiniteTransition(label = "glassBloom")

    val spinDeg by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(72_000, easing = LinearEasing)),
        label = "spin",
    )
    // Nadechnuti celeho tvaru - bez nej pusobi kvet jako statmicky obrazek, ktery se jen otaci.
    val breath by infinite.animateFloat(
        initialValue = 0.975f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(tween(3400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    val corePulse by infinite.animateFloat(
        initialValue = 0.78f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "corePulse",
    )
    val idlePulse by infinite.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.78f,
        animationSpec = infiniteRepeatable(tween(2100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "idlePulse",
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
    val glow = (if (indeterminate) idlePulse else 0.25f + animatedProgress * 0.75f) * corePulse

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = min(size.width, size.height) / 2f * breath
        val strokeW = (maxRadius * 0.008f).coerceAtLeast(1f)

        /**
         * Plátek jako uzavřená křivka z kubických Bézierů (ne trojúhelník) - jedna hrana
         * vypouklá, druhá vydutá a špička odkloněná do strany ([curlOffset]), takže tvar čte
         * jako organický list, ne jako geometrický hrot. Špička je záměrně mírně TUPÁ (krátká
         * úsečka místo matematického bodu) - ostré jehly čtou jako trny, ne jako plátek skla.
         *
         * Sklo vzniká trojicí přesvětlení: hrana na osvícené straně, slabší hrana na stinované
         * (sklo si vede světlo i po odvrácené hraně) a vnitřní podélný odlesk uvnitř těla.
         * Bez toho vnitřního odlesku vypadá plátek jako tmavý papír.
         */
        fun drawPetal(angleDeg: Float, len: Float, halfWidth: Float, curlOffset: Float, litness: Float) {
            val rad = Math.toRadians(angleDeg.toDouble())
            val dir = Offset(cos(rad).toFloat(), sin(rad).toFloat())
            val perp = Offset(-sin(rad).toFloat(), cos(rad).toFloat())

            val base = center + dir * (maxRadius * 0.045f)
            val tipMid = base + dir * len + perp * curlOffset
            val tipHalf = halfWidth * 0.13f
            val tipA = tipMid + perp * tipHalf
            val tipB = tipMid - perp * tipHalf

            // Osvicena (vypukla) hrana
            val a1 = base + dir * (len * 0.10f) + perp * (halfWidth * 1.00f)
            val a2 = base + dir * (len * 0.58f) + perp * (halfWidth * 0.82f)
            // Stinena (vyduta) hrana - zpatky od spicky k zakladne
            val b1 = base + dir * (len * 0.60f) - perp * (halfWidth * 0.58f)
            val b2 = base + dir * (len * 0.12f) - perp * (halfWidth * 0.76f)

            val body = Path().apply {
                moveTo(base.x, base.y)
                cubicTo(a1.x, a1.y, a2.x, a2.y, tipA.x, tipA.y)
                quadraticBezierTo(tipMid.x, tipMid.y, tipB.x, tipB.y)
                cubicTo(b1.x, b1.y, b2.x, b2.y, base.x, base.y)
                close()
            }

            drawPath(
                path = body,
                brush = Brush.linearGradient(
                    0.00f to GlowViolet.copy(alpha = (0.30f + 0.55f * glow) * litness),
                    0.22f to GlassMid.copy(alpha = 0.96f),
                    0.55f to GlassDeep.copy(alpha = 0.94f),
                    1.00f to GlassTip.copy(alpha = 0.80f),
                    start = base,
                    end = tipMid,
                ),
            )

            // Osvicena hrana - nejsilnejsi presvetleni, slabne ke spicce
            drawPath(
                path = Path().apply {
                    moveTo(base.x, base.y)
                    cubicTo(a1.x, a1.y, a2.x, a2.y, tipA.x, tipA.y)
                },
                brush = Brush.linearGradient(
                    0.00f to Color.White.copy(alpha = (0.20f + 0.50f * glow) * litness),
                    0.60f to Color.White.copy(alpha = 0.10f * litness),
                    1.00f to Color.Transparent,
                    start = base,
                    end = tipA,
                ),
                style = Stroke(width = strokeW * 1.3f),
            )

            // Stinena hrana - jen naznak, aby plátek nesplyval s pozadim u obrysu
            drawPath(
                path = Path().apply {
                    moveTo(tipB.x, tipB.y)
                    cubicTo(b1.x, b1.y, b2.x, b2.y, base.x, base.y)
                },
                brush = Brush.linearGradient(
                    0.00f to Color.Transparent,
                    1.00f to AccentLight.copy(alpha = 0.22f * glow * litness),
                    start = tipB,
                    end = base,
                ),
                style = Stroke(width = strokeW * 0.8f),
            )

            // Vnitrni podelny odlesk - "lesk na skle", posunuty k osvicene strane
            val s0 = base + dir * (len * 0.20f) + perp * (halfWidth * 0.26f)
            val sc = base + dir * (len * 0.52f) + perp * (halfWidth * 0.34f)
            val s1 = base + dir * (len * 0.80f) + perp * (halfWidth * 0.16f)
            drawPath(
                path = Path().apply {
                    moveTo(s0.x, s0.y)
                    quadraticBezierTo(sc.x, sc.y, s1.x, s1.y)
                },
                brush = Brush.linearGradient(
                    0.00f to Color.White.copy(alpha = 0.02f),
                    0.35f to Color.White.copy(alpha = (0.22f + 0.20f * glow) * litness),
                    1.00f to Color.Transparent,
                    start = s0,
                    end = s1,
                ),
                style = Stroke(width = strokeW * 1.1f),
            )
        }

        fun drawLayer(index: Int) {
            val layer = PETAL_LAYERS[index]
            val openness = if (indeterminate) {
                BloomSchedule.MIN_OPENNESS + (1f - BloomSchedule.MIN_OPENNESS) * bloomProgress
            } else {
                BloomSchedule.layerOpenness(bloomProgress, index, PETAL_LAYERS.size)
            }

            val len = maxRadius * layer.lengthFactor * openness
            // Zavreny kvet ma plátky relativne sirsi vuci delce - jinak by se poupe scvrklo
            // na drobnou hvezdicku misto kompaktniho pupenu.
            val halfWidth = len * layer.widthFactor * (1f + (1f - openness) * 0.9f)
            val curlOffset = len * layer.curl * openness

            for (i in 0 until layer.count) {
                val angle = (360f / layer.count) * i +
                    layer.angleOffsetDeg +
                    spinDeg * layer.spinFactor
                // Deterministicka variace delky - naprosto stejne dlouhe plátky vypadaji
                // strojove; par procent rozdilu udela dojem rostleho tvaru.
                val jitter = 1f + 0.12f * sin(i * 2.399f)
                drawPetal(angle, len * jitter, halfWidth, curlOffset, layer.litness)
            }
        }

        // 1) Ambientni zare v pozadi - zasadi kvet do prostoru, aby neplaval na plochem cerne.
        // Radius MUSI zustat v ramci canvasu: pri vetsim se gradient rezne o hranu Canvasu a
        // vznikne viditelny svetly ctverec kolem kvetu (drive 1.25x = presne tenhle artefakt).
        val ambientRadius = maxRadius * 0.98f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    GlowViolet.copy(alpha = 0.10f * glow + 0.03f),
                    GlowViolet.copy(alpha = 0.03f * glow),
                    Color.Transparent,
                ),
                center = center,
                radius = ambientRadius,
            ),
            radius = ambientRadius,
            center = center,
        )

        // 2) Zadni a stredni vrstva
        drawLayer(0)
        drawLayer(1)

        // 3) Zare jadra JESTE PRED predni vrstvou - viz doc komentar funkce
        val glowRadius = maxRadius * (0.18f + 0.42f * glow)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    CoreMagenta.copy(alpha = 0.58f * glow),
                    GlowViolet.copy(alpha = 0.44f * glow),
                    Color.Transparent,
                ),
                center = center,
                radius = glowRadius,
            ),
            radius = glowRadius,
            center = center,
        )

        // 4) Predni vrstva - tmava silueta proti zari
        drawLayer(2)

        // 5) Samotne jadro: protazene svitici poupe se sklennym odleskem
        val coreH = maxRadius * (0.13f + 0.20f * glow)
        scale(scaleX = 0.62f, scaleY = 1f, pivot = center) {
            drawCircle(
                brush = Brush.radialGradient(
                    0.00f to Color.White.copy(alpha = 0.96f * glow),
                    0.30f to CoreMagenta.copy(alpha = 0.90f * glow),
                    0.62f to GlowViolet.copy(alpha = 0.55f * glow),
                    1.00f to Color.Transparent,
                    center = center,
                    radius = coreH,
                ),
                radius = coreH,
                center = center,
            )
            // Prepalene horke jadro - maly tvrdy zdroj svetla. Bez nej je poupe jen mekka
            // skvrna, ktera pri vysokem jasu cte jako sediva, ne jako svitici hmota.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.98f * glow),
                        Color.White.copy(alpha = 0.55f * glow),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = coreH * 0.42f,
                ),
                radius = coreH * 0.42f,
                center = center,
            )
        }
        // Bodovy odlesk mimo stred - oko ho cte jako lesklou plochu skla. Musi byt maly a
        // ostry (mekky vetsi kruh na svetlem jadru zesedne a cte se jako smitko, ne odlesk).
        val specR = (coreH * 0.075f).coerceAtLeast(0.8f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.95f * glow), Color.Transparent),
                center = center + Offset(-coreH * 0.24f, -coreH * 0.36f),
                radius = specR * 2.2f,
            ),
            radius = specR * 2.2f,
            center = center + Offset(-coreH * 0.24f, -coreH * 0.36f),
        )

        // 6) Svetelne prasinky - drobny detail, ktery zabrani tomu, aby tvar pusobil "vystrizeny"
        val moteCount = 7
        for (i in 0 until moteCount) {
            val angle = Math.toRadians(
                (spinDeg * 0.22f + (360f / moteCount) * i + i * 13f).toDouble(),
            )
            val dist = maxRadius * (0.44f + 0.10f * (i % 5)) * (0.55f + 0.45f * bloomProgress)
            val twinkle = (0.35f + 0.65f * ((sin(moteWave + i * 0.9f) + 1f) / 2f)) * glow
            drawCircle(
                color = AccentLight.copy(alpha = 0.38f * twinkle),
                radius = (maxRadius * 0.008f).coerceAtLeast(1f),
                center = center + Offset(cos(angle).toFloat() * dist, sin(angle).toFloat() * dist),
            )
        }
    }
}
