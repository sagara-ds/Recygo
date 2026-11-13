# Implementation status

Updated 13 November 2026 (WIB). **Paused again at the user's explicit request; a friend will continue. Do not resume without a new instruction.** Current handoff: [HANDOFF.md](../HANDOFF.md).

Latest APK builds/lints successfully but is **not installed**. The phone still runs the version preceding the final password-masking fix. Source/APK now restore password transformation after `setSingleLine`; populated-field device verification is pending. The affected synthetic password was reset. Final source-positioned CTA arrows and measured profile divider/stats also await recapture. Last review disposition remains **fix**. Full physical pickup retest passed twice; cumulative synthetic-account results are3100 points/7kg.

## Confirmed scope

- Native Android Kotlin and Kotlin/JVM backend with SQLite; pickup is the priority.
- All Figma flows, including onboarding and resident/collector accounts. Original light design is authoritative; the user rejected the initial automatic dark theme and changed composition.
- Login: phone/email + password. OTP: registration and forgotten password only.
- OTP uses a local demo inbox now. SMTP/WhatsApp delivery and official identity verification are not implemented.
- README team name is `Masukan text...`, with the four requested members.

## Current implementation

- Server-controlled pickup states, role/ownership checks, atomic acceptance, idempotent submission, persisted orders, actual weights, resident confirmation and simulated points.
- Native splash/welcome, optional location/notification prompts, role selection, two-step resident and three-step collector registration, optional demo/camera/gallery images, consent, six-cell OTP, password recovery/reset and encrypted session storage.
- Encrypted profiles, optional coordinates, bounded images and demo identity data. Profile contact/address editing and collector area/vehicle editing use the backend.
- Resident/collector dashboards, selection, pickup form/detail, arrival confirmation, weighing controls, transaction summary, profile and history filters. History uses server update timestamps in WIB, not invented completion times.
- Arrival is manually confirmed; the map is illustrative. No real GPS route/ETA, chat, dispatch, partner verification, cash withdrawal, or rewards are claimed.

## Verification

- 19 backend tests passed on 13 November (10 account + 9 pickup tests), including rejection of OTP login and legacy login challenges. Login requires phone/email + password.
- `scripts/backend-smoke.py` passed real local HTTP registration, OTP/replay, phone sign-in, pickup idempotency, collector acceptance/journey/arrival/weighing, resident confirmation and logout invalidation on 13 November.
- Android debug build and lint passed on 13 November: zero lint errors, 10 warnings in the latest report. Release signing/public deployment are not completed.
- Latest APK installed on Samsung SM-M325FV, Android 13/API33, via USB reverse. Existing device density preserved; app data not cleared.
- Device UI passed welcome -> optional-permission skip -> role selection, and resident registration -> local OTP -> authenticated dashboard.
- Device testing found an OTP asset-reference crash. Missing references were replaced by documented shared account artwork; build and resident OTP flow then passed. `scripts/check-assets.py` checks manifest/native asset references and passes.
- Collector registration/local OTP, reset/password/phone login, backend profile edit, full pickup UI and relaunch persistence passed on the physical phone. A scenario recorded 3.5 kg actual weight and 1550 simulated points.
- Independent review found missing CTA arrows, cramped registration back/progress, overlapping consent hit areas, missing selection semantics, wrong collector shortcut assets/panel geometry, profile text overlap, new-password field geometry and weighing CTA spacing. These fixes are in the latest installed APK; recaptures/verdict are in progress.
- The 1.3 font-scale/night-mode pass revealed overlaps on account screens and profile. The latest build adds a flowing account layout for enlarged text, measured profile text and a non-overlapping redeem action while preserving default Figma geometry. Recapture verification is in progress. Original device font/night settings were restored after testing.
- Device harness rejects failed/stale `uiautomator dump` output. OTP checks wait for the resend countdown to finish because Android's hierarchy dumper requires UI idle. No codes/passwords/tokens are printed.

## Design evidence and remaining fidelity gaps

Cached high-fidelity references cover resident home/selection/orders/profile, collector dashboard/detail, weighing, new password, splash/welcome/permissions/role choice, resident registration, account lookup and recovery email. References and actual phone captures are local in ignored `.figma-cache/` and `.impeccable/review/`.

Figma MCP quota is exhausted. Do not repeat known quota-failing calls. Public browser viewing showed collector registration but did not provide detailed exports; no account, sharing or source design was changed. Exact collector registration/profile, OTP/reset-success, arrival, summary/history assets/comparison remain incomplete. Metadata and existing original assets support functionality, but do not establish exact fidelity. OTP/reset success share the downloaded recovery icon and account wave; arrival shares the existing address icon. Original frame exports and corresponding SVG/PNG assets, or restored quota, are needed to close those visual gaps.

Existing `DESIGN.md`, `.impeccable/design.json` and the native surface brief preserve the original light system. Their prior review scope was resident home/selection only. New evidence is being checked by the independent finish reviewer. No HTML/CSS detector is applicable to native Views. Tablet testing is not verified.

## Local operational notes

- Backend listens on loopback port 8080; phone build uses `-PrecygoApiUrl=http://127.0.0.1:8080` plus ADB reverse. Default emulator build uses `10.0.2.2`.
- Credentials are ignored in `backend/data/demo-accounts.json`; synthetic UI accounts in `backend/data/device-accounts.json`; HTTP smoke accounts in `backend/data/smoke-account.json`; OTP messages in `backend/data/outbox`.
- Do not print/upload `.env`, credentials, tokens, OTPs or private photos. Use synthetic registration/identity data only.
- Keep `OTP_SIGNING_KEY`: encrypted profiles depend on it. Do not rotate it without a migration.
- The user authorized GitHub publication with separate feature commits after pausing implementation. Publication commits use their actual dates; see Git history for current hashes. No public backend deployment or submission hash is designated. Submission freeze in the attached rules still needs official confirmation.
