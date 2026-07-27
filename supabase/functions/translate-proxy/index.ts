import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const GROQ_API_KEY = Deno.env.get("GROQ_API_KEY") ?? "";
const GEMINI_API_KEY = Deno.env.get("GEMINI_API_KEY") ?? "";
const OPENROUTER_API_KEY = Deno.env.get("OPENROUTER_API_KEY") ?? "";
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

// Svévolná bezpečnostní pojistka proti runaway smyčce/zneužití, NE skutečný limit od
// Gemini/Groq/OpenRouteru - ty mají vlastní free-tier limity a samy odmítnou požadavek
// zdarma, appka na to nikdy nic neplatí. Zvednuto 2026-07-27 poté, co reálné dní (24. a
// 25. 7.) narazily na původních 500 000 znaků (508k/632k) - počet requestů (131/205) byl
// přitom hluboko pod tehdejším limitem 5000, takže znakový limit byl ten skutečný strop.
const DAILY_CHAR_LIMIT = 3_000_000;
const DAILY_REQUEST_LIMIT = 20_000;

const OPENROUTER_MODEL = "google/gemma-4-26b-a4b-it:free";

const NAME_HANDLING_INSTRUCTION =
  "For character names, place names, organizations, and named skills/techniques, use " +
  "the name commonly used in English translations of this work - both fan translations " +
  "and official English releases count as valid sources - regardless of what language " +
  "you are translating from or into. If no established English name is known for a " +
  "particular term, render it into English yourself rather than leaving it in the " +
  "original script or inventing a name in the target language. When an established " +
  "English name IS known, do not invent a different transliteration for it, do not " +
  "translate its literal meaning into the target language (a city whose name means " +
  '"storm" in the original language should stay as its established English name, not ' +
  "become the target-language word for storm), and do not substitute an alternate " +
  "localized name used in other language editions - official translations into other " +
  "languages sometimes rename things in ways that don't match what fans use, ignore " +
  "those. If the source text already contains the name written in Latin letters, keep " +
  "it exactly as written. Translate the same recurring name or term the same way every " +
  "time. ";

const CZECH_DECLENSION_INSTRUCTION =
  "Since the target language is Czech, decline every English-spelled name (character " +
  "names, place names, organizations) according to Czech grammatical case so the sentence " +
  "reads naturally - keep the Latin/English spelling of the name itself, but change its " +
  'ending to match the grammatical case, the way Czech naturally declines foreign proper ' +
  'nouns (example: "Frodo" becomes "Froda" in genitive/accusative, "Frodovi" in dative, ' +
  '"Frodův" as a possessive; "Naruto" becomes "Narutovi"/"Narutem"; "Sakura" becomes ' +
  '"Sakuru"/"Sakuře"/"Sakurou"), following Czech masculine/feminine noun patterns based on ' +
  "the character's gender. If declining a name would sound forced or ambiguous, rephrase " +
  "using a preposition instead of forcing an awkward ending - but do not skip declension " +
  "altogether, since leaving every name in the nominative case regardless of its role in " +
  "the sentence reads unnatural in Czech. ";

const HONORIFICS_AND_TONE_INSTRUCTION =
  "Drop Japanese/Korean/Chinese honorific suffixes (-san, -kun, -chan, -senpai, -sama, etc.) " +
  "unless they matter to the plot or characterization - render the relationship or respect " +
  "they imply naturally in the target language instead of leaving them attached by default, " +
  'except for a few terms fan translations conventionally keep as-is (e.g. "senpai", "-sama" ' +
  "in a reverent context) where dropping them would feel wrong to readers used to that " +
  "convention. Match the intensity and register of the original dialogue - if the source is " +
  "crude, vulgar, or profane, translate it with equivalent intensity instead of softening or " +
  "censoring language that isn't censored in the source. ";

const MANGA_BREVITY_INSTRUCTION =
  "Keep dialogue natural and concise the way people actually speak, the way it would appear " +
  "in a comic speech bubble - avoid overly formal, literal, or wordy phrasing that would feel " +
  "stiff or unnatural spoken aloud. ";

function systemPromptFor(mode: string, fromClause: string, target: string): string {
  const nameHandling = target.trim().toLowerCase() === "czech"
    ? NAME_HANDLING_INSTRUCTION + CZECH_DECLENSION_INSTRUCTION
    : NAME_HANDLING_INSTRUCTION;

  if (mode === "novel") {
    return (
      "You are a professional literary translator specializing in light novels. " +
      `Given a JSON array of paragraphs from a light novel chapter, return a JSON array of ${fromClause}${target} ` +
      "translations in exactly the same order, preserving tone, dialogue formatting and paragraph " +
      "structure. Translate idioms, jokes and cultural references naturally so the prose reads " +
      `fluently in ${target}, not word-for-word. ` +
      nameHandling +
      HONORIFICS_AND_TONE_INSTRUCTION +
      "Return ONLY the JSON array, no explanations, no markdown."
    );
  }
  return (
    "You are an experienced manga translator. Given a JSON array of manga text strings, " +
    `return a JSON array of ${fromClause}${target} translations in exactly the same order. ` +
    "Translate jokes, wordplay, slang, and cultural references naturally and idiomatically " +
    `so the result reads fluently in ${target}, rather than translating word-for-word. ` +
    nameHandling +
    HONORIFICS_AND_TONE_INSTRUCTION +
    MANGA_BREVITY_INSTRUCTION +
    "Return ONLY the JSON array, no explanations, no markdown."
  );
}

