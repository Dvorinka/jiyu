package com.haise.jiyu.update

import android.graphics.RuntimeShader
import com.haise.jiyu.util.report

/**
 * AGSL fragment shader pro animaci stahování aktualizace (viz [UpdateProgressOverlay]).
 *
 * ## Proč shader a ne Canvas
 * Předchozí verze animace kreslila drátěné těleso z primitiv Canvasu. Objemová záře se tam dá
 * jen předstírat vrstvením průhledných tvarů - a přesně to pruhuje na černém pozadí a působí
 * podomácku. Skutečné světlo se počítá per-pixel, což znamená shader. To je taky důvod, proč
 * appka má minSdk 33: [RuntimeShader] existuje od Androidu 13.
 *
 * ## Co se kreslí
 * Formování jádra (凝丹) z kultivačních románů: rozptýlená čchi je vtahována po spirálách
 * dovnitř, stlačuje se a zhušťuje v zářící jádro. Hustota pole i jeho teplota nesou postup
 * stahování - viz [CoreFormationSchedule], kde ta matematika žije a je pod testy.
 *
 * ## Co tady NENÍ testovatelné
 * AGSL se překládá až na GPU zařízení, takže o správnosti tohoto řetězce nic nedokáže říct ani
 * kompilátor Kotlinu, ani jednotkové testy. Proto [create] nikdy nevyhazuje - selhání překladu
 * na neznámém GPU nesmí shodit aktualizaci appky.
 */
internal object QiFieldShader {

    /**
     * @return přeložený shader, nebo null když ho GPU odmítlo. Volající si musí poradit
     *   bez něj (viz záložní vykreslení v [UpdateProgressOverlay]).
     */
    fun create(): RuntimeShader? = try {
        RuntimeShader(SRC)
    } catch (e: Exception) {
        e.report("update:qi-shader")
        null
    }

    // Pozn.: veskera matematika uvnitr bezi ve float a na half se prevadi az navratova
    // hodnota. Michani float/half v jednom vyrazu je nejcastejsi duvod, proc AGSL neprojde.
    private val SRC = """
        uniform float2 uSize;     // velikost plochy v px
        uniform float  uTime;     // cas v sekundach
        uniform float  uDensity;  // 0..1 zhusteni pole
        uniform float  uHeat;     // 0..1 teplota jadra
        uniform float  uFlashR;   // polomer razoveho prstence
        uniform float  uFlashA;   // kryti razoveho prstence
        uniform float3 uMist;     // barva mlhy
        uniform float3 uHot;      // barva zaru

        float hash(float2 p0) {
            float2 p = fract(p0 * float2(123.34, 456.21));
            p += dot(p, p + 45.32);
            return fract(p.x * p.y);
        }

        float vnoise(float2 p) {
            float2 i = floor(p);
            float2 f = fract(p);
            float2 u = f * f * (3.0 - 2.0 * f);
            float a = hash(i);
            float b = hash(i + float2(1.0, 0.0));
            float c = hash(i + float2(0.0, 1.0));
            float d = hash(i + float2(1.0, 1.0));
            return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
        }

        // Ctyri oktavy staci: pate uz na 260dp plose nikdo neuvidi a plati se za ne kazdy pixel.
        float fbm(float2 p0) {
            float2 p = p0;
            float sum = 0.0;
            float amp = 0.5;
            for (int i = 0; i < 4; i++) {
                sum += amp * vnoise(p);
                p *= 2.03;
                amp *= 0.5;
            }
            return sum;
        }

        half4 main(float2 fragCoord) {
            float2 center = 0.5 * uSize;
            float  unit   = 0.5 * min(uSize.x, uSize.y);
            float2 uv     = (fragCoord - center) / unit;

            float r   = length(uv);
            float ang = atan(uv.y, uv.x);

            // Diferencialni rotace - bliz k jadru rychleji, jako akrecni disk. Rovnomerne
            // otaceni cte jako roztocena textura, ne jako proudici latka.
            float swirl = uTime * 0.55 / (r * r + 0.22);
            float2 q = float2(cos(ang + swirl), sin(ang + swirl)) * r;

            float n = fbm(q * 3.1 + float2(0.0, uTime * 0.15));

            // Mlzna slupka se s hustotou stahuje dovnitr a zaroven zuzuje. Zamerne se
            // NESTAHNE uplne do jadra: zhustena latka ma kolem jadra zustat videt jako husta
            // slupka, jinak z finale zbyde hladky bily kotouc bez struktury.
            float shell = mix(0.80, 0.27, uDensity);
            float width = mix(0.34, 0.13, uDensity);
            float dr    = (r - shell) / width;
            float band  = exp(-dr * dr);
            float mist  = band * (0.25 + 0.95 * n);

            // Nasavaci vlakna - slabe radialni pruhy smerem k jadru. Mizi, jak se pole
            // zhustuje: kdyz uz je vsechno nasate, neni co nasavat.
            float lobes   = sin(ang * 6.0 + swirl * 0.6) * 0.5 + 0.5;
            float strands = pow(max(lobes, 0.0), 6.0) * band * (1.0 - uDensity) * 0.35;

            // Jadro: s hustotou roste, s teplotou se rozzhavuje.
            float coreR = mix(0.045, 0.19, uDensity);
            float core  = exp(-(r * r) / (coreR * coreR));
            float glow  = core * (0.35 + 1.6 * uHeat);

            float3 col = uMist * (mist + strands);
            col += mix(uMist, uHot, clamp(uHeat, 0.0, 1.0)) * glow;

            // Razovy prstenec po ztuhnuti jadra.
            float dring = (r - uFlashR) / 0.045;
            float ring  = exp(-dring * dring) * uFlashA;
            col += uHot * (ring * 0.85);

            // Mekke dosednuti na okraji, aby nebyla videt hrana ctverce.
            col *= 1.0 - smoothstep(0.72, 1.02, r);

            // Dither proti pruhovani gradientu na OLED cerne.
            col += float3((hash(fragCoord) - 0.5) / 220.0);

            float a = clamp(max(col.r, max(col.g, col.b)), 0.0, 1.0);
            return half4(half3(col * a), half(a));
        }
    """.trimIndent()
}
