package com.haise.jiyu.update

import android.graphics.RuntimeShader
import com.haise.jiyu.util.report

/**
 * AGSL fragment shader pro animaci stahování aktualizace (viz [UpdateProgressOverlay]).
 *
 * ## Proč shader a ne Canvas
 * Objemové světlo se z primitiv Canvasu dá jen předstírat vrstvením průhledných tvarů - a právě
 * to na černém pozadí pruhuje a působí podomácku. Skutečné světlo se počítá per-pixel. To je
 * taky důvod, proč appka má minSdk 33: [RuntimeShader] existuje od Androidu 13.
 *
 * ## Co se kreslí
 * Meditující postava, která kultivuje: rozptýlená syrová čchi (fialová - drží barevnou identitu
 * appky) je vtahována po hedvábných stuhách do dolního tantienu, kde se zjemňuje a zhušťuje
 * v **zlaté jádro** - doslovný překlad 金丹 (Jindan), vrcholu tohohle stádia kultivace. Na těle
 * u tantienu svítí tenká **rudá pečeť**, od začátku slabě, sílí s teplem. Jak jádro sílí, čchi
 * stoupá páteří vzhůru (malý nebeský oběh) a nakonec se rozsvítí temeno. Při dokončení jádro
 * ztuhne a vyšle jednu zlatou rázovou vlnu.
 *
 * Tři barvy nesou tři různé věci, ne jen dekorace: fialová = syrová čchi, zlatá = zjemnělá/
 * zhuštěná čchi (roste s hustotou i teplem), červená = neměnná značka na těle.
 *
 * Postava je celá procedurální - 2D SDF složené z kuželů, úseček a kružnic přes smooth-min,
 * v zrcadlovém poloprostoru (`abs(x)`), aby se symetrické části počítaly jen jednou. Důvod
 * není úspora assetu, ale to, že jedině tak s ní světlo doopravdy interaguje: záře prosvítá
 * tělem nejsilněji u tantienu a obrys se rozsvěcuje podle vzdálenosti od jádra. Kdyby postava
 * byla obrázek, muselo by se tohle všechno fakovat.
 *
 * ## Co tady NENÍ testovatelné
 * AGSL se překládá až na GPU zařízení, takže o správnosti tohoto řetězce nic nedokáže říct ani
 * kompilátor Kotlinu, ani jednotkové testy. Proto [create] nikdy nevyhazuje - selhání překladu
 * na neznámém GPU nesmí shodit aktualizaci appky. Časování, které do shaderu vstupuje, žije
 * v [CoreFormationSchedule] právě proto, aby aspoň ono pod testy bylo.
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
        uniform float2 uSize;      // velikost plochy v px
        uniform float  uTime;      // sekundy od prvniho snimku
        uniform float  uDensity;   // 0..1 zhusteni pole
        uniform float  uHeat;      // 0..1 teplota jadra
        uniform float  uMeridian;  // 0..1 jak vysoko vystoupala cchi pateri
        uniform float  uFlashR;    // polomer razoveho prstence
        uniform float  uFlashA;    // kryti razoveho prstence
        uniform float3 uMist;      // syrova cchi - drzi barevnou identitu appky
        uniform float3 uGold;      // zjemnela cchi / zlate jadro (Jindan)
        uniform float3 uSeal;      // rudá pecet na tantienu

        // Tantien lezi pod stredem boxu, aby cela sedici postava vysla do ramu vycentrovane.
        const float DANTIAN_Y = -0.14;
        // Kam az saha pateřni kanal. Konci u zatylku, ne skrz hlavu - cara vedena skrz lebku
        // cte jako napichnuty drat, ne jako proudici cchi.
        const float SPINE_TOP = 0.40;
        // Polomer pecete na tantienu.
        const float SEAL_R = 0.135;

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

        float sdSeg(float2 p, float2 a, float2 b, float r) {
            float2 pa = p - a;
            float2 ba = b - a;
            float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
            return length(pa - ba * h) - r;
        }

        // Kuzel s linearne menicim se polomerem. Neni to presna SDF (vzdalenost je u strmych
        // prechodu podhodnocena), ale pro siluetu a smooth-min to staci.
        float sdCone(float2 p, float2 a, float2 b, float r1, float r2) {
            float2 pa = p - a;
            float2 ba = b - a;
            float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
            return length(pa - ba * h) - mix(r1, r2, h);
        }

        float sdCirc(float2 p, float2 c, float r) {
            return length(p - c) - r;
        }

        float smin(float a, float b, float k) {
            float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
            return mix(b, a, h) - k * h * (1.0 - h);
        }

        /**
         * Silueta sedici postavy. Pocatek souradnic je TANTIEN, y roste nahoru.
         *
         * Proporce vychazi z vysky hlavy jako jednotky: ~3 hlavy nad tantienem, ~1.5 pod nim,
         * ramena 2.4 hlavy, zkrizene nohy 4. Stihle, ne nafouknute.
         *
         * Vse se pocita v zrcadlovem poloprostoru (x = |x|), takze symetricke casti staci
         * napsat i spocitat jednou.
         */
        float figure(float2 p, float breath) {
            float ch = breath * 0.012;   // dech: hrudnik se lehce zvedne
            float2 m = float2(abs(p.x), p.y);

            // Zkrizene nohy - kolena zaoblena. Zuzujici se kuzel je to, co dela rozdil mezi
            // zkrizenyma nohama a polstarem.
            float d = sdCone(m, float2(0.0, -0.11), float2(0.385, -0.285), 0.135, 0.078);
            d = smin(d, sdSeg(m, float2(0.0, -0.315), float2(0.20, -0.315), 0.058), 0.05);

            // Trup - uzsi v pase, sirsi v hrudi
            d = smin(d, sdCone(m, float2(0.0, -0.12), float2(0.0, 0.285 + ch), 0.128, 0.148 + ch), 0.055);
            // Ramena - tenka, aby nedelala boule nad pazemi
            d = smin(d, sdSeg(m, float2(0.0, 0.288 + ch), float2(0.205, 0.288 + ch), 0.055), 0.04);

            // Krk a hlava. Viditelny krk je to, co dela z tvaru cloveka.
            d = smin(d, sdSeg(m, float2(0.0, 0.335 + ch), float2(0.0, 0.415 + ch), 0.036), 0.03);
            d = smin(d, sdCirc(m, float2(0.0, 0.505 + ch), 0.088), 0.032);
            // Drdol - levna, ale silna identita wuxia postavy
            d = smin(d, sdCirc(m, float2(0.0, 0.605 + ch), 0.040), 0.028);

            // Pace: lokty daleko od tela, aby mezi pazi a trupem vznikl dost velky negativni
            // prostor. Tenka skvira by se na 260dp zavrela a cetla by jako chyba vykresleni.
            d = smin(d, sdCone(m, float2(0.195, 0.275 + ch), float2(0.335, 0.02), 0.050, 0.044), 0.035);
            d = smin(d, sdCone(m, float2(0.335, 0.02), float2(0.095, -0.105), 0.044, 0.040), 0.035);
            // Ruce slozene v kline
            d = smin(d, sdSeg(m, float2(0.0, -0.105), float2(0.085, -0.105), 0.042), 0.032);

            return d;
        }

        // Jedna vrstva hedvabne stuhy: vlastni frekvence, rychlost a faze rotace, aby se
        // vice vrstev nikdy necetlo jako jeden jednoduchy prstenec.
        // Vraci (mist, lobe, band) - bez 'out' parametru, jehoz podpora v AGSL je nejista.
        float3 ribbonLayer(float r, float ang, float freq, float speed, float phase, float widthMul) {
            float swirl = uTime * speed / (r * r + 0.22) + phase;
            float2 q = float2(cos(ang + swirl), sin(ang + swirl)) * r;
            float n = fbm(q * 3.1 + float2(0.0, uTime * 0.15));

            float shell = mix(0.86, 0.48, uDensity);
            float width = mix(0.34, 0.19, uDensity) * widthMul;
            float dr    = (r - shell) / width;
            float band  = exp(-dr * dr);

            float lobes = sin(ang * freq + swirl * 0.6) * 0.5 + 0.5;
            float lobe  = pow(max(lobes, 0.0), 6.0) * band;
            return float3(band * (0.20 + 0.85 * n), lobe, band);
        }

        half4 main(float2 fragCoord) {
            float2 center = 0.5 * uSize;
            float  unit   = 0.5 * min(uSize.x, uSize.y);
            float2 uv = float2((fragCoord.x - center.x) / unit, -(fragCoord.y - center.y) / unit);
            float2 p  = float2(uv.x, uv.y - DANTIAN_Y);

            float r   = length(p);
            float ang = atan(p.y, p.x);
            float breath = 0.5 + 0.5 * sin(uTime * 1.4);

            // ── Dve vrstvy hedvabnych stuh, ruzna faze/rychlost/sirka ────────
            float3 r1 = ribbonLayer(r, ang, 6.0, 0.55, 0.0, 1.0);
            float3 r2 = ribbonLayer(r, ang, 4.0, -0.38, 2.1, 1.35);
            float mist    = r1.x + 0.6 * r2.x;
            float strands = (r1.y + 0.7 * r2.y) * (1.0 - uDensity) * 0.30;

            // Blize k jadru = zjemnelejsi cchi = presouva se do zlate. Gate hustotou, aby
            // rane snimky (mala hustota, jadro daleko) zustaly cistě fialove.
            float refine = clamp(uDensity * 1.3 - r * 0.55, 0.0, 1.0);
            float3 auraCol = mix(uMist, uGold, refine);

            // ── Postava ─────────────────────────────────────────────────────
            float d = figure(p, breath);
            float aa = 1.5 / unit;
            float inside = clamp(0.5 - d / aa, 0.0, 1.0);

            float3 warm = mix(uMist, uGold, clamp(uHeat, 0.0, 1.0));
            float3 col  = auraCol * (mist + strands);

            // Telo auru zastini. Bez toho postava neni pevna a vsechno splyne v jednu kasi.
            col *= 1.0 - inside * 0.93;

            // Vnitrni prosvit: svetlo z jadra prosakuje telem, nejvic u tantienu. Tohle je
            // ten detail, ktery rika "to svetlo je UVNITR nej" - drzet ho kratky, jinak se
            // pri plnem vykonu z postavy stane zarici skvrna a prave ve vyvrcholeni zmizi.
            float bleed = exp(-r / (0.09 + 0.13 * uDensity)) * (0.12 + 0.55 * uHeat);
            col += inside * warm * bleed;

            // Obrys: uzky pruh tesne UVNITR hrany, tlumeny vzdalenosti od jadra - svetlo
            // dopada na blizsi casti tela vic nez na vzdalene.
            float rimD = (d + 0.011) / 0.013;
            float rim  = exp(-rimD * rimD) * (0.50 + 0.75 * uHeat) * exp(-r * 0.55);
            col += mix(uMist, uGold, 0.5 * uHeat) * rim;

            // ── Jadro v tantienu - zlate, ne bile ───────────────────────────
            float coreR = 0.028 + 0.055 * uDensity;
            float core  = exp(-(r * r) / (coreR * coreR));
            float3 coreCol = mix(uMist, uGold, clamp(uHeat * 1.3, 0.0, 1.0));
            col += coreCol * (core * (0.40 + 1.55 * uHeat));

            // ── Pecet na tantienu: tenky vyryty prstenec, existuje od zacatku ─
            // Ostrejsi nez difuzni zare - ma cist jako vypalena znacka, ne jako mlha.
            float dseal = abs(r - SEAL_R) - 0.005;
            float seal  = exp(-max(dseal, 0.0) * 90.0 - max(-dseal, 0.0) * 40.0);
            seal *= 0.55 + 0.55 * uHeat;   // sili s teplem, ale nikdy uplne nezhasne
            col += uSeal * seal * 1.1;

            // ── Jiskry: hruby grid bodu, blika v case, vic jich s hustotou ───
            const float CELL = 26.0;
            float2 g  = floor(p * CELL);
            float sparkId = hash(g);
            float2 f2 = fract(p * CELL) - 0.5;
            float sparkD = length(f2);
            float isSpark = step(0.965, sparkId);
            float twinkle = 0.5 + 0.5 * sin(uTime * (3.0 + sparkId * 6.0) + sparkId * 40.0);
            float sparkFalloff = exp(-((r - 0.55) / 0.55) * ((r - 0.55) / 0.55));
            float spark = isSpark * exp(-sparkD * sparkD * 40.0) * twinkle * (0.25 + 0.9 * uDensity) * sparkFalloff;
            col += mix(uMist, uGold, 0.7) * spark * 0.9;

            // ── Maly nebesky obeh: cchi stoupa pateri ────────────────────────
            float dsp    = sdSeg(p, float2(0.0, 0.0), float2(0.0, SPINE_TOP), 0.0);
            float reveal = clamp((uMeridian * SPINE_TOP - p.y) / 0.06, 0.0, 1.0) * step(-0.01, p.y);
            float along  = clamp(p.y / SPINE_TOP, 0.0, 1.0);
            float spineD = dsp / 0.011;
            float mer    = exp(-spineD * spineD) * reveal * (0.5 + 0.5 * (1.0 - along)) * uMeridian;
            col += uGold * (mer * 0.9);

            // Temeni (bai-hui) se rozsviti, az cchi dorazi nahoru - to je pointa obehu.
            float crown = clamp((uMeridian - 0.78) / 0.22, 0.0, 1.0);
            float dcr   = length(float2(p.x, p.y - 0.50)) / 0.075;
            col += uGold * (exp(-dcr * dcr) * crown * 0.85);

            // ── Razova vlna po ztuhnuti - zlata ──────────────────────────────
            float dring = (r - uFlashR) / 0.045;
            col += uGold * (exp(-dring * dring) * uFlashA * 0.85);

            // Mekke dosednuti na okraji, aby nebyla videt hrana ctverce.
            col *= 1.0 - smoothstep(0.80, 1.05, length(uv));

            // Dither proti pruhovani gradientu na OLED cerne.
            col += float3((hash(fragCoord) - 0.5) / 220.0);

            float a = clamp(max(col.r, max(col.g, col.b)), 0.0, 1.0);
            return half4(half3(col * a), half(a));
        }
    """.trimIndent()
}
