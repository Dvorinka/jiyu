# Jiyū — Bubble Text Overflow Analysis

## 1. Issue

Translated text in the lower-right bubble renders inside a solid white rectangle that spills well past the actual bubble outline, covering the sunset background and the tail of the speech bubble. The overlay is clipped with `RoundedCornerShape(3.dp)` instead of the true bubble contour, and its width is far larger than the original OCR text box.

## 2. Affected Components

- `com.haise.jiyu.translate.TranslationLayout`
- `com.haise.jiyu.translate.BubbleShapeDetector`
- `com.haise.jiyu.ui.reader.TranslationOverlay` (render path)
- `com.haise.jiyu.ui.reader.BubbleOverlayLayer`
- `com.haise.jiyu.translate.BubbleTextFit`

## 3. Root Cause

`TranslationLayout.layoutHeuristic` is the fallback used when `BubbleShapeDetector` returns `null` and `TranslatedBlock.shape` is missing. The function is supposed to grow a uniform bubble at most by a factor of `expandFactor` (3× for `bgUniform == true`).

The implementation computes `halfWidth` as:

```kotlin
val halfWidth = minOf(center - expandLimitLeft, expandLimitRight - center)
    .coerceAtLeast((b.rightF - b.leftF) / 2f)
```

It never enforces `ownWidth * expandFactor / 2`. For a block in the middle of the page with no horizontally adjacent neighbor, `expandLimitLeft` becomes `0f` and `expandLimitRight` becomes `1f`, so `halfWidth` is the distance from the block center to the nearest page edge — effectively full-page width. The final overlay can therefore be **3–7× (or more)** the OCR box, producing the white spill seen in the screenshot.

In addition, `MAX_SHAPE_TO_TEXT_AREA_RATIO == 30` in `BubbleShapeDetector` can reject legitimate speech bubbles that have long tails or that contain short text inside a large bubble, which forces even more cases into the broken heuristic fallback.

## 4. Why Shape Detection Likely Failed

The bubble in the screenshot is a clean white speech bubble on a non-uniform sunset background. It should be a near-ideal candidate for `BubbleShapeDetector.floodFill`. The most likely reasons it still returned `null`:

- The bubble contains a tail or is larger than `30 × textAreaPx`, causing `MAX_SHAPE_TO_TEXT_AREA_RATIO` to reject it.
- The background sample ring used for `bgColorArgb` may include anti-aliased outline pixels, making `bgColorArgb` too dark for the white interior to match `colorDistanceThreshold = 40`.
- Flood-fill leakage through a low-contrast anti-aliased border can exceed `maxAreaFraction = 0.25f` and trigger the safe null fallback.

## 5. Proposed Fixes

### 5.1 Immediate — cap `layoutHeuristic` expansion

In `TranslationLayout.kt`, bound `halfWidth` by `ownWidth * expandFactor / 2f` so the documented 3× (or 1.15×) cap is actually enforced.

```kotlin
val halfWidth = minOf(
    center - expandLimitLeft,
    expandLimitRight - center,
    ownWidth * expandFactor / 2f,
).coerceAtLeast(ownWidth / 2f)
```

This alone prevents the full-page horizontal spill shown in the screenshot.

### 5.2 Short-term — tighten shape detection, reduce fallback pressure

1. Raise `MAX_SHAPE_TO_TEXT_AREA_RATIO` cautiously (e.g. 30 → 45) and gate it on a compactness metric such as `filledArea / boundsArea < 0.2` to keep watermark strips rejected while accepting speech-bubble tails.
2. Lower `colorDistanceThreshold` from `40` to `30` for images with high-contrast black outlines, or make it adaptive per-page.
3. Add a small proximity margin to `verticallyOverlaps`/`horizontallyOverlaps` in `layoutHeuristic` so bubbles that are merely near each other are treated as obstacles, not just strictly overlapping ones.

### 5.3 Long-term — fall back to an edge-aware rectangle

When `shape == null` and `bgUniform == true`, cast rays from the OCR box outward using `bgColorArgb` and stop at the first color discontinuity. This produces a conservative rectangular clip that follows the visible bubble boundary much better than `RoundedCornerShape(3.dp)` and avoids spilling into art.

## 6. Implemented long-term fix

The ray-cast fallback is implemented in [BubbleShapeDetector.edgeAwareShape] and is invoked from `OcrEngine` whenever `detectShape` returns `null` and the background is uniform (`bgUniform == true`). It casts rays from the OCR box outward, stops at the first color discontinuity (the bubble outline / page art), and returns a rectangular `BubbleShapePoint` list. `TranslationLayout` then treats it as a real shape, so the overlay is clipped to the bubble instead of being a 3× heuristic box.

`EdgeAwareShapeTest` and `LayoutHeuristicTest` cover the new code. The full `:app:testDebugUnitTest` suite passes.

## 7. Visual comparison

`/tmp/jiyu_bubble_fix_visual.png` shows a synthetic side-by-side demo:

- **Left**: the 3× heuristic fallback still spills below the bubble.
- **Right**: the edge-aware fallback is clipped to the actual bubble boundary and does not cover the background.

Full instrumented verification on an emulator was skipped this session because the available system memory is low and the emulator keeps dying.
