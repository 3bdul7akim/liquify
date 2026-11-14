/*
 * The rounded-rectangle signed distance field, its gradient and the refraction/highlight programs
 * in this file are derived from Kyant0's AndroidLiquidGlass:
 *
 *     https://github.com/Kyant0/AndroidLiquidGlass
 *     Copyright 2025 Kyant — Licensed under the Apache License, Version 2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */

package com.hakim.liquify.internal

import org.intellij.lang.annotations.Language

/**
 * Signed distance field of a rounded rectangle with four independent corner radii, plus its
 * gradient. `radii` is ordered top-left, top-right, bottom-right, bottom-left.
 */
@Language("AGSL")
internal const val ROUNDED_RECT_SDF: String = """
float radiusAt(float2 coord, float4 radii) {
    // Branchless. As nested ifs this was two divergent branches on every pixel of every field
    // evaluation — and the merge pass evaluates the field five times per pixel per member, so a
    // quad straddling either centre line paid for both sides of both branches. `step` and `mix`
    // compile to selects, which have no such cost.
    float2 pair = mix(radii.xw, radii.yz, step(0.0, coord.x));
    return mix(pair.y, pair.x, step(0.0, -coord.y));
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}"""

/**
 * The colour work of the effects in front of a program — vibrancy, tint, opacity — folded into it
 * instead of running as a render pass of its own. See `BaseBackdropEffectScope.pendingColor`.
 *
 * The result is a `ColorMatrixColorFilter`'s — unpremultiply, map, clamp, premultiply again — but it
 * is worked out on the premultiplied colour directly. Every matrix those effects build leaves alpha
 * alone and never lets a colour channel read it (see `ColorMatrices.toShaderUniforms`); for such a
 * matrix `clamp(M·c/a + t, 0, 1)·a` is exactly `clamp(M·c + t·a, 0, a)`. That drops the divide, the
 * branch on alpha that guarded it and the alpha row and column of the matrix — and it runs on every
 * pixel of every pane, the deep interior included.
 *
 * Half precision throughout: colour leaves at eight bits, which fp16 carries with room to spare, and
 * most mobile GPUs run fp16 at twice the rate of fp32 with half the registers.
 */
@Language("AGSL")
internal const val COLOR_MATRIX: String = """
// The colour rows' linear part, column-major, and their translation in 0..1.
uniform half3x3 colorMatrix;
uniform half3 colorOffset;
// 0 when there is no colour work to do. A uniform, so the branch below is coherent across the draw
// and glass without any colour effect pays nothing for this.
uniform half colorFiltered;

half4 filtered(half4 color) {
    if (colorFiltered == 0.0) {
        return color;
    }
    half3 rgb = clamp(colorMatrix * color.rgb + colorOffset * color.a, half3(0.0), half3(color.a));
    return half4(rgb, color.a);
}"""

/**
 * The pane's own colour, laid over the refracted backdrop by the lens instead of by a draw of its own:
 * that draw blended the whole pane a second time on every frame, behind a clip. Unpremultiplied as
 * set, so a transparent fill leaves the colour as it is — no branch, and nothing to switch off.
 */
@Language("AGSL")
internal const val FILL: String = """
layout(color) uniform half4 fill;

half4 withFill(half4 color) {
    return half4(fill.rgb * fill.a, fill.a) + color * (1.0 - fill.a);
}"""

/**
 * A small blur taken in by the pass that samples the backdrop anyway: nine bilinear taps on a 3×3
 * grid `blurSpacing` apart, weighted 1-2-1 along each axis. Zero spacing — no blur to take in — is
 * one tap, and a uniform, so that branch is coherent across the draw.
 *
 * Nine is what the look needs. The fetches are most of what a pane costs the GPU — measured on an
 * Adreno 642L, one tap instead of nine took two milliseconds off a frame — but five taps spread just
 * as far, on the diagonals, left visible copies of every glyph behind the glass.
 *
 * Run as an effect of its own, a blur is two more render passes for every pane: the layer drawn into
 * a texture, blurred along one axis into a second and along the other into a third, before the lens
 * reads it. On a page with a dozen panes that was most of what the glass cost. The light blur of a
 * control — a couple of dp, under a fill most of the way to opaque — is as well served by nine taps
 * here. See `BaseBackdropEffectScope.foldedBlur` for which blurs come this way.
 *
 * Needs `uniform shader content` declared before it.
 */
