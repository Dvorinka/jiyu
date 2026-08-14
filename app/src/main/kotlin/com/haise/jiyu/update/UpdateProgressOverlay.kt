package com.haise.jiyu.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush
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
                QiCore(
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

                // Vlasova linka presneho postupu - pole nese naladu, ale procenta se z nej
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
// Formování jádra
// ─────────────────────────────────────────────────────────────────────────────

/** Hustota, kolem které pole osciluje, dokud appka nezná velikost souboru. */
private const val INDETERMINATE_DENSITY = 0.34f

/** Rozkmit té oscilace. Malý - má to působit jako dýchání, ne jako kolísání postupu. */
private const val INDETERMINATE_SWING = 0.05f

/** Jak dlouho trvá rázová vlna po ztuhnutí jádra. */
private const val FLASH_MILLIS = 520

/**
 * Formování jádra (凝丹) řízené postupem stahování [progress] (0f–1f): rozptýlená čchi je
 * vtahována po spirálách dovnitř, stlačuje se a zhušťuje v zářící jádro. Při dokončení jádro
 * ztuhne a vyšle jednu rázovou vlnu.
 *
 * Postup nesou dva nezávislé kanály, hustota a teplota - viz [CoreFormationSchedule].
 *
 * Záporná hodnota [progress] = neurčitý postup (appka ještě nezná velikost souboru): pole
 * zůstane rozptýlené a jen mírně dýchá. Zhušťovat ho by bylo lhaní o postupu, který neznáme.
 */
@Composable
private fun QiCore(progress: Float, modifier: Modifier = Modifier) {
    val indeterminate = progress < 0f

    // Preklad AGSL probehne az na GPU, takze tohle je jedine misto, kde se muze nepovest.
    // remember: prekladat shader pri kazde rekompozici by byla cista ztrata.
    val shader = remember { QiFieldShader.create() }

    // Cas se pocita od PRVNIHO snimku overlaye, ne z absolutni hodnoty animacnich hodin.
    // Ta je odvozena od doby behu systemu a na zarizeni beziciho tyden dosahuje milionu
    // sekund - ve float by na takove hodnote zbyla presnost kolem ctvrt sekundy a animace
    // by viditelne trhala. Zalomeni casu by zase udelalo skok uprostred pohybu.
    var timeSec by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var startMs = -1L
        while (true) {
            withInfiniteAnimationFrameMillis { ms ->
                if (startMs < 0L) startMs = ms
                timeSec = (ms - startMs) / 1000f
            }
        }
    }

    // Postup se do pole promita zmekcene - skok o deset procent najednou (server posle vetsi
    // kus najednou) by jinak poskocil i vizualne.
    val eased by animateFloatAsState(
        targetValue = if (indeterminate) 0f else progress.coerceIn(0f, 1f),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "progress",
    )

    // Zablesk se rozbehne prave jednou, ve chvili dokonceni. Startuje na 1f = uz doznely,
    // aby pri prvnim slozeni nebliknul.
    var flashT by remember { mutableFloatStateOf(1f) }
    val done = !indeterminate && progress >= 1f
    LaunchedEffect(done) {
        if (done) {
            animate(0f, 1f, animationSpec = tween(FLASH_MILLIS, easing = LinearEasing)) { v, _ ->
                flashT = v
            }
        }
    }

    val density = if (indeterminate) {
        INDETERMINATE_DENSITY + INDETERMINATE_SWING * sin(timeSec * 0.9f)
    } else {
        CoreFormationSchedule.density(eased)
    }
    val heat = if (indeterminate) 0f else CoreFormationSchedule.heat(eased)

    Canvas(modifier) {
        val s = shader
        if (s == null) {
            // GPU shader odmitlo. Nekreslit nic by vypadalo jako rozbita obrazovka, takze
            // aspon jadro z bezneho gradientu - zadna mlha, zadna turbulence.
            val radius = min(size.width, size.height) * (0.10f + 0.16f * density)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AccentLight.copy(alpha = 0.85f),
                        GlowViolet.copy(alpha = 0.35f),
                        GlowViolet.copy(alpha = 0f),
                    ),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = radius * 2.2f,
                ),
                radius = radius * 2.2f,
                center = Offset(size.width / 2f, size.height / 2f),
            )
            return@Canvas
        }

        s.setFloatUniform("uSize", size.width, size.height)
        s.setFloatUniform("uTime", timeSec)
        s.setFloatUniform("uDensity", density.coerceIn(0f, 1f))
        s.setFloatUniform("uHeat", heat)
        s.setFloatUniform("uFlashR", CoreFormationSchedule.flashRadius(flashT))
        s.setFloatUniform("uFlashA", CoreFormationSchedule.flashAlpha(flashT))
        // Fialova mlha appky (#7C5CFC) a bily zar s jemnym fialovym nadechem. Kanon by chtel
        // zlate jadro, ale zlata by se tloukla s celou appkou - soudrznost prebiji trop.
        s.setFloatUniform("uMist", 0.486f, 0.361f, 0.988f)
        s.setFloatUniform("uHot", 1f, 0.94f, 1f)

        drawRect(brush = ShaderBrush(s))
    }
}
