# Recygo native Android surface brief

Primary target: `app/src/main/java/id/recygo/app/MainActivity.kt`
Related target: `app/src/main/java/id/recygo/app/Ui.kt`
Visitor mode: Operate
Platform: Android, native Views in Kotlin

## Scope and authority

Residents select waste and request a pickup; collectors process the resulting orders. This app preserves the user's original [Recygo Figma, page 0:1](https://www.figma.com/design/7tPzzgzyPQCkhl6UnuFd98/Design-Apk?node-id=0-1). The user rejected the initial automatic dark palette and recomposed dashboard. Preserve the original light identity and adapt only for native behavior, real data, and the agreed demo limitations.

This implementation uses a code-first native workflow for this app. This records the current task's approach, not a standing preference or an `.impeccable/config.json` setting. The supplied Figma remains the visual authority; code-first does not reduce its authority. No HTML/CSS detector ran because it has no verdict on native Android code.

## Direction contract

THESIS: Make the original Recygo resident pickup flow usable as a native Android demo. Preserve its recognizable white and green visual hierarchy and task order.

OWN-WORLD: Use the supplied light Figma, local Inter, original SVG icon tiles, white outlined containers, circular selection, and pill actions. Native system bars and truthful account data sit within this world.

STORY: A resident sees their actual demo balance and current orders, chooses Setor sampah, selects supported materials, and continues to a native pickup form. Unsupported features explain their limitations.

FIRST VIEWPORT: Beranda Warga has the green curved header, white greeting, points panel, three equal shortcut cards overlapping the lower header, active orders, information, and four bottom destinations. Setor Sampah has a back action and title, muted instruction, five outlined rows, circular indicators, and the wide Lanjut pill below. Source geometry uses a 402-unit frame; preserve native insets and the current density.

FORM: Preserve the user-supplied composition through Kotlin Views. No new form exploration or generated concept was selected for this fidelity correction. seed: none (unavailable)

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Composition notes

- Home: source gutters are 35/32 around shortcut cards and 32 around the lower content; the curved header contains the original source decorative assets. Keep the source's proportional relationships rather than flattening it into a generic stacked dashboard.
- Selection: source gutters are 29/31; five source-shaped rows remain visible. Four materials are supported; Lainnya remains visible and explains that it is unavailable. Row names use source semibold and selection uses a white check in the filled green circle.
- Zero points, empty orders, native status/navigation bars, and a demo explanation are accepted adaptations. Do not fabricate the source screenshot's balance, an active collector, redeemability, or a live pickup service.
- Geometry scales from the source width while text still respects Android font scaling. Do not reset the existing device density to improve an apparent comparison.

## Evidence and limits

Source references are `.impeccable/review/reference-2003_631.png` (Beranda Warga) and `.impeccable/review/reference-2003_681.png` (Setor Sampah). Current native captures are `.impeccable/review/phone-home-final.png` and `.impeccable/review/phone-select-final.png`. These are physical-phone captures; the native system UI intentionally differs from the Figma's drawn iOS bars.

The documentation pass inspected both source references, both phone captures, `PRODUCT.md`, `docs/DESIGN_SOURCE.md`, `Ui.kt`, and `MainActivity.kt`. The capture review scope is Beranda Warga and Setor Sampah only. The first independent review found substantial compositional fidelity and requested small back-icon, selected-check, and type corrections; their final verdict belongs to the parent task's reviewer after recapture. This brief does not certify those pending corrections, all other screens, tablet layout, enlarged-font layout, gestures, or hardware performance.

`DESIGN.md` and `docs/design/design.json` record the extracted durable system. The surface-brief launcher returned exit code 1 without diagnostics during this pass, so this brief was written directly; runtime indexing has not been verified.
