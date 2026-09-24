# CinefinTV Design Direction & System

> Formal design specifications, brand identity, and anti-slop dials for the CinefinTV Android TV client.

---

## 1. Identity & Mood

- **Product**: Standalone, native Jellyfin client built from the ground up for Android TV and Google TV (10-foot experience).
- **Core Mood**: **Cinematic, immersive, focused, and distraction-free**. The application serves media content; the chrome should quietly recede during browsing and disappear during playback.
- **Audience**: Home theater enthusiasts, Jellyfin self-hosters, and family living room viewing.
- **Ten-Foot Experience**: Every screen, surface, and interactive control is designed for D-pad navigation with clear, unambiguous focus states, high-contrast typography, and zero pointer/touch assumptions.

---

## 2. Anti-Slop Liveliness Dials

Per the **antislop** specification, the interface adheres to three calibrated dials:

| Dial | Level | Implementation & Rationale |
|---|---|---|
| **ENERGY** | **2 (Controlled)** | Deep, cinema-grade backgrounds (`#0D1117`) let colorful movie and show artwork stand out. Surfaces use measured tonal elevation without neon accents, purple-pink default glows, or decorative filler graphics. |
| **RHYTHM** | **2 (Structured Variety)** | Shelves vary purposefully based on content semantics: 16:9 landscape cards for continue-watching and next-up episodes; 2:3 portrait posters for library rows; distinct chips for audio/collection items. Avoids uniform bento grids or cookie-cutter templates. |
| **MOTION** | **2 (Choreographed & Snappy)** | D-pad focus transitions settle smoothly using fast cubic eases (180–220ms). Overshoot spring scale is restricted to high-tier hardware to preserve 60fps rendering on budget streaming sticks. Zero endless background pulses or decorative looping animations. |

---

## 3. Color System

- **Background & Canvas**:
  - `BackgroundDark`: `#0D1117` (Deep slate, avoiding harsh `#000000` except in dedicated AMOLED Black mode)
  - `BackgroundTop`: `#161B22` (Subtle dark gradient lift at header)
  - `SurfaceDark`: `#0D1117`
  - `SurfaceElevated`: `#161B22`
  - `SurfaceVariant`: `#21262D`
- **Brand Accents**:
  - `CinefinRed`: `#E50914` (Primary brand accent, used for high-impact actions like "Play" / "Resume")
  - `JellyfinPurple`: `#7E57C2` (Secondary platform heritage accent)
  - `CinefinGold`: `#FFD700` (Media badges, IMDb/critic rating stars, and subtitle highlights)
- **High-Contrast Typography**:
  - Primary text (`OnBackground`): `#F0F6FC` (Luminance ratio > 12:1 against canvas)
  - Muted secondary text (`OnSurfaceMuted`): `#8B949E` (Luminance ratio > 5.9:1 against canvas, fully WCAG AA compliant)
- **Focus Indicators**:
  - Crisp, white focus borders (`2.dp`, `Color.White.copy(alpha = 0.8f)`) accompanied by subtle, performance-scaled glow rings (`focusGlow`).

---

## 4. Typography Scale (10-Foot Readability)

All typography is strictly scaled for legibility from couch distance (typically 7–10 feet):

- `DisplayLarge`: 57sp / ExtraBold (Cinema Wall titles)
- `HeadlineLarge`: 36sp–44sp / Black (Hero and screen titles)
- `HeadlineMedium`: 28sp / SemiBold (Shelf titles and modal headings)
- `TitleLarge`: 24sp / SemiBold (Card focus labels and section anchors)
- `TitleMedium`: 20sp / Medium (Standard button text and dialog labels)
- `BodyLarge`: 18sp / Normal (**Baseline minimum** for overviews, descriptions, and list item bodies)
- `BodyMedium`: 18sp / Normal (Secondary descriptions and dialog messages)
- `LabelLarge`: 16sp / SemiBold (Action badges and metadata indicators)
- `LabelMedium`: 16sp / Medium (**Absolute floor** for tags, resolution chips, and duration pills; sub-16sp is strictly avoided on TV surfaces)

---

## 5. TV D-Pad Navigation & Resilience (Craftsmanship)

1. **Focus Traversal**:
   - Every screen has an explicit `FocusRequester` anchored to its primary actionable element on mount.
   - Rows utilize `focusProperties` / directional links so that horizontal navigation stays within shelves, and vertical navigation smoothly scrolls the canvas.
2. **Resilient UI States (R-27)**:
   - **Empty States**: Must clearly state the reason and provide an actionable focus target (e.g. "Refresh Library", "Back to Playlists") so D-pad focus is never trapped.
   - **Loading States**: Sized indicators with clear descriptive status ("Loading library...", "Restoring session...").
   - **Error States**: Clear error message accompanied by a high-contrast "Try Again" / "Retry" button.
3. **No Decorative Non-Interactive Elements (R-26)**:
   - Any visual element styled as a pill, button, or card must be focusable and perform a real action, or be converted to clean semantic text.