@Language("AGSL")
internal const val BLUR_TAPS_FUNCTION: String = """
uniform float blurSpacing;

half4 backdropAt(float2 coord) {
    if (blurSpacing <= 0.0) {
        return content.eval(coord);
    }
    float s = blurSpacing;
    half4 sum = content.eval(coord) * 4.0;
    sum += (content.eval(coord + float2(s, 0.0)) + content.eval(coord - float2(s, 0.0)) +
        content.eval(coord + float2(0.0, s)) + content.eval(coord - float2(0.0, s))) * 2.0;
    sum += content.eval(coord + float2(s, s)) + content.eval(coord - float2(s, s)) +
        content.eval(coord + float2(s, -s)) + content.eval(coord + float2(-s, s));
    return sum * (1.0 / 16.0);
}"""

/** Edge refraction: bends the backdrop inside a band of `refractionHeight` around the border. */
@Language("AGSL")
internal const val REFRACTION_SHADER: String = """
uniform shader content;
$BLUR_TAPS_FUNCTION

uniform float2 halfSize;
// `offset - halfSize`, folded on the CPU. Centring the coordinate was an add and a subtract on
// every pixel of the layer to arrive at a translation that is the same for all of them.
uniform float2 centerOffset;
// min(halfSize.x, halfSize.y), also folded. It bounded the gradient radius on every pixel of the
// band, again for a value that does not vary across the draw.
uniform float halfMinSize;
uniform float4 cornerRadii;
// refractionHeight plus the largest of the corner radii, folded on the CPU; see the interior test.
uniform float interiorDepth;
uniform float refractionHeight;
// 1 / refractionHeight, folded on the CPU. Every pixel inside the band divided by it to arrive at
// a value that is the same for all of them; a reciprocal multiply is several times cheaper.
uniform float refractionHeightInverse;
uniform float refractionAmount;
uniform float depthEffect;

$COLOR_MATRIX
$FILL

$ROUNDED_RECT_SDF

half4 refracted(float2 coord) {
    float2 centeredCoord = coord + centerOffset;

    // Conservative interior test, before the field itself. Rounding only ever pulls the boundary
    // inward, and never by more than the largest corner radius, so a pixel this far inside the
    // axis-aligned bounds is certainly past the band. A handful of ALU ops here let every
    // deep-interior pixel skip the SDF's length() — on a large pane that is nearly all of them.
    //
    // Measured against the largest radius, folded into interiorDepth on the CPU, rather than this
    // quadrant's: the test then needs no radius at all, and the lookup it saves ran on every pixel.
    // A pixel it lets through that the quadrant's own radius would have caught leaves at the next
    // test instead, with the same result.
    float2 inset = halfSize - abs(centeredCoord);
    if (min(inset.x, inset.y) >= interiorDepth) {
        return filtered(backdropAt(coord));
    }

    float radius = radiusAt(centeredCoord, cornerRadii);
    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return filtered(backdropAt(coord));
    }
    sd = min(sd, 0.0);

    // sd is negative here, so 1 - (-sd)/h is 1 + sd * (1/h) — the same value without the divide.
    float t = 1.0 + sd * refractionHeightInverse;
    float d = circleMap(t) * refractionAmount;
    float gradRadius = min(radius * 1.5, halfMinSize);
    // gradSdRoundedRect already returns a unit vector — both of its branches do — so without a
    // depth term the outer normalize() is a no-op and the inner one is computed only to be
    // multiplied by zero. depthEffect is a uniform, so this branch is coherent across the entire
    // draw: the GPU takes one side, not both.
    float2 normal = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    float2 grad = normal;
    if (depthEffect != 0.0) {
        grad = normalize(normal + depthEffect * normalize(centeredCoord));
    }

    return filtered(backdropAt(coord + d * grad));
}

half4 main(float2 coord) {
    return withFill(refracted(coord));
}"""

