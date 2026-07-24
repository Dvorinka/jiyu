package com.haise.jiyu.translate

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ověřuje zpětnou kompatibilitu JSON cache formátu pro nová pole "shape"/"type" -
 * starý záznam bez těchto polí se musí deserializovat na shape=null, bubbleType=SPEECH.
 */
class TranslatedBlockSerializationTest {

    @Test
    fun `old cache entry without shape or type fields deserializes with safe defaults`() {
        val oldFormatJson = JSONArray().put(
            JSONObject().apply {
                put("orig", "Hello")
                put("trans", "Ahoj")
                put("disp", "Ahoj")
                put("bg", -1)
                put("sfx", false)
                put("lc", 1)
                put("l", 0.1); put("t", 0.1); put("r", 0.5); put("b", 0.2)
            }
        ).toString()

        val blocks = deserializeForTest(oldFormatJson)

        assertEquals(1, blocks.size)
        assertNull(blocks[0].shape)
        assertEquals(BubbleType.SPEECH, blocks[0].bubbleType)
    }

    @Test
    fun `shape and type round-trip through serialize and deserialize`() {
        val original = TranslatedBlock(
            originalText = "Hi", translatedText = "Ahoj",
            leftF = 0.1f, topF = 0.1f, rightF = 0.5f, bottomF = 0.3f,
            shape = listOf(BubbleShapePoint(0.1f, 0.15f, 0.45f), BubbleShapePoint(0.3f, 0.12f, 0.48f)),
            bubbleType = BubbleType.SHOUT,
        )

        val json = serializeForTest(listOf(original))
        val roundTripped = deserializeForTest(json)

        assertEquals(1, roundTripped.size)
        assertEquals(BubbleType.SHOUT, roundTripped[0].bubbleType)
        assertEquals(2, roundTripped[0].shape!!.size)
        assertEquals(0.15f, roundTripped[0].shape!![0].leftF, 0.001f)
    }

    // Kopie formátu z TranslateRepository.serialize()/deserialize() - ty jsou private,
    // tenhle test ověřuje kontrakt JSON formátu, ne implementaci samotnou.
    private fun serializeForTest(blocks: List<TranslatedBlock>): String = JSONArray().also { arr ->
        blocks.forEach { b ->
            arr.put(JSONObject().apply {
                put("orig", b.originalText); put("trans", b.translatedText); put("disp", b.displayText)
                put("bg", b.bgColorArgb); put("sfx", b.isSfx); put("lc", b.lineCount); put("type", b.bubbleType.name)
                b.shape?.let { shape ->
                    put("shape", JSONArray().apply {
                        shape.forEach { p -> put(JSONArray().apply { put(p.yF.toDouble()); put(p.leftF.toDouble()); put(p.rightF.toDouble()) }) }
                    })
                }
                put("l", b.leftF.toDouble()); put("t", b.topF.toDouble()); put("r", b.rightF.toDouble()); put("b", b.bottomF.toDouble())
            })
        }
    }.toString()

    private fun deserializeForTest(json: String): List<TranslatedBlock> {
        val arr = JSONArray(json)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val translated = o.getString("trans")
            val shapeArr = o.optJSONArray("shape")
            val shape = if (shapeArr != null) List(shapeArr.length()) { j ->
                val p = shapeArr.getJSONArray(j)
                BubbleShapePoint(p.getDouble(0).toFloat(), p.getDouble(1).toFloat(), p.getDouble(2).toFloat())
            } else null
            TranslatedBlock(
                originalText = o.getString("orig"), translatedText = translated,
                leftF = o.getDouble("l").toFloat(), topF = o.getDouble("t").toFloat(),
                rightF = o.getDouble("r").toFloat(), bottomF = o.getDouble("b").toFloat(),
                displayText = o.optString("disp", translated),
                bgColorArgb = if (o.has("bg")) o.getInt("bg") else DEFAULT_BUBBLE_BG_ARGB,
                isSfx = o.optBoolean("sfx", false), lineCount = o.optInt("lc", 1),
                shape = shape,
                bubbleType = try { BubbleType.valueOf(o.optString("type", "SPEECH")) } catch (e: Exception) { BubbleType.SPEECH },
            )
        }
    }
}