const CORS_HEADERS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

function json(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...CORS_HEADERS },
  });
}

async function checkQuota(charCount: number): Promise<{ allowed: boolean; errored: boolean }> {
  const supabase = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);
  const { data: allowed, error: quotaError } = await supabase.rpc(
    "increment_translate_usage",
    {
      p_chars: charCount,
      p_daily_char_limit: DAILY_CHAR_LIMIT,
      p_daily_request_limit: DAILY_REQUEST_LIMIT,
    },
  );
  if (quotaError) {
    console.error("quota rpc failed", quotaError);
    return { allowed: false, errored: true };
  }
  return { allowed: Boolean(allowed), errored: false };
}

async function handleGeminiApi(system: string, user: string, model: string): Promise<Response> {
  if (!GEMINI_API_KEY) {
    console.error("GEMINI_API_KEY secret není nastavený na tomto projektu");
    return json({ text: "" }, 500);
  }

  const geminiResp = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`,
    {
      method: "POST",
      headers: {
        "x-goog-api-key": GEMINI_API_KEY,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: system }] },
        contents: [{ role: "user", parts: [{ text: user }] }],
        generationConfig: {
          temperature: 0.2,
          maxOutputTokens: 8192,
          responseMimeType: "application/json",
        },
      }),
    },
  );

  if (!geminiResp.ok) {
    console.error("gemini call failed", geminiResp.status, await geminiResp.text());
    return json({ text: "" }, 200);
  }

  const data = await geminiResp.json();
  const parts = data?.candidates?.[0]?.content?.parts;
  const text: string = Array.isArray(parts)
    ? parts.map((p: { text?: string }) => p.text ?? "").join("")
    : "";
  return json({ text }, 200);
}

async function handleGroqApi(system: string, user: string): Promise<Response> {
  if (!GROQ_API_KEY) {
    console.error("GROQ_API_KEY secret není nastavený na tomto projektu");
    return json({ text: "" }, 500);
  }

  const groqResp = await fetch("https://api.groq.com/openai/v1/chat/completions", {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${GROQ_API_KEY}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      model: "llama-3.3-70b-versatile",
      temperature: 0.1,
      max_tokens: 4096,
      messages: [
        { role: "system", content: system },
        { role: "user", content: user },
      ],
    }),
  });

  if (!groqResp.ok) {
    console.error("groq chat call failed", groqResp.status, await groqResp.text());
    return json({ text: "" }, 200);
  }

  const data = await groqResp.json();
  const text: string = data?.choices?.[0]?.message?.content ?? "";
  return json({ text }, 200);
}

async function handleOpenRouterApi(system: string, user: string): Promise<Response> {
  if (!OPENROUTER_API_KEY) {
    console.error("OPENROUTER_API_KEY secret není nastavený na tomto projektu");
    return json({ text: "" }, 500);
  }

  const orResp = await fetch("https://openrouter.ai/api/v1/chat/completions", {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${OPENROUTER_API_KEY}`,
      "Content-Type": "application/json",
      "HTTP-Referer": "https://github.com/morg1z/jiyu",
      "X-Title": "Jiyu",
    },
    body: JSON.stringify({
      model: OPENROUTER_MODEL,
      temperature: 0.1,
      max_tokens: 4096,
      messages: [
        { role: "system", content: system },
        { role: "user", content: user },
      ],
      response_format: {
        type: "json_schema",
        json_schema: {
          name: "bubble_translation",
          strict: true,
          schema: {
            type: "object",
            additionalProperties: false,
            properties: {
              bubbles: {
                type: "array",
                items: {
                  type: "object",
                  additionalProperties: false,
                  properties: {
                    id: { type: "integer" },
                    original: { type: "string" },
                    translated: { type: "string" },
                    bubble_size_tag: { type: "string" },
                    is_sfx: { type: "boolean" },
                    syllable_breaks: { type: "string" },
                    notes: { type: "string" },
                  },
                  required: ["id", "translated"],
                },
              },
            },
            required: ["bubbles"],
          },
        },
      },
    }),
  });

  if (!orResp.ok) {
    console.error("openrouter call failed", orResp.status, await orResp.text());
    return json({ text: "" }, 200);
  }

  const data = await orResp.json();
  const text: string = data?.choices?.[0]?.message?.content ?? "";
  return json({ text }, 200);
}

