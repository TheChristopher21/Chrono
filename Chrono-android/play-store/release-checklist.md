# Google Play Release Checklist

## Build

- Build signed release AAB:

```powershell
.\gradlew.bat bundleRelease "-PchronoVersionCode=8" "-PchronoVersionName=0.1.8"
```

- Upload file:

```text
app/build/outputs/bundle/release/app-release.aab
```

- Confirm release values:

```text
applicationId = ch.chronologisch.app
API_BASE_URL = https://api.chrono-logisch.ch
DEMO_LOGIN_ENABLED = false
```

## Keystore

- Keep `keystores/chrono-upload.jks` backed up outside Git.
- Keep `local.properties` backed up outside Git or store its secrets in a password manager.
- Do not upload debug APKs to Play Console.

## Store Listing

- App name: Chrono
- Category: Business
- Short description: see `listing/de-DE.md` and `listing/en-US.md`
- Full description: see `listing/de-DE.md` and `listing/en-US.md`
- Privacy policy: https://chrono-logisch.ch/datenschutz
- Contact email: info@chrono-logisch.ch

## Assets

- App icon: `assets/app-icon-512.png`
- Feature graphic: `assets/feature-graphic-1024x500.png`
- Phone screenshots: `screenshots/phone/`

## Policy

- Complete App access declaration from `app-access.md` before submitting a release.
- Complete Data safety form from `data-safety.md`.
- Confirm data deletion contact/process.
- Confirm whether production backend uses external subprocessors.
- Confirm whether payroll and accounting data categories need additional disclosure.
- Confirm target countries and age rating before production rollout.

Official references:

- Preview assets: https://support.google.com/googleplay/android-developer/answer/1078870
- Data safety: https://support.google.com/googleplay/android-developer/answer/10787469
- Privacy policy/user data: https://support.google.com/googleplay/android-developer/answer/16329168
- Account deletion: https://support.google.com/googleplay/android-developer/answer/13327111

## Rollout

- Start with Internal testing.
- Add at least one tester account that can log into production backend.
- Verify login, time tracking, absences, admin navigation and logout on a physical Android device.
- Promote to Closed testing only after backend production environment is ready.