/**
 * The seven taps across the spectrum around one refracted point — the fringe of chromatic
 * aberration, shared by a single pane's lens and a merged body's so the two fringe alike. Needs
 * `uniform shader content`, [BLUR_TAPS_FUNCTION]'s `backdropAt()` and [COLOR_MATRIX]'s `filtered()`
 * declared before it.
 *
 * A small blur taken in by the lens reaches the fringe through its middle tap and wherever the
 * fringe is too small to see: blurring all seven would be sixty-three fetches per pixel of the band,
 * and the outer taps only lend their colour to an edge the lens is bending anyway.
 */
@Language("AGSL")
internal const val DISPERSION_FUNCTION: String = """
// The seven taps across the spectrum around one refracted point.
half4 dispersed(float2 refractedCoord, float2 dispersedCoord) {
    // The fringe is a product of the bend and of x·y, so it vanishes along both centre lines and
    // towards the inner edge of the band. Where the seven taps would all land within a tenth of a
    // pixel of each other they read the same colour, and the weights below add up to exactly that
    // colour — so one fetch gives what seven did.
    if (dot(dispersedCoord, dispersedCoord) < 0.01) {
        return filtered(backdropAt(refractedCoord));
    }

    half4 color = half4(0.0);

    // Each tap is filtered on its own, as the colour pass in front of this program used to see it:
    // the channels below are picked from different taps, which a matrix applied to the sum would
    // mix up.
    half4 red = filtered(content.eval(refractedCoord + dispersedCoord));
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;

    half4 orange = filtered(content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0)));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;

    half4 yellow = filtered(content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0)));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;

    half4 green = filtered(backdropAt(refractedCoord));
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;

    half4 cyan = filtered(content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0)));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;

    half4 blue = filtered(content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0)));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;

    half4 purple = filtered(content.eval(refractedCoord - dispersedCoord));
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;

    return color;
}"""

/**
 * Edge refraction with chromatic aberration: the red end of the spectrum is refracted slightly
 * more than the blue end, which is what makes a real lens fringe at its rim.
 */
@Language("AGSL")
internal const val REFRACTION_DISPERSION_SHADER: String = """
uniform shader content;
$BLUR_TAPS_FUNCTION

uniform float2 halfSize;
// See the plain refraction program: both of these are folded on the CPU.
uniform float2 centerOffset;
uniform float halfMinSize;
uniform float4 cornerRadii;
// As in the plain refraction program: refractionHeight plus the largest corner radius.
uniform float interiorDepth;
uniform float refractionHeight;
// 1 / refractionHeight, folded on the CPU. Every pixel inside the band divided by it to arrive at
// a value that is the same for all of them; a reciprocal multiply is several times cheaper.
uniform float refractionHeightInverse;
uniform float refractionAmount;
uniform float depthEffect;
// Fringe strength per unit of x*y, also folded on the CPU. This was a division by the element's
// half area on every pixel of the band, by a quantity that does not vary across the draw.
uniform float dispersionScale;

$COLOR_MATRIX
$FILL

$ROUNDED_RECT_SDF
$DISPERSION_FUNCTION

half4 refracted(float2 coord) {
    float2 centeredCoord = coord + centerOffset;

    // Conservative interior test, before the field itself. Rounding only ever pulls the boundary
    // inward, and never by more than the largest corner radius, so a pixel this far inside the
    // axis-aligned bounds is certainly past the band. A handful of ALU ops here let every
    // deep-interior pixel skip the SDF's length() — on a large pane that is nearly all of them.
    //
    // Measured against the largest radius, folded into interiorDepth on the CPU, rather than this
    // quadrant's: the test then needs no radius at all, and the lookup it saves ran on every pixel.
    // A pixel it lets through that the quadrant's own radius would have caught leaves at the next
    // test instead, with the same result.
    float2 inset = halfSize - abs(centeredCoord);
    if (min(inset.x, inset.y) >= interiorDepth) {
        return filtered(backdropAt(coord));
    }

    float radius = radiusAt(centeredCoord, cornerRadii);
    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return filtered(backdropAt(coord));
    }
    sd = min(sd, 0.0);

    // sd is negative here, so 1 - (-sd)/h is 1 + sd * (1/h) — the same value without the divide.
    float t = 1.0 + sd * refractionHeightInverse;
    float d = circleMap(t) * refractionAmount;
    float gradRadius = min(radius * 1.5, halfMinSize);
    // gradSdRoundedRect already returns a unit vector — both of its branches do — so without a
    // depth term the outer normalize() is a no-op and the inner one is computed only to be
    // multiplied by zero. depthEffect is a uniform, so this branch is coherent across the entire
    // draw: the GPU takes one side, not both.
    float2 normal = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    float2 grad = normal;
    if (depthEffect != 0.0) {
        grad = normalize(normal + depthEffect * normalize(centeredCoord));
    }

    float dispersionIntensity = dispersionScale * (centeredCoord.x * centeredCoord.y);

    return dispersed(coord + d * grad, d * grad * dispersionIntensity);
}

half4 main(float2 coord) {
    return withFill(refracted(coord));
}"""

