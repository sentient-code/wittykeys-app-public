# WittyKeys Android - AI Keyboard and Overlay Assistant

WittyKeys is a production Android AI keyboard and floating overlay assistant for bilingual communicators. It combines an AOSP LatinIME-based keyboard, Claude-powered writing actions, screenshot-aware overlay chat, subscription/quota states, and release-quality mobile engineering.

This repository is a sanitized public showcase copy. Production secrets, keystores, Firebase config, local deployment scripts, and internal automation logs are intentionally excluded.

## Live Product

- Google Play: https://play.google.com/store/apps/details?id=project.witty.keys
- Website: https://wittykeys.com
- Case study: https://wittykeys.com/abhishek/case-studies/wittykeys
- Portfolio: https://wittykeys.com/abhishek

## What It Demonstrates

- Android `InputMethodService` keyboard engineering on top of AOSP LatinIME.
- Floating overlay UX using Android accessibility/media-projection style surfaces.
- Mobile-first AI UX: rewrite, grammar, translation, quick replies, AI chat, and contextual overlay assistance.
- Backend-mediated Claude integration with quotas, subscriptions, and entitlement checks.
- Production release discipline: Play Store packaging, privacy/data-safety docs, UI golden references, regression scripts, and manual release checklists.

## Architecture

```text
Android app
  - Keyboard surface: AOSP LatinIME + smart AI action bar
  - Overlay surface: floating bubble, screenshot context, assistant chat
  - App shell: onboarding, settings, privacy controls, quota/subscription states

Backend boundary
  - Firebase Cloud Functions proxy LLM calls and entitlement checks
  - Firestore stores user state, quotas, and subscription status
  - Google Play Billing validates paid access
```

## Curated Source Map

```text
app/src/main/java/project/witty/keys/
  api/              Claude/Firebase API client integration
  app/context/      reply generation, matching, and caching logic
  app/helpers/      action tracking and screen-reading helpers
  app/utils/        tones, quota state, and app utilities
  keyboard/         keyboard AI surfaces and assistant UI
  overlay/          floating overlay assistant and smart reply cards
  latin/            AOSP LatinIME base

docs/
  PRIVACY_POLICY.md
  PLAY_STORE_DATA_SAFETY.md
  PLAY_STORE_RELEASE_BUILD_7_1.md
  BUILD_7_1_FINAL_MANUAL_REGRESSION_CHECKLIST.md
  BUILD_7_1_SPRINT_COMPLETION_REPORT.md
```

## Running Locally

This showcase copy excludes production Firebase and signing files. To build locally, provide your own Firebase config and signing setup.

```bash
./gradlew assembleDebug
./gradlew test
```

Expected missing private files in a clean public clone:

- `app/google-services.json`
- release keystore
- local deployment environment files

## Security and Privacy Notes

- User text is treated as sensitive input.
- AI requests are routed through a backend boundary rather than exposing provider keys in the Android app.
- Final production builds use Firebase/Play Billing entitlement checks and quota enforcement.
- Public repo excludes Firebase project config, signing keys, service credentials, and local deployment files.

## Why This Matters

WittyKeys is the strongest product proof in this portfolio: a live mobile AI application with real Android constraints, privacy-sensitive UX, backend integration, subscription logic, release discipline, and user-facing AI workflows.

## License

Source available for portfolio review. Not licensed for commercial reuse.
