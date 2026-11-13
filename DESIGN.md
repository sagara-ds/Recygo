---
name: Recygo
description: Native Android waste pickup demo preserving the supplied light Recygo Figma.
colors:
  header-green: "#2DCC90"
  primary: "#16C784"
  cta: "#16C47F"
  label-green: "#16A66A"
  pale-green: "#E3F7EE"
  surface: "#FFFFFF"
  ink: "#111827"
  muted: "#6B7280"
  outline: "#E5E7EB"
  error: "#B42318"
typography:
  display:
    fontFamily: "Inter, sans-serif"
    fontSize: "32px"
    fontWeight: 700
  headline:
    fontFamily: "Inter, sans-serif"
    fontSize: "26px"
    fontWeight: 700
  title:
    fontFamily: "Inter, sans-serif"
    fontSize: "20px"
    fontWeight: 600
  body:
    fontFamily: "Inter, sans-serif"
    fontSize: "16px"
    fontWeight: 400
  label:
    fontFamily: "Inter, sans-serif"
    fontSize: "16px"
    fontWeight: 600
  caption:
    fontFamily: "Inter, sans-serif"
    fontSize: "13px"
    fontWeight: 400
rounded:
  field: "16px"
  action-card: "18px"
  row-card: "24px"
  pill: "32px"
  header: "40px"
spacing:
  tight: "4px"
  small: "8px"
  compact: "12px"
  default: "16px"
  section: "20px"
  large: "24px"
  wide: "32px"
components:
  button-primary:
    backgroundColor: "{colors.cta}"
    textColor: "{colors.surface}"
    rounded: "{rounded.pill}"
    padding: "12px 16px"
    height: "64px"
  button-secondary:
    backgroundColor: "{colors.pale-green}"
    textColor: "{colors.label-green}"
    rounded: "{rounded.pill}"
    padding: "12px 16px"
    height: "64px"
  field:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.field}"
    padding: "14px 16px"
    height: "56px"
  card:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.row-card}"
    padding: "16px"
  action-card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.label-green}"
    rounded: "{rounded.action-card}"
    padding: "12px"
    height: "124px"
  selection-row:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.label-green}"
    typography: "{typography.label}"
    rounded: "{rounded.row-card}"
    padding: "12px"
    height: "73px"
  status-chip:
    backgroundColor: "{colors.pale-green}"
    textColor: "{colors.label-green}"
    rounded: "{rounded.field}"
    padding: "6px 10px"
  navigation:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.muted}"
---

# Design System: Recygo

## Overview

**Creative North Star: "The original Recygo light Figma"**

