# Recygo product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Kotlin, requested by the user. Implementation choice: native Android Views, Kotlin/JVM HTTP backend, SQLite. The small dependency set supports a local end-to-end demo without a paid service.

## Users

Residents requesting waste pickup and collectors processing requests, as shown in Figma. Household/student targeting remains a research hypothesis.

## Product Purpose

Support waste selection, pickup requests, collector acceptance, weighing, resident confirmation, and persisted history. The user explicitly prioritized pickup with a backend.

## Capabilities and Constraints

MVP demonstration with synthetic accounts, no real dispatch or verified partner. Points are simulated and cannot be redeemed. Collect only pickup address and notes; no continuous location tracking, ID-card uploads, selfie collection, payments, or paid AI APIs. A backend governs identity, ownership, state transitions, and points.

## Brand Commitments

Recygo / RecyGo. Indonesian copy. Preserve the supplied Figma design, file 7tPzzgzyPQCkhl6UnuFd98, page 0:1. Access succeeded on 12 November 2026. Adapt layout to Android insets, font scaling, and real data; do not replicate the drawn iOS status/home bars.

## Evidence on Hand

Team roster in README. Primary-source research in docs/RESEARCH.md. Original Figma screens for resident home, waste selection, orders, collector home/detail, weighing, password form, and resident profile. No verified partners, environmental impact results, or operational pricing.

## Product Principles

- Complete the resident-to-collector workflow before adding features.
- Present demo activity and points honestly.
- Preserve requests after server and app restarts.
- Keep personal data scoped to the owner and assigned collector.