/** Directional rim light: brightest where the border normal faces `angle`. */
@Language("AGSL")
internal const val HIGHLIGHT_DEFAULT_SHADER: String = """
uniform float2 halfSize;
// min(halfSize.x, halfSize.y), folded on the CPU: it bounded the gradient radius on every pixel of
// the element for a value that is the same across the draw.
uniform float halfMinSize;
uniform float4 cornerRadii;
layout(color) uniform half4 color;
// Precomputed on the CPU. As an angle this cost a cos() and a sin() for every pixel of the
// element, on every frame, to arrive at a value that is the same for all of them.
uniform float2 lightNormal;
uniform float falloff;

$ROUNDED_RECT_SDF

half4 main(float2 coord) {
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);

    float gradRadius = min(radius * 1.5, halfMinSize);
    float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    // pow() is among the most expensive things a fragment can do, and the default falloff of 1
    // makes it the identity. The exponent is a uniform, so this branch is coherent across the
    // whole draw — the GPU takes one side of it, not both.
    float aligned = abs(dot(grad, lightNormal));
    float intensity = falloff == 1.0 ? aligned : pow(aligned, falloff);
    return color * intensity;
}"""

/** Rim light that goes white on the lit side and black on the unlit side. */
@Language("AGSL")
internal const val HIGHLIGHT_AMBIENT_SHADER: String = """
uniform float2 halfSize;
// Folded on the CPU, as in the directional rim.
uniform float halfMinSize;
uniform float4 cornerRadii;
// Precomputed on the CPU. As an angle this cost a cos() and a sin() for every pixel of the
// element, on every frame, to arrive at a value that is the same for all of them.
uniform float2 lightNormal;
uniform float falloff;

$ROUNDED_RECT_SDF

half4 main(float2 coord) {
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);

    float gradRadius = min(radius * 1.5, halfMinSize);
    float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    float d = dot(grad, lightNormal);
    // As in the directional rim: a falloff of 1 makes pow() the identity, and the exponent is a
    // uniform, so the branch costs nothing.
    float aligned = abs(d);
    float intensity = falloff == 1.0 ? aligned : pow(aligned, falloff);
    float t = step(0.0, d);
    return half4(t, t, t, 1.0) * intensity;
}"""

/**
 * A drawn edge rather than a reflection, divided twice over.
 *
 * **Across the border**: one band just inside the outline and one just outside it, each fading to
 * nothing as it leaves the border, and neither crossing to the other side.
 *
 * **Around the border**: which of the two tones each band carries alternates by quadrant, so the
 * lit face is the inner one on the leading diagonal and the outer one on the other. That is the
 * same light seen from the other side of the lip, and it is why the two diagonals agree.
 *
 * This is the only rim in the library that draws past the element, which is why it is also the only
 * one the decoration node lets straddle the outline instead of clipping the stroke to it.
 */