The supplied Recygo design owns the identity: fresh green fields, white surfaces, dark text, pale icon tiles, rounded outlined rows, and clear Indonesian labels. Preserve the visual relationships of [Figma file 7tPzzgzyPQCkhl6UnuFd98, page 0:1](https://www.figma.com/design/7tPzzgzyPQCkhl6UnuFd98/Design-Apk?node-id=0-1). This name records the user's chosen source; it introduces no replacement aesthetic.

The implementation is native Android Views in Kotlin. Native system bars, window/keyboard insets, real data, and honest demo states adapt the supplied design to a working app. The automatic dark palette and recomposed dashboard were explicitly rejected. The light source remains binding even when the phone uses dark mode.

**Key Characteristics:**

- Light surfaces with distinct header, action, and label greens.
- Local Inter typography and original Figma SVG assets.
- Rounded white containers with fine neutral outlines.
- Native Android behavior around the supplied visual identity.

## Colors

A single green accent family separates large header fields, action fills, and text labels; neutral white and gray keep task content readable. The frontmatter is the normative palette extracted from `Ui.kt` and `MainActivity.kt`.

### Primary

- **Header Green** (`header-green`): the resident dashboard field and matching native status bar.
- **Recygo Green** (`primary`): compact redeem affordance and source accent details.
- **Action Green** (`cta`): filled primary action.
- **Label Green** (`label-green`): material names, action labels, selected navigation, and source icon strokes.
- **Pale Mint** (`pale-green`): secondary actions, icon tiles, status chips, and the points track.

### Neutral

- **White Surface** (`surface`): page, card, field, and primary-action lettering.
- **Deep Ink** (`ink`): headings, balances, and primary content.
- **Muted Gray** (`muted`): instructions, supporting data, and inactive navigation.
- **Fine Outline** (`outline`): container borders and dividers.

The implementation additionally uses **Error Red** (`error`) for functional error feedback; this is an app state, not an extra brand accent.

**The Source Palette Rule.** Keep the supplied light palette. Do not derive a replacement identity from system dark mode or wallpaper colors.

## Typography

**Display Font:** Inter, with Android sans-serif fallback.
**Body Font:** Inter, with Android sans-serif fallback.

Inter is bundled locally as `app/src/main/assets/fonts/InterVariable.ttf` with its OFL license. A single face carries the hierarchy through scale and weight. Source semibold labels use weight 600; do not silently promote them to bold.

### Hierarchy

- **Display:** the large points balance.
- **Headline:** screen titles.
- **Title:** section headings; the greeting also uses the title size with bold emphasis.
- **Body:** material rates, fields, and ordinary content.
- **Label:** material names and prominent controls, with source semibold.
- **Caption:** supporting and demo copy; dashboard action captions use this size with semibold emphasis.

Frontmatter sizes are source-frame units encoded as CSS pixels for token portability. Native text maps those numbers through `Ui.sp()`, then Android sp; it must retain system font scaling. Body text also uses the implemented 3-unit extra line spacing and excludes Android font padding. This is an observed hierarchy with size variants, not a claim that every text instance already uses a formal theme role.

**The Inter Weight Rule.** Preserve regular, semibold, and bold as distinct weights; keep font scaling enabled.

## Layout

The mobile source frame is 402 units wide. `Ui` derives a proportional scale from the current width in dp, clamped to 0.8–1.5. Source geometry maps through `Ui.dp()`; source text maps through `Ui.sp()`. The existing device density override is preserved rather than reset to make screenshots appear closer.

Screens use a vertical scroll container, white content, native system and IME insets, and a separate bottom navigation row when applicable. General content gutters are 24 source units; the home and selection compositions have source-specific gutters recorded in their surface brief. Repeated spacing follows the extracted scale; keep headings closer to their content than to the preceding section.

Buttons and rows have minimum heights rather than fixed text clipping. The shared primary action has a 64-unit minimum; fields have a 56-unit minimum. Back, notification, redeem, and navigation hit areas use 48-unit minimum dimensions in source geometry. A full accessibility, tablet, and enlarged-font verification is not established by the two phone captures.

## Elevation & Depth

The reusable surfaces are flat: fine outlines, pale fills, overlap, and the curved green header create separation. Shared buttons disable the Android state elevation animator. No custom shadow or motion scale was extracted, so none is invented here. Native platform dialogs retain their platform behavior.

**The Flat Surface Rule.** Use the existing outline and tonal separation before adding elevation.

## Shapes

Soft corners distinguish functions: compact fields, action cards, larger row cards, pill actions, and the dashboard's rounded lower corners use the frontmatter radius roles. Selection indicators remain circular source assets; a selected indicator has a white check. Keep the source's pale rounded icon tiles and stroke illustrations rather than substituting emoji or text glyphs.

## Components

### Buttons

Filled Action Green pills carry white sentence-case text; secondary pills use Pale Mint and Label Green. The shared button uses a 20-unit bold label. Compact text actions and navigation intentionally override the shared size, background, and minimum height. Native clickable and focusable semantics remain essential; no custom native hover, focus ring, ripple, or animation system has been verified. Sidecar HTML states are panel-only affordances, not evidence of Android behavior.

### Cards / Containers

White outlined row cards hold task content with the shared padding. Home shortcut cards use the smaller action-card radius, three equal columns, and original icon tiles. Their source overlap with the header is specific to the resident home composition.

### Inputs / Fields

White rounded fields have a fine outline, dark input, muted hint, and native input types. Errors use native field feedback or the polite live feedback region. Retain password visibility and keyboard behavior; a bespoke visual focus treatment has not been established.

### Navigation

Four resident destinations appear in a white bottom row separated by a fine divider: Beranda, Pesanan, Riwayat, Profil. Active text is Label Green; inactive text is Muted Gray, both semibold at the implemented navigation size. Detail and weighing screens retain the orders context. Android Back uses the app's back handler and modern platform callback; source-drawn iOS status and home bars are not copied.

### Waste Selection

Outlined rows pair a pale original material icon with a green semibold name and muted rate. The whole row toggles selection, communicates checkable/checked accessibility state, and updates the circular indicator. The visible fifth category, Lainnya, opens an unavailable explanation rather than accepting an unsupported material.

### Status Chips

Pale rounded status labels use Label Green text within order cards. Their content follows the real order state; they are not decorative claims of activity.

### Points Panel

A white panel within the home header displays the computed balance and a compact redeem affordance. Zero points and an empty progress track are valid demo states. Redeem opens an honest explanation that points cannot yet be exchanged.

## Do's and Don'ts

### Do:

- **Do** preserve the original light Figma identity and source assets.
- **Do** scale source geometry from the 402-unit frame while retaining native insets and sp font scaling.
- **Do** keep native system bars and the device's existing density.
- **Do** show actual demo balances, empty orders, and unavailable categories honestly.
- **Do** compare rendered native captures with the matching source screen before claiming fidelity.

### Don't:

- **Don't** reintroduce automatic dark theming or replace the supplied dashboard composition.
- **Don't** copy drawn iOS system bars into Android content.
- **Don't** replace source icons with glyphs or treat semibold as bold.
- **Don't** claim visual verification of screens beyond Beranda Warga and Setor Sampah from the current review evidence.
