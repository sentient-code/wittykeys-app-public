# Architecture

WittyKeys is split into three mobile surfaces and one backend boundary.

## Mobile Surfaces

- Keyboard: AOSP LatinIME base, custom AI action bar, tone/grammar/translation actions, and chat entry points.
- Overlay: floating assistant bubble, screenshot context, smart reply cards, and cross-app help flows.
- App shell: onboarding, settings, privacy controls, quota/subscription states, and support screens.

## Backend Boundary

The Android app does not ship provider secrets. AI requests, entitlement checks, quota state, and subscription validation are routed through Firebase Cloud Functions in the companion backend repository.

## Reliability Practices

- Manual regression checklist for final release gates.
- UI references and approved screenshots for visual state checks.
- Play Store data-safety and privacy documents kept in version control.
- Debug/release signing and Firebase project config excluded from public source.
