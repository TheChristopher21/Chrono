// src/extendedTestNfcWriteToCard.js
import { NFC } from 'nfc-pcsc';
import { Buffer } from 'buffer';

console.log("[TEST] Starte erweiterten NFC-Test...");

// Standard-Key, der von vielen MIFARE Classic-Karten verwendet wird –
// beachte: Bei sogenannten Schlüsselkarten können individuelle Schlüssel hinterlegt sein!
const DEFAULT_KEY_HEX = 'FFFFFFFFFFFF';
const DEFAULT_KEY = Buffer.from(DEFAULT_KEY_HEX, 'hex');

// Wir testen hier ausschließlich Block 4 (Sektor 1, erster Datenblock)
// Weitere Blöcke können in weiteren Tests ausprobiert werden.
const TEST_BLOCK = 4;
const TEST_USER_ID = "admin";

const nfc = new NFC();

nfc.on('reader', reader => {
    // Filtere den SAM-Reader – dieser unterstützt nicht MIFARE Classic Befehle.
    if (reader.reader.name.includes('SAM')) {
        console.log("[TEST] Ignoriere SAM Reader:", reader.reader.name);
        return;
    }

    console.log(`[TEST] PICC Reader erkannt: ${reader.reader.name}`);
    reader.autoProcessing = false;

    // Hinweis: Falls die Authentifizierung fehlschlägt, könnte es sein,
    // dass die Karte individuell programmierte Schlüssel besitzt.
    console.log("[TEST] Hinweis: Bei Schlüsselkarten können individuelle Schlüssel hinterlegt sein.");
    console.log("[TEST] Bitte prüfen Sie die Herstellerdokumentation oder nutzen Sie Tools wie mfoc/mfcuk, um den tatsächlichen Sektortrailer auszulesen.");
    console.log("[TEST] Weitere Informationen dazu finden Sie auch auf der ACS-Webseite: https://www.acs.com.hk/de/");

    const timeoutId = setTimeout(() => {
        console.warn("[TEST] Timeout: Keine Karte innerhalb von 15 Sekunden erkannt.");
        process.exit(1);
    }, 15000);

    reader.on('card', async card => {
        clearTimeout(timeoutId);
        console.log("[TEST] Karte erkannt:", card);

        try {
            console.log(`[TEST] Versuche Authentifizierung für Block ${TEST_BLOCK} mit KEY_A: ${DEFAULT_KEY_HEX}`);
            await reader.authenticate(TEST_BLOCK, 'KEY_A', DEFAULT_KEY);
            console.log(`[TEST] Authentifizierung in Block ${TEST_BLOCK} erfolgreich.`);

            // Erstelle einen 16-Byte Buffer, der mit Leerzeichen gefüllt wird
            const buffer = Buffer.alloc(16, ' ');
            buffer.write(TEST_USER_ID, 0, 'utf8');
            console.log(`[TEST] Schreibe Buffer (Hex): ${buffer.toString('hex')}`);

            await reader.write(TEST_BLOCK, buffer, 16);
            console.log(`[TEST] Schreiben in Block ${TEST_BLOCK} erfolgreich für userId: ${TEST_USER_ID}`);
            process.exit(0);
        } catch (err) {
            console.error("[TEST] Fehler bei der Authentifizierung/Schreiben:", err.message);
            console.error("[TEST] Mögliche Ursachen:");
            console.error("  - Der verwendete Schlüssel (" + DEFAULT_KEY_HEX + ") entspricht nicht dem auf der Karte hinterlegten Schlüssel.");
            console.error("  - Die Karte ist als Schlüsselkarte individuell programmiert.");
            console.error("  - Der Reader benötigt eventuell ein Firmware-Update oder muss in einem speziellen Modus betrieben werden.");
            console.error("[TEST] Bitte prüfen Sie die Dokumentation des Kartenherstellers und des ACS ACR1252U-MF.");
            process.exit(1);
        }
    });

    reader.on('error', err => {
        console.error("[TEST] Reader-Error:", err);
        process.exit(1);
    });
});

nfc.on('error', err => {
    console.error("[TEST] Globaler NFC-Fehler:", err);
    process.exit(1);
});
