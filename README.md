# Liquify

[![build](https://github.com/3bdul7akim/liquify/actions/workflows/build.yml/badge.svg)](https://github.com/3bdul7akim/liquify/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.3bdul7akim/liquify)](https://central.sonatype.com/artifact/io.github.3bdul7akim/liquify)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg)](https://developer.android.com/tools/releases/platforms)

**Liquid glass for Jetpack Compose** — real edge refraction, glass that merges and separates like
liquid, and one theme that adapts every pane to what the device and the user can afford.

```kotlin
val backdrop = rememberLayerBackdrop()

LiquifyTheme(backdrop = backdrop) {                                   // 1. declare the glass once
    Box(Modifier.fillMaxSize()) {
        Image(wallpaper, null, Modifier.fillMaxSize().layerBackdrop(backdrop))  // 2. what it refracts

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .liquify(Capsule(), dragging = true, interactiveHighlight = true)  // 3. glass
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Icon(Icons.Default.Favorite, null)
        }
    }
}
```

---

## Contents

- [Why Liquify](#why-liquify)
- [Install](#install)
- [How it fits together](#how-it-fits-together)
- [LiquifyTheme and looks](#liquifytheme-and-looks)
- [Modifier.liquify](#modifierliquify)
- [Materials](#materials)
- [Pressed states](#pressed-states)
- [Touch](#touch)
- [Merging — LiquidGlassGroup](#merging--liquidglassgroup)
- [Backdrops](#backdrops)
- [Custom effects](#custom-effects)
- [Rims and shadows](#rims-and-shadows)
- [Transitions](#transitions)
- [Progressive blur](#progressive-blur)
- [Performance](#performance)
- [Migrating from 1.x](#migrating-from-1x)
- [The catalog app](#the-catalog-app)
- [Credits](#credits)

---

## Why Liquify

- **One modifier.** `Modifier.liquify(shape)` is the whole API for a pane of glass. An element says
  what it *is* — a shape, maybe a colour, maybe how it reacts to the finger — and inherits the rest.
- **It adapts.** Inside a group it merges with its neighbours. Under a cheaper look it becomes
  blurred glass, or a plain shape that samples nothing. Its refraction is in proportion to its size.
  On older devices it does what the device can. No call site branches for any of it.
- **Merging.** Glass that fuses into one body when two elements approach and pinches apart as they
  separate, each member's colour flowing through the bridge into its neighbour's.
- **Declarative press states.** A thumb that is an opaque pill at rest and a clear lens while held
  is two materials — the library blends between them in the draw phase.
- **Fast by default.** Colour work, light blurs and the pane's own colour folded into the refraction
  pass, clips the renderer works out analytically, pooled shader programs, cached uniforms, and opaque
  panes and plain-coloured backdrops that skip the backdrop layer altogether.

---

## Install

```kotlin
dependencies {
    implementation("io.github.3bdul7akim:liquify:2.0.0")
}
```

Coming from 1.x? See [Migrating from 1.x](#migrating-from-1x).

**Requirements:** `minSdk 21`, Compose 1.11+, Kotlin 2.x. Effects light up progressively, and nothing
ever throws for an unsupported one — it is skipped:

| API level | What renders |
| --- | --- |
| 21–30 | Shape, tint, rim, drop shadow |
| 31+ (Android 12) | + blur, vibrancy, inner shadow, `materialize`, `motionBlur`, `progressiveBlur` |
| 33+ (Android 13) | + refraction, merging, shader rims, chromatic aberration, the touch glow's hotspot |

---

## How it fits together

Three levels, each inheriting from the one above it:

| Level | Declares | For |
| --- | --- | --- |
| [`LiquifyTheme`](#liquifytheme-and-looks) | backdrop, material, look, dark theme | everything inside it |
| [`LiquidGlassGroup`](#merging--liquidglassgroup) | a material for one merged body | its children, which fuse |
| [`Modifier.liquify`](#modifierliquify) | a shape, and only what differs | one pane |

An element resolves each property element → group → theme, so a screen declares its glass once and
its components name nothing but their shape.

---

## LiquifyTheme and looks

```kotlin
LiquifyTheme(
    backdrop = backdrop,                 // what glass refracts
    material = GlassMaterial.Regular,    // what it is made of
    look = GlassLook.Liquid,             // how much it may do
    darkTheme = isDark                   // what theme-dependent inks resolve against
) {
    App()
}
```

Every argument defaults to what is already in scope, so a nested `LiquifyTheme(look = GlassLook.Solid)`
changes one subtree and keeps the rest. Read the current values with `LiquifyTheme.material`,
`LiquifyTheme.backdrop`, `LiquifyTheme.look` and `LiquifyTheme.darkTheme`.

**Looks** are the one switch an app turns to trade appearance for GPU time — a low-power setting, a
"reduce transparency" option. Every pane adapts on its own:

| | `Liquid` | `Frosted` | `Solid` |
| --- | --- | --- | --- |
| Refraction, merging | yes | — | — |
| Touch glow, lean, stretch | yes | — | — |
| Pressed material | by touch or a `pressProgress` | by a `pressProgress` | — |
| Backdrop sampled and blurred | yes | yes | **no** |
| Tint, rim, shadows | yes | yes | yes |
| Layer transforms, layout motion | yes | yes | yes |

`Solid` records no layer, builds no effect chain and compiles no shader: the pane is its `tint`
drawn as a plain shape. Give it a dense colour — with nothing behind it, a translucent tint lets the
page show through. Remember to drop the `layerBackdrop` source as well: a full-screen layer nobody
samples is still re-recorded every frame.

---

## Modifier.liquify

```kotlin
Modifier.liquify(RoundedRectangle(28.dp))                              // inherits everything
Modifier.liquify(Capsule(), tint = accent.copy(alpha = 0.6f))          // a coloured pane
Modifier.liquify(CircleShape, dragging = true, interactiveHighlight = true)  // reacts to touch
Modifier.liquify(Capsule(), material = GlassMaterial.Clear)            // a different glass
```

The element's children draw on top of the glass, clipped to its outline: it is a background. Keep
anything that has to overhang the pane — a badge, a swelling thumb — outside the element. The clip is
the outline's rounded rectangle, which costs nothing: a continuous corner strays from the circular one
by at most 1.6 % of its radius, always to the inside, and the rim drawn over it follows the true
curve.

| Parameter | Default | |
| --- | --- | --- |
| `shape` | — | The outline. `RoundedRectangle` and `Capsule` give the continuous corners refraction is designed around; other shapes get no lens rather than a wrong one. |
| `tint` | material's | The colour of the glass, laid over what it refracts; alpha is the strength. In a group it is the member's colour, under `Solid` the pane itself. |
| `material` | inherited | The recipe — see [Materials](#materials). |
| `pressedMaterial`, `pressProgress` | none | A second recipe to blend towards while pressed — see [Pressed states](#pressed-states). |
| `backdrop` | inherited | Refract something else for this one element. |
| `dragging`, `stretching`, `interactiveHighlight`, `interaction` | off | See [Touch](#touch). |
| `layerBlock` | none | A transform, inverted for the backdrop so the refraction stays anchored while the element scales or turns. |
| `onDrawSurface` | none | Drawn over the glass and under the content, for what a tint cannot express. |
| `exportedBackdrop` | none | Records this pane into a `LayerBackdrop`, so another element can refract it. |
| `refractsContent` | `false` | Bends the content along with the backdrop, as if it lay behind the glass. Colour and blur stay with the backdrop, so text is bent, never blurred. |
| `effects` | material's | A hand-written recipe — see [Custom effects](#custom-effects). The rim and shadows still come from `material`. |
| `exactClip` | `false` | Clips the glass and the content to the true outline instead of its rounded rectangle — for content that fills the corners and must follow the continuous curve to the pixel. Costs a coverage mask for every draw inside the element, every frame. |

Animated values belong in the lambdas — `pressProgress`, `layerBlock`, `onDrawSurface`, `effects` —
which are read in the draw phase: motion costs a redraw, never a recomposition.

---

## Materials

A `GlassMaterial` is the whole recipe: what the pane does to the backdrop, and what it wears around
it.

```kotlin
GlassMaterial(
    blurRadius = 8.dp,              // how far the backdrop is blurred
    refractionHeight = 20.dp,       // thickness of the refracting band, at full size
    refractionAmount = 35.dp,       // how far the rim bends the backdrop, at full size
    saturation = 1.15f,             // vibrancy; 1f for none
    tint = Color.Unspecified,       // the glass's colour; alpha is the strength
    depthEffect = true,             // dome the pane into a lens
    chromaticAberration = false,    // coloured fringe at the rim — 7 samples per pixel
    rim = GlassRim.Default,         // the specular edge, or null
    shadow = GlassShadow.Default,   // the drop shadow, or null
    innerShadow = null,             // a recessed look
    refractionBySize = true,        // refract every element in proportion to its size
    refractionHeightScale = 1f,     // multiply the band — the knob for a user setting
    refractionAmountScale = 1f,     // multiply the bend
    motion = GlassMotion.Default    // how it moves under the finger
)
```

| Preset | Blur | Refraction | Saturation | Tint | Rim |
| --- | --- | --- | --- | --- | --- |
| `Regular` | 8 dp | 20 / 35 dp | 1.15 | none | `Default` |
| `Clear` | 0 | 20 / 35 dp | 1.0 | none | `Default` |
| `Thick` | 22 dp | 32 / 40 dp | 1.6 | white 1 % | `Default` |
| `TwSe` | 6 dp | 24 / 24 dp | 1.15 | none | `TwSe` |

Materials are values: `GlassMaterial.Regular.copy(shadow = null)` is unshadowed glass, and handing it
to `LiquifyTheme` makes every pane in the app unshadowed.

### Refraction that fits every size

One fixed band is only ever right for one size: 20 dp turns a 32 dp button into a lens with no flat
middle, and barely registers on a sheet. So by default every element is refracted in proportion to
its short side `s`, and eases into the material's values as it grows:

| | small elements | large panes |
| --- | --- | --- |
| band | `s / 4` | `refractionHeight` |
| bend | `s × 3/8` round, `s / 2` elongated (from 2 : 1) | `refractionAmount` |

The two are joined by a smooth minimum, so an element resizing across the corner never sees its
refraction kink. With `Regular`:

| Element | Size (dp) | Band / bend (dp) |
| --- | --- | --- |
| icon button | 32 × 32 | 8 / 12 |
| text button | 48 × 100 | 11.6 / 22.8 |
| search field | 56 × 360 | 13.3 / 25.7 |
| card | 120 × 360 | 19.1 / 34.1 |
| sheet | 400 × 800 | 20 / 35 |

Inside a group every member is refracted by its own size, blended across the bridges. Set
`refractionBySize = false` for exactly the material's values on every element.

---

## Pressed states

Controls that change their glass while held — a thumb that turns into a lens, a tab indicator that
grows a rim — are two materials:

```kotlin
val thumbAtRest = GlassMaterial(
    tint = Color.White, blurRadius = 8.dp,
    refractionHeight = 0.dp, refractionAmount = 0.dp, refractionBySize = false,
    rim = null, shadow = GlassShadow(radius = 4.dp)
)
val thumbPressed = thumbAtRest.copy(
    tint = Color.Transparent, blurRadius = 0.dp,
    refractionHeight = 10.dp, refractionAmount = 14.dp,
    rim = GlassRim.Ambient, innerShadow = GlassInnerShadow(radius = 4.dp)
)

val drag = rememberDampedDragAnimation(initialValue = 0f, valueRange = 0f..1f) { _, _ -> }

Modifier
    .then(drag.modifier)                    // the gesture that presses the thumb
    .liquify(
        shape = Capsule(),
        material = thumbAtRest,
        pressedMaterial = thumbPressed,
        pressProgress = { drag.pressProgress }   // 0 at rest, 1 held — read in the draw phase
    )
```

Everything blends with the press, in the draw phase: blur, refraction, saturation and the dome ease
between the two; a tint, a rim or a shadow that exists on one side only fades in or out; an inner
shadow grows in. With `dragging` or `interactiveHighlight` on, `pressProgress` defaults to the
element's own touch. And while a tint is opaque — the thumb at rest — the backdrop is not even
recorded.

The progress can be anything from `0f` to `1f`. A pane that drops its refraction while it flies is a
calm material driven by `MotionBlur.intensity` — see [Transitions](#transitions). Under `Frosted`
there is no touch, but a progress passed in still blends the two; under `Solid` the pane keeps its
resting material.

A pressed material that differs only in colour or decoration — a shadow that breathes, a rim that
fades in — leaves the effect chain alone: the pane redraws, nothing is rebuilt, so it can run for as
long as it likes.

---

## Touch

```kotlin
Modifier.liquify(
    Capsule(),
    dragging = true,             // leans towards the finger, tanh-damped
    stretching = true,           // …and swells and stretches along its travel
    interactiveHighlight = true  // a hotspot under the fingertip, trailing on a spring
)
```

- **`dragging`** bounds the lean to the element's short side however far the finger goes. It claims
  the drag once the finger passes the touch slop, so leave it off inside something that scrolls.
- **`stretching`** needs `dragging`: something that stays put has nothing to stretch towards.
- **`interactiveHighlight`** lights the element up from within, in the colour of its glass: a
  tinted element glows in its own `tint` — the colour gets brighter, rather than a white film
  landing on it — and clear glass in white. Only the element itself is lit. In a group the light
  flows on through the bridges like the colours do (see [Merging](#merging--liquidglassgroup)).

**Taps still work.** Nothing is consumed before the touch slop, so a press that never really moves
reaches your `clickable` intact, while the glow and the flex start on touch-down.

**Shared gestures.** Hoist a `GlassInteraction` only when several elements must share one gesture —
a tab bar whose panel glows from wherever its indicator sits:

```kotlin
val shared = rememberGlassInteraction(
    position = { size, _ -> indicatorCenter(size) },  // where the light comes from
    tracksConsumedDrags = true                         // keep following the indicator's own drag
)

Row(Modifier.liquify(Capsule()).then(shared.modifier)) { … }          // glows
Box(Modifier.then(shared.gestureModifier).liquify(Capsule())) { … }   // feeds the gesture
```

**Controls with a value.** `rememberDampedDragAnimation` is the gel physics behind sliders, toggles
and tab indicators: a value that lags slightly behind the finger, a press that swells, and a velocity
to stretch along. It claims a gesture only along its own axis, so a vertical swipe that starts on a
horizontal slider still scrolls the page. Its `pressProgress` is exactly what a pressed material
wants.

**Motion presets.** `GlassMotion.Performance`, `Default` and `Bouncy` set how far glass leans, how much
it swells and how much it wobbles — everywhere at once, from the material. The differences are a few
per cent: three moods of one material. `Performance` is also the cheapest — every frame a spring is
still moving is a frame of glass to render.

---

## Merging — LiquidGlassGroup

The headline feature. Glass elements inside a group fuse into one body:

```kotlin
LiquidGlassGroup(merge = 1.2f) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(72.dp).liquify(Capsule(), tint = Color.Red.copy(alpha = 0.75f)))
        Box(Modifier.size(72.dp).liquify(Capsule(), tint = Color.Blue.copy(alpha = 0.75f)))
    }
}
```

The group evaluates every member's signed distance field, blends them with a smooth minimum and
renders one surface: two members approaching grow a liquid bridge well before they touch, fuse,
then pinch apart again — with refraction (its dome and its chromatic aberration too), rim and
shadow following the combined outline, so there is never a seam.

**Who merges.** Every `liquify` inside the group, made of the group's `material` with its rim and its
shadow. An element renders a pane of its own instead when it has its own `effects` (unless they call
`merge()`), when it sits inside another glass element — a clear button inside a search field — or
when `merge` is `0f`. Merging needs `GlassLook.Liquid` and API 33; elsewhere every member is simply its
own pane. The body refracts the group's `backdrop`, or else the one `LiquifyTheme` declares — with
neither, a merging group fails with a message saying so, as a lone element would.

**When a bridge appears.** The blend reach `k` pulls the surface in by at most a quarter of `k`, so two
members fuse once `k` exceeds twice the gap between them. With `merge` relative to the short side,
that is `merge > 4 × gap / short side`: two 48 dp buttons 8 dp apart need about `0.7` and look
properly gooey around `1.2`.

**Colours mix.** A member's `tint` is its colour in the body, mixed with the very weights the distance
fields are blended with: apart, two members stay pure red and pure blue; fused, the neck carries the
mixture. Dragged into a differently coloured neighbour, a member carries its colour across like a
liquid, and the neighbour's own colour returns about a second and a half after they part — these are
controls, and a delete button has to end up red again.

**Light flows like colour.** A held member's touch light is drawn by the group, not by the member,
and mixed with the same weights: it fills the member, fades across the neck of a bridge into the
colour beyond and stops where the bridge does. So a member never lights a neighbour across a gap,
and its light never ends at its own outline in the middle of the body.

**Limits.** Up to twelve members per group, and the cost is linear in their number — keep groups
tight. The group lays its children out as a `Box`, so they stack: wrap them in a `Row` or a `Column`.

---

## Backdrops

```kotlin
rememberLayerBackdrop()                  // real composables, marked with Modifier.layerBackdrop
rememberCanvasBackdrop { drawPattern() } // something drawn — cheap, nothing captured
colorBackdrop(color)                     // one colour — glass over it needs no layer at all
rememberCombinedBackdrop(a, b)           // b drawn over a
rememberBackdrop(a) { draw -> … }        // a, filtered or transformed while it draws
emptyBackdrop()                          // nothing behind
```

A backdrop is re-recorded every frame, so glass tracks a scrolling list or a moving wallpaper. For a
full-screen content layer, `rememberLayerBackdrop(drawContentFromLayer = true)` records the content
once and draws the recording, instead of drawing it twice. `rememberCombinedBackdrop` skips a `null`,
so the inherited `LiquifyTheme.backdrop` goes in as it is: `rememberCombinedBackdrop(LiquifyTheme.backdrop, panel)`.

**One rule that crashes if broken:** glass must never sample the layer it is part of. Glass *inside*
a `layerBackdrop` source refracts something else — the colour of its card, say, which is what the
catalog's `MaterialSurface` declares for the controls on it with `colorBackdrop`.

Over a `colorBackdrop` there is nothing to blur and nothing to bend, so a pane is laid down as that
colour, with the material's vibrancy worked into it: no layer is recorded and no effect runs. Its
tint, rim, shadows, touch and pressed material are untouched. An element with its own `effects`, one
with `refractsContent` and the members of a merging group render it the long way.

---

## Custom effects

For a recipe a material cannot express, write the effect stack yourself. The effects are members of
the scope — no imports — and take dp:

```kotlin
Modifier.liquify(
    Capsule(),
    effects = {
        vibrancy()
        blur(4.dp)
        lens(refractionHeight = 24.dp, refractionAmount = 24.dp)
    }
)
```

| Effect | |
| --- | --- |
| `blur(radius)` | Gaussian blur of the backdrop. |
| `lens(refractionHeight, refractionAmount, depthEffect, chromaticAberration)` | Bends the backdrop along the border — exactly these values, at any size. |
| `vibrancy(amount)` | Saturation, so colours survive the blur. |
| `tint(color)` | Blends the backdrop towards a colour *before* refraction. |
| `material(material)` | A material's recipe, size-adapted refraction included. |
| `merge(amount)` | Joins the enclosing group, for an element with its own effects. |
| `effect(renderEffect)` | Any `RenderEffect` — the escape hatch for your own AGSL. |

**Call order is GPU order.** `blur()` before `lens()` bends already-blurred light, which is what reads
as thick glass; reversed, it reads as a blurred sticker. The scope is a `Density`, so a dp value
converts with `toPx()` and `size` is in the same pixels.

---

## Rims and shadows

Both live on the material — `rim`, `shadow`, `innerShadow` — so an app states them once.

| Rim | Look |
| --- | --- |
| `GlassRim.Default` | Directional, lit from a fixed angle. |
| `GlassRim.Ambient` | White on the lit side, dark opposite — reads as a bevel. |
| `GlassRim.Plain` | A uniform hairline; the cheapest, and the fallback below API 33. |
| `GlassRim.Outline` | A crisp line in one colour — the theme's ink unless given one. |
| `GlassRim.TwSe` | A drawn edge: a lit band inside the outline and a shadow band outside it, swapping by quadrant so one light seems to cross a raised lip. The same in light and dark UIs. |

Styles carry their angle, falloff and colours: `GlassRim(width = 1.dp, style =
GlassRimStyle.Default(angle = 210f))`. Fading a rim or a shadow is a layer property and costs nothing.

**Shadows follow the theme.** An unspecified shadow colour resolves when it is drawn: black ink over a
light UI, a pale halo over a dark one — black on near-black is no shadow at all. The theme comes from
`LiquifyTheme(darkTheme = …)`, or the system. Shadows are punched out under the pane, so they never
darken the backdrop seen through the glass.

---

## Transitions

```kotlin
Modifier.materialize({ progress })   // content blurs and fades into the material, not cross-fades
Modifier.motionBlur({ progress })    // blur by how fast something is moving right now
```

`motionBlur` samples the rate of change rather than the value, so a spring blurs hardest mid-travel
and sharpens as it settles, and costs nothing at rest. Hoist it with `rememberMotionBlur` when the
glass should also calm down while it flies:

```kotlin
val motion = rememberMotionBlur(maxRadius = 12.dp, fullSpeed = 15f) { progress.value }

Modifier
    .motionBlur(motion)
    .liquify(
        RoundedRectangle(22.dp),
        pressedMaterial = LiquifyTheme.material.copy(blurRadius = 0.dp, refractionHeight = 0.dp),
        pressProgress = { motion.intensity }
    )
```

A render effect rasterises its layer at exactly the layer's bounds, so leave slack around anything
that draws past them — a group draws half a merge radius past its box, and its shadow further:
`Modifier.height(470.dp).motionBlur { p }.padding(bottom = 48.dp)`.

---

## Progressive blur

```kotlin
Column(
    Modifier
        .fillMaxWidth()
        .progressiveBlur(radius = 20.dp, tint = background.copy(alpha = 0.8f))
        .statusBarsPadding()
) {
    Title()
    SearchField()
}
```

The surface of a header a list scrolls under: the backdrop blurred at `radius` at the top, the blur
easing off towards the bottom edge and the tint fading with it, so the header ends *in* the page
instead of on a line. It is a real progressive blur — the radius falls off with the height, in two
Gaussian passes at half resolution — not one blur faded out over a sharp copy, which reads as two
pages laid over each other. And it is not glass: no outline to refract along, no rim, no shadow, and
none of their cost.

It adapts like a pane. Android 12, without runtime shaders, blurs at one radius and fades it out
through a gradient; under `GlassLook.Solid` and below Android 12 the tint is drawn alone, opaque at
the top and fading out downwards. Like glass it samples the inherited backdrop, so put the header
*beside* the layer it blurs, not inside it — and glass on the header, a search field or a segmented
control, refracts that same layer.

---

## Performance

- **Glass clips without masks.** An element clips to the rounded rectangle of its outline, which the
  renderer clips to analytically. A continuous-curvature corner is a path, and an anti-aliased clip to
  a path costs a coverage mask for every draw inside it — the glass, its colour, each icon and line of
  text — rasterised on the CPU and uploaded again on every frame, because the renderer cannot keep a
  clip mask from one frame to the next. On a mid-range phone (Adreno 642L, Android 14) that was 31
  uploads a frame on a single screen, and dropping them took a scrolling page from 18 % janky frames
  to 5 %. The same holds for your own `Modifier.clip`: clip to `RoundedCornerShape` or `CircleShape`,
  and keep the continuous shapes for glass. `exactClip` brings the curve back where it matters.
- **Draw small shapes analytically.** A continuous-corner shape drawn on every frame — a chip, a
  list item's background, a progress bar — is a path, and one smaller than about 256 × 256 px is
  rasterised into a path atlas again on every frame, and again into every pane of glass above it.
  A `RoundedCornerShape` is drawn analytically, many in one go; at small radii the two curves differ by
  under half a pixel. On the same phone, a scrolling week of small rounded blocks went from 11.5 % to
  6.4 % janky frames.
- **The colour costs no draw.** The lens lays the pane's tint over the refracted backdrop itself,
  instead of a second draw that blended the whole pane again on every frame. A tint that a press blends
  towards another one is still drawn on its own, so the chain is not rebuilt for every frame of it.
- **Colour work costs no pass.** Vibrancy and tint fold into one matrix that the refraction or merge
  program applies to every sample, on premultiplied colour in half precision.
- **A light blur costs no pass either.** A blur of up to 2 dp in front of the lens — the light blur
  glass usually wears — is taken in by the refraction or merge program as nine taps per sample,
  instead of running as an effect of its own: on a tile-based GPU that was two more render passes per
  pane, and on a mid-range phone with a dozen panes the largest single cost of the glass. A wider
  blur, or one without a lens after it, stays the platform blur.
- **Most of a pane is one read.** A pixel deeper than the refracting band is recognised by its
  distance to the box alone and leaves with one sample and the colour matrix.
- **Opaque panes sample nothing.** While a pane's tint is opaque its backdrop is not recorded, and a
  `Solid` look takes the layer, the chain and the shader out of every pane.
- **One colour costs nothing.** Glass over a `colorBackdrop` is that colour: no layer, no chain, no
  shader, in any look — the controls on a card are as cheap as the card. A pane makes its backdrop
  layer only when it first draws a backdrop, so these, and a knob behind an opaque tint, never make
  one at all.
- **Two nodes per pane.** One draws the decoration — shadow, rim, inner shadow — and one the glass;
  a third layer exists only while the element moves.
- **Shadows and soft rims are blurred once.** Each is drawn sharp into a layer that blurs it with a
  render effect, and kept as an offscreen texture: the blur runs when the shadow or rim changes, not on
  every frame — Android 14 runs a render effect again on every draw on older Qualcomm drivers. A mask
  filter blurs the same Gaussian, but on a path the renderer blurs a mask for it on the GPU, and on
  that phone the memory was never handed back: some 7 MB more with every screen of glass visited,
  until the app reached its GPU budget and scrolling went from 1 % to 13 % janky frames. Keep
  `BlurMaskFilter` off paths in your own drawing, too.
- **Nothing is rebuilt that did not change.** Uniforms are fingerprinted, effect chains kept while
  their inputs hold, outlines cached per size, and recordings of rims and shadows reused — a group
  redrawn because the page scrolled behind it costs one merge pass, and a press that only fades a
  shadow or a rim costs a redraw of the decoration.
- **Shader programs are pooled** process-wide, so a panel full of glass opening again pays no parse
  in the frame it appears; the touch glow compiles nothing until the first press.
- **A static backdrop is the biggest win.** Any frame re-renders glass over a `layerBackdrop`, so an
  endless animation anywhere in the window costs a blur and a refraction per pane for as long as it
  runs. Let animations end.
- **Big panes are best plain.** A panel over the whole page refracting it costs more than all the
  controls in it together, on exactly the frames it animates in. Under `GlassLook.Solid` a pane is
  its fill and its rim; put glass controls on it with a `colorBackdrop` of its colour, and they cost
  nothing either (the catalog's `GlassPanel` does).
- **Paint every pixel once.** Behind a full-screen backdrop layer that draws its own background, a
  window background and a background on the root are two more full-screen fills on every frame —
  clear them (`window.setBackgroundDrawable(null)`) when the content covers the window.
- **The progressive blur is the expensive element.** Over a tall header its two passes cost about as
  much as all the panes in it together; keep it as short as the design allows.
- **Measure with a warm-up.** Shader compilation and thermals move frame timings enormously, and so
  does GPU memory: an app close to its cache budget loses the renderer's reordering of offscreen work,
  and its main render pass splits into pieces. If the part of it that is not purgeable (`dumpsys
  gfxinfo`) grows with every screen visited, something is holding on to GPU memory.

---

## Migrating from 1.x

| 1.x | 2 |
| --- | --- |
| `ProvideBackdrop(b) { }`, `ProvideGlassMaterial(m) { }`, `ProvideGlassDarkTheme(d) { }` | `LiquifyTheme(backdrop = b, material = m, darkTheme = d) { }` |
| `LocalGlassEnabled provides false` | `LiquifyTheme(look = GlassLook.Solid)` — and `Frosted` in between |
| `LocalGlassMaterial.current` | `LiquifyTheme.material` |
| three `liquify` overloads | one `liquify(shape, …)` |
| `liquify(shape = { s }, …)` | `liquify(s, …)` |
| `onDrawSurface = { drawRect(color) }` | `tint = color` |
| `highlight = …`, `shadow = …`, `innerShadow = …` | on the material: `material = m.copy(rim = …, shadow = null)` |
| rim / shadow lambdas driven by a press | `pressedMaterial` + `pressProgress` |
| `tint`, `animations` on `liquify` | on the material |
| `supersampling` | removed — glass always renders at full resolution |
| `Highlight`, `HighlightStyle` | `GlassRim`, `GlassRimStyle` |
| `Shadow`, `InnerShadow` | `GlassShadow`, `GlassInnerShadow` |
| `InteractiveHighlight`, `rememberInteractiveHighlight` | `GlassInteraction`, `rememberGlassInteraction` |
| `GlassAnimations`, `material.animations` | `GlassMotion`, `material.motion` |
| `blur(8.dp.toPx())`, `lens(h.toPx(), a.toPx())` | `blur(8.dp)`, `lens(h, a)` — scope members |
| `lensBySize(…)`, `refractionBySize = true` | the default; `material(m)` in an effects block |
| `mergeTint(c)`, `merge(radius: Dp)` | a member's `tint`; `merge(amount)` |
| `rememberGlassEffects(m)` | `LiquidGlassGroup(material = m)` |
| `Modifier.liquidGlassGroup(…)`, `LocalLiquidGlassGroup` | `LiquidGlassGroup { }` — membership is automatic |
| a member leaves the merge by naming another material | it leaves with its own `effects`, by being nested, or with `merge = 0f` |
| `onDrawBehind`, `onDrawFront`, `onDrawBackdrop` | draw around the element, or wrap the backdrop with `rememberBackdrop` |
| `gradientBlur`, `opacity`, `colorControls`, `colorFilter`, `runtimeShaderEffect` | `effect(renderEffect)` |
| `HighlightStyle.Dynamic`, `dynamicHighlight()` | removed |
| `rememberLayerBackdrop(graphicsLayer = …)` | `rememberLayerBackdrop()` |
| packages `…effects`, `…group`, `…highlight`, `…shadow`, `…material`, `…interaction`, `…backdrops`, `…transition` | `com.hakim.liquify` |

---

## The catalog app

The `:catalog` module is a demo app built only from Liquify's public API and Compose Foundation — no
Material 3 — and the reference for how to use the library: every component in it is meant to be
copied.

```bash
./gradlew :catalog:installDebug
```

| Screen | Shows |
| --- | --- |
| Merge & gooey | Two to four panes fusing and separating with a live `merge` — each in its own colour, mixing in the bridges and carried along when dragged |
| Morphing | One capsule splitting into three controls, bridged while it travels |
| Menu emergence | A menu growing out of the button that opened it |
| Interactive controls | Plain, coloured and disabled buttons, toggles, a slider with double-tap reset, segmented tabs |
| Header & navigation | Five controls reshaping into each other as one merged body, a merged `‹ label ›` picker, a search field with a nested clear button, a live button that breathes |
| Panels & menus | A plain panel with glass controls standing on its colour, a drop that flies out of its trigger and spreads into a menu, a snackbar |
| Scroll & bars | A small app: four pages under a `progressiveBlur` header with a search field and tabs, bottom tabs that follow the swipe, a menu out of the FAB, a panel behind every row |
| Materials | The four presets — **tap one and the whole app switches to it** |
| Rims & shadows | The five rim styles with their light angle, width, blur and opacity; drop shadow, glow and inner shadow |
| Backdrops & content | Layer, canvas, colour, combined, decorated and empty backdrops; glass on glass through `exportedBackdrop`; `refractsContent`; an `effects` block with a colour matrix in it; `progressiveBlur` |
| Looks & motion | `Liquid`, `Frosted` or `Solid`, the three `GlassMotion` presets, the depth effect and chromatic aberration, and light or dark — **for the whole app** — and the three looks side by side through a nested `LiquifyTheme` |
| Playground | Every parameter of a `GlassMaterial` on a slider, with a pressed state |

Every screen's header is one `LiquidGlassGroup` — back in the accent, the title clear, an action in
its own colour — drawn over the screen's layer, so it refracts the screen scrolling beneath it. Two
layers make up the screen: panes inside it refract the wallpaper's, and the chrome above it — the
header, and the panels and menus a screen hands up to the shell — the screen's.

It ships `LiquidButton`, `LiquidToggle`, `LiquidSlider`, `LiquidTopTabs`, `LiquidBottomTabs`, the
`LiquidDropdown` trigger and overlay, `LiquidSearchField`, `LiquidSnackbarHost`, `GlassPanel`,
`RangeNav`, `LiveButton` and `MorphingControls`, each in all three looks. The knobs of the toggle and
the slider are two materials each; the tab bar refracts a hidden, accent-tinted copy of its own tab
row, so icons genuinely change colour *through* the indicator. The app turns Android's overscroll
stretch off: it is a render effect on the scroll container, which the glass would refract
unstretched.

---

## Credits

The rounded-rectangle signed distance field, its gradient, the refraction and rim AGSL programs, and
the backdrop / effect-scope / shadow-node structure are derived from
**[Kyant0/AndroidLiquidGlass][alg]** (Apache-2.0). Liquify began as a clone of that project rather
than a fork — a mistake of process, and the lineage is stated here instead of buried. Kyant0 built
the foundation this stands on. Corner shapes come from **[Kyant0/Shapes][shapes]**, consumed as a
Maven artifact. See [`NOTICE`](NOTICE).

What Liquify adds: the merged distance-field renderer with members' colour and refraction blended
through it, refraction sized to each element, the adaptive theme and its looks, materials with
pressed states and motion presets, the one-line touch reactions, and the single `liquify` modifier.

## Contributing

Issues and feature requests are very welcome — describe what you are trying to build, and I will
help as soon as I can. Pull requests too; see [CONTRIBUTING.md](CONTRIBUTING.md).

## Licence

Apache License 2.0 — see [`LICENSE`](LICENSE).

[alg]: https://github.com/Kyant0/AndroidLiquidGlass
[shapes]: https://github.com/Kyant0/Shapes
