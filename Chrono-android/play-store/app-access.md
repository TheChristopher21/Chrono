# Google Play App Access

Wenn Google Play `Anmeldedaten fehlen` meldet, muss das in der Play Console unter `App-Inhalte` -> `App-Zugriff` gepflegt werden. Die App braucht Login-Daten, deshalb reicht ein neuer AAB-Upload allein nicht aus.

## Einstellung

- Auswahl: `Alle oder einige Funktionen sind eingeschraenkt`
- Zugriffstyp: `Login mit Benutzername und Passwort`
- Name der Zugangsdaten: `Chrono Review Account`

## Einzutragen

```text
Benutzername: <GOOGLE_REVIEW_USERNAME>
Passwort: <GOOGLE_REVIEW_PASSWORD>
```

## Anleitung fuer Google

```text
1. App oeffnen.
2. Auf dem Login-Screen Benutzername und Passwort eingeben.
3. Anmelden antippen.
4. Die Bereiche Zeit, Abwesenheit, Lohn, Profil und Admin sind ueber die untere Navigation und die Bereichsauswahl erreichbar.
5. Zum Abmelden oben im Dashboard das Logout-Symbol verwenden.
```

## Empfehlung

Lege im produktiven Backend einen stabilen Testnutzer fuer Google an, z. B. `google-review`, und gib ihm nur die Rechte, die Google pruefen soll. Wenn Google auch Admin-Funktionen pruefen soll, hinterlege zusaetzlich einen Admin-Testnutzer in derselben App-Zugriffserklaerung oder erweitere die Anleitung mit den Admin-Zugangsdaten.

Der Superadmin-Zugang sollte nicht als allgemeiner Google-Review-Zugang verwendet werden, ausser du willst explizit, dass Google die Superadmin-Ansicht prueft.