async function handleGemini(payload: Record<string, unknown>): Promise<Response> {
  const system = typeof payload.system === "string" ? payload.system : "";
  const user = typeof payload.user === "string" ? payload.user : "";
  const model = typeof payload.model === "string" && payload.model.length > 0
    ? payload.model
    : "gemini-flash-latest";
  const provider = payload.provider === "groq"
    ? "groq"
    : payload.provider === "openrouter"
    ? "openrouter"
    : "gemini";

  if (!user) return json({ text: "" }, 200);

  const { allowed, errored } = await checkQuota(system.length + user.length);
  if (errored) return json({ text: "" }, 500);
  if (!allowed) return json({ text: "", error: "daily_quota_exceeded" }, 429);

  if (provider === "groq") return await handleGroqApi(system, user);
  if (provider === "openrouter") return await handleOpenRouterApi(system, user);
  return await handleGeminiApi(system, user, model);
}

async function callChatCompletion(provider: "groq" | "openrouter", system: string, userContent: string): Promise<string | null> {
  if (provider === "openrouter") {
    if (!OPENROUTER_API_KEY) {
      console.error("OPENROUTER_API_KEY secret není nastavený na tomto projektu");
      return null;
    }
    const resp = await fetch("https://openrouter.ai/api/v1/chat/completions", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${OPENROUTER_API_KEY}`,
        "Content-Type": "application/json",
        "HTTP-Referer": "https://github.com/morg1z/jiyu",
        "X-Title": "Jiyu",
      },
      body: JSON.stringify({
        model: OPENROUTER_MODEL,
        temperature: 0.1,
        max_tokens: 4096,
        messages: [
          { role: "system", content: system },
          { role: "user", content: userContent },
        ],
      }),
    });
    if (!resp.ok) {
      console.error("openrouter call failed", resp.status, await resp.text());
      return null;
    }
    const data = await resp.json();
    return data?.choices?.[0]?.message?.content ?? "";
  }

  if (!GROQ_API_KEY) {
    console.error("GROQ_API_KEY secret není nastavený na tomto projektu");
    return null;
  }
  const resp = await fetch("https://api.groq.com/openai/v1/chat/completions", {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${GROQ_API_KEY}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      model: "llama-3.3-70b-versatile",
      temperature: 0.1,
      max_tokens: 4096,
      messages: [
        { role: "system", content: system },
        { role: "user", content: userContent },
      ],
    }),
  });
  if (!resp.ok) {
    console.error("groq call failed", resp.status, await resp.text());
    return null;
  }
  const data = await resp.json();
  return data?.choices?.[0]?.message?.content ?? "";
}

async function handleGroq(payload: Record<string, unknown>, mode: "manga" | "novel"): Promise<Response> {
  const provider: "groq" | "openrouter" = payload.provider === "openrouter" ? "openrouter" : "groq";

  const texts: unknown = payload.texts;
  const targetLanguage: string = (payload.targetLanguage as string) ?? "Czech";
  const sourceLanguage: string = (payload.sourceLanguage as string) ?? "Auto";
  const glossary: Record<string, string> = (payload.glossary as Record<string, string>) ?? {};

  if (!Array.isArray(texts) || texts.length === 0) {
    return json({ translations: [] }, 200);
  }

  const charCount = texts.reduce(
    (sum: number, t: unknown) => sum + (typeof t === "string" ? t.length : 0),
    0,
  );

  const { allowed, errored } = await checkQuota(charCount);
  if (errored) return json({ translations: [] }, 500);
  if (!allowed) return json({ translations: [], error: "daily_quota_exceeded" }, 429);

  const fromClause = sourceLanguage && sourceLanguage !== "Auto" ? `from ${sourceLanguage} ` : "";
  const glossaryClause = Object.keys(glossary).length > 0
    ? "\n\nThe following terms MUST be translated exactly as specified below, with no " +
      "deviation, regardless of what the general translation style would otherwise suggest:\n" +
      Object.entries(glossary).map(([k, v]) => `- "${k}" → "${v}"`).join("\n")
    : "";

  const systemPrompt = systemPromptFor(mode, fromClause, targetLanguage) + glossaryClause;

  const content = await callChatCompletion(provider, systemPrompt, JSON.stringify(texts));
  if (content === null) return json({ translations: [] }, provider === "groq" ? 500 : 200);

  const cleaned = content.trim()
    .replace(/^```json/, "")
    .replace(/^```/, "")
    .replace(/```$/, "")
    .trim();

  let translations: unknown;
  try {
    translations = JSON.parse(cleaned);
    if (!Array.isArray(translations)) throw new Error("not an array");
  } catch {
    console.error(`failed to parse ${provider} response as JSON array`, cleaned);
    return json({ translations: [] }, 200);
  }

  return json({ translations }, 200);
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { headers: CORS_HEADERS });
  }

  try {
    const payload = await req.json().catch(() => null);
    if (!payload) return json({ translations: [] }, 400);

    if (payload.mode === "gemini") {
      return await handleGemini(payload);
    }

    const mode: "manga" | "novel" = payload.mode === "novel" ? "novel" : "manga";
    return await handleGroq(payload, mode);
  } catch (e) {
    console.error(e);
    return json({ translations: [] }, 500);
  }
});
