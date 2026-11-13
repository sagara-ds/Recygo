# Physical Android checks

Date: 13 November 2026 (WIB). Samsung SM-M325FV, Android 13/API33, physical 1080 x 2400. Existing density override338 was preserved. No emulator/tablet evidence is claimed.

**Paused by user.** Latest APK builds but is not installed. Final password masking, CTA positioning and profile-divider fixes still need device verification; see [HANDOFF.md](HANDOFF.md). The installed phone version predates the masking fix.

The backend runs locally on loopback8080; USB reverse connects the debug APK. Synthetic accounts and pickup data are retained locally for inspection. No real pickup, email, WhatsApp message, call or payment is sent.

| Scenario | Observed result |
| --- | --- |
| Welcome, location and notification explanations | Skip path reaches role choice without mandatory permissions |
| Resident registration | Two steps, local OTP and authenticated home passed |
| Collector registration | Three steps, synthetic NIK, local OTP and authenticated home passed |
| Optional profile image | Built-in demo image selected; no private camera/gallery content used |
| Account selection semantics | Role, vehicle and accepted-material choices expose checked state in native accessibility hierarchy |
| Password recovery | Local OTP, new password and phone/password login passed |
| Incorrect OTP | Recoverable error followed by correct-code retry passed |
| Profile editing | Name saved by backend and reflected in profile |
| Pickup | Selection, request, collector acceptance, journey, arrival, weighing, resident completion and history passed |
| Weight/points | One scenario recorded 2.5kg plastic + 1kg paper =1550 simulated points |
| Relaunch | Encrypted session and backend order results remained available |
| Enlarged text/night mode | Font1.3 found initial overlap; flowing account layout and measured text groups rebuilt and recaptured; original device settings restored |
| Password masking | Default transformation bug found and fixed; affected synthetic password reset; final populated-field check pending |

Build validation: 19 backend tests pass, HTTP smoke passes, debug APK and Android lint build successfully with zero errors and10 warnings. Warnings include target/tooling updates and localization guidance; this is not a release-readiness claim. `scripts/check-assets.py` passes.

`scripts/device-account-smoke.py` exercises native UI. It rejects failed/stale hierarchy dumps, avoids logging command arguments containing test secrets, blocks captures with populated OTPs/readable passwords, and saves captures only under ignored `.impeccable/review/`. The Android hierarchy tool requires idle UI; OTP checks wait for the resend countdown. Never run two hierarchy-dump scripts concurrently.

Visual findings are reviewed independently against cached Figma evidence. `DESIGN.md` and its sidecar are preserved as the incumbent light design system. Exact fidelity remains unverified for missing exports documented in `DESIGN_SOURCE.md`; reviewer fix verification is still in progress. Normal-font source geometry and enlarged-text legibility are separate checks.

Not covered: a TalkBack user session, real camera/gallery selection, actual permission grant/denial system dialogs, tablet, broad Android-version matrix, load testing, independent security audit or release distribution.