@Language("AGSL")
internal const val HIGHLIGHT_TWSE_SHADER: String = """
uniform float2 halfSize;
// Folded on the CPU, as in the directional rim.
uniform float halfMinSize;
uniform float4 cornerRadii;
// The two tones. Which side of the border each one sits on swaps from quadrant to quadrant — see
// the diagonal factor below.
layout(color) uniform half4 color;
layout(color) uniform half4 inverseColor;
// Precomputed on the CPU, as in the directional rim.
uniform float2 lightNormal;
uniform float falloff;
// How far each band reaches from the outline before it is fully transparent, in pixels.
uniform float innerWidth;
uniform float outerWidth;
// How much of the rim survives where the border faces away from the light.
uniform float ambient;
// Half-width of the crossfade between the two quadrant tones, per axis. Per axis because the sweep
// it controls runs along an edge, and the two edges of a card are rarely the same length.
uniform float2 seamBlend;

$ROUNDED_RECT_SDF

half4 main(float2 coord) {
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);
    // Negative inside the outline, positive outside it. This is the one rim that lives on both
    // sides of the border, so it is also the one that needs the field rather than just its gradient.
    float sd = sdRoundedRect(centeredCoord, halfSize, radius);

    // Which side of the outline this pixel is on, crossfaded across a single pixel. A hard split
    // would alias into a stepped line along a curve, and this is exactly where the two tones meet.
    float inside = 1.0 - smoothstep(-0.5, 0.5, sd);

    // Two bands, one on each side of the border, each at full strength against it and fading away
    // from it. Neither is allowed across to the other side: that division is what makes the pair
    // read as a raised lip rather than as one soft line straddling the edge.
    float innerBand = (1.0 - smoothstep(0.0, innerWidth, max(-sd, 0.0))) * inside;
    float outerBand = (1.0 - smoothstep(0.0, outerWidth, max(sd, 0.0))) * (1.0 - inside);

    float gradRadius = min(radius * 1.5, halfMinSize);
    float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    // pow() is among the most expensive things a fragment can do, and the default falloff of 1
    // makes it the identity. The exponent is a uniform, so the branch is coherent across the draw.
    float aligned = abs(dot(grad, lightNormal));
    float directional = falloff == 1.0 ? aligned : pow(aligned, falloff);
    // Never all the way down to nothing: a rim that disappears on two sides stops reading as an
    // edge and starts reading as a broken outline.
    float intensity = mix(directional, 1.0, ambient);

    // Which way round the two tones sit here: 1 on the leading diagonal, 0 on the other.
    // smoothstep rather than step, because the switch falls at the midpoint of an edge — exactly
    // where the rim is a single row of pixels, and a hard change would alias into a visible notch.
    //
    // The width of that crossfade is most of the look. Kept short it reads as four flat quadrants
    // with a hairline seam; spread across most of the half-edge — which is what an unspecified
    // seamBlend resolves to — it becomes one continuous sweep from the lit corner to the dark one,
    // and only the corners themselves settle on a pure arrangement.
    float sx = smoothstep(-seamBlend.x, seamBlend.x, centeredCoord.x);
    float sy = smoothstep(-seamBlend.y, seamBlend.y, centeredCoord.y);
    float onDiagonal = sx * sy + (1.0 - sx) * (1.0 - sy);

    // Premultiplied, so each tone's own alpha is the strength of its band rather than something
    // dropped between here and the layer.
    half4 lit = half4(color.rgb * color.a, color.a);
    half4 shade = half4(inverseColor.rgb * inverseColor.a, inverseColor.a);

    // The swap, and it is not decoration — it is the same light seen from the other side. Where the
    // border faces the light the inner face catches it and the body drops a hairline of shadow just
    // outside; on the opposite quadrants that reverses, the *outer* edge catches the light and the
    // inner face lies in shade. The diagonals therefore agree — top-left with bottom-right — so one
    // light appears to cross a raised edge rather than four unrelated arcs meeting at the corners.
    half onLeading = half(onDiagonal);
    half4 innerTone = mix(shade, lit, onLeading);
    half4 outerTone = mix(lit, shade, onLeading);

    // The two bands never overlap, so adding them is exactly compositing them.
    return (innerTone * half(innerBand) + outerTone * half(outerBand)) * half(intensity);
}"""
