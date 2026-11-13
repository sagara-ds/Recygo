# Figma implementation scope

Access verified: 12 November 2026. Source: [Design Apk](https://www.figma.com/design/7tPzzgzyPQCkhl6UnuFd98/Design-Apk?node-id=0-1).

| Node | Screen | Use |
| --- | --- | --- |
| 2003:631 | Beranda Warga | Green header, points, three actions, active order, information |
| 2003:681 | Setor sampah | Waste rows and selection; native form follows selection |
| 2003:726 | Pesanan warga | Active/history filter, status timeline, weighing confirmation |
| 2060:547 | Pesanan Masuk | Collector dashboard and incoming requests |
| 2003:951 | Detail Pesanan | Pickup location, waste, notes, start journey |
| 2003:1022 | Timbang sampah | Per-type actual weights and points |
| 2003:549 | Verifikasi Password | Password-form visual language, adapted for demo sign-in |
| 2003:806 | Profil & statistik | Profile summary and account actions |

The initial 2003:517 context is an OTP screen, not a password login. The user expanded scope to onboarding, both registration roles, OTP and recovery. Login uses phone/email + password; OTP is used only for registration and password recovery. OTP delivery is an explicitly local inbox, without real email/WhatsApp delivery.

Additional cached references cover splash 2003:176, welcome 2003:198, location 2003:225, notifications 2003:251, role choice 2003:276, resident registration 2003:378 + 2089:235, account lookup 2003:448, and recovery email 2003:479. Selected-role frames are interactive states of the role selector; splash variants are consolidated into one native animation respecting disabled animations.

The collector's three registration steps were viewed publicly, but their detailed exports are unavailable. Arrival 2003:990, transaction summary 2003:770 and history 2072:1146 use cached metadata and the existing native visual system. Collector profile extends the available resident profile system. Their exact fidelity remains unverified. OTP-specific `imgPreview3` and `imgIconKodeOtp1` were not present in the downloaded exports: OTP/reset success explicitly reuse the downloaded account wave and recovery icon. This is a documented visual adaptation, not an exact asset match. `scripts/check-assets.py` detects absent manifest files and named native references before packaging.

Figma MCP quota is exhausted. Do not repeat known failing requests. Original PNG/PDF frame exports plus corresponding SVG/PNG assets, or restored quota, are required to finish exact comparison of those gaps. Runtime functionality can be tested independently.

The supplied design uses Inter, green #16C784/#16C47F, near-black #111827, muted #6B7280, white surfaces, pale-green icon containers, rounded outlined rows, and pill primary actions. Android system bars are native and are excluded from asset copying. React Code Connect examples reference an external Simple Design System; there are no matching components in this empty Kotlin repository, so native controls are used.

Changes required for functional truth: real request/points data instead of fixed balances, demo labels, address/schedule entry, explicit errors/empty states, permission-free manual address, and an illustrative map label. No live route, ETA, chat, or call integration is claimed. See the asset manifest for original Figma assets. Asset ownership/licensing must be confirmed by the team before public distribution.

## Fidelity correction after device review

The first Android build introduced an automatic dark palette and changed dashboard geometry. The user rejected this as different from Figma. The approved source is the original light design; no alternate identity is authorized. The correction restores its white surfaces, green/white CTAs, white header lettering, original SVG icon tiles, circular waste selection, redeem affordance with honest demo explanation, and navigation topology. The fifth category remains visibly present but unavailable because the backend supports four materials.

The device has an existing density override. It is preserved. Design measurements are scaled proportionally from the 402px source frame; sp text still respects Android font scaling. Native status/navigation bars remain native. Updated device screenshots must be compared before claiming fidelity.

At font scale above 1.1, account screens use native flowing groups instead of fixed reference coordinates. Text is not capped: labels, fields, links and choices can grow and scroll. Decorative footers/CTA arrows recede at that setting. Default font geometry retains the Figma layout. Profile identity text uses measured vertical groups. This adaptation fixes overlaps found on the physical phone at font scale 1.3; it does not introduce an alternate theme.

Collector detail follows the available map/overlapping resident card, location, waste chips, notes and action structure. Fake rating/distance/ETA/currency values are replaced with actual order state, a map-illustration label and simulated points. Chat/phone controls explain their unavailable state and do not send messages or calls.
