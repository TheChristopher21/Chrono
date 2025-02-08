// testNfcWriteStandard.js
import { NFC } from 'nfc-pcsc';
import { Buffer } from 'buffer';

console.log("[TEST] Starte MIFARE Classic Schreibtest mit Standard-Schlüsseln...");

// Standard-Key: KEY_A = FFFFFFFFFFFF
const DEFAULT_KEY_HEX = 'FFFFFFFFFFFF';
const DEFAULT_KEY = Buffer.from(DEFAULT_KEY_HEX, 'hex');

// Wir testen hier Block 4 (Sektor 1, erster Datenblock)
// Der zu schreibende Text ("admin") wird in einen 16-Byte-Block geschrieben.
const TEST_BLOCK = 4;
const TEST_USER_ID = "admin";

const nfc = new NFC();

nfc.on('reader', reader => {
    // SAM-Reader ignorieren – nur den PICC-Teil verwenden
    if (reader.reader.name.includes('SAM')) {
        console.log("[TEST] Ignoriere SAM Reader:", reader.reader.name);
        return;
    }

    console.log(`[TEST] Verwende PICC Reader: ${reader.reader.name}`);
    reader.autoProcessing = false;

    // Setze ein Timeout, falls innerhalb von 15 Sekunden keine Karte präsentiert wird
    const timeoutId = setTimeout(() => {
        console.error("[TEST] Timeout: Keine Karte innerhalb von 15 Sekunden erkannt.");
        process.exit(1);
    }, 15000);

    reader.on('card', async card => {
        clearTimeout(timeoutId);
        console.log("[TEST] Karte erkannt:", card);

        try {
            // Authentifizierung versuchen: Block 4, KEY_A mit Standard-Schlüssel
            console.log(`[TEST] Versuche Authentifizierung für Block ${TEST_BLOCK} mit KEY_A: ${DEFAULT_KEY_HEX}`);
            await reader.authenticate(TEST_BLOCK, 'KEY_A', DEFAULT_KEY);
            console.log(`[TEST] Authentifizierung für Block ${TEST_BLOCK} erfolgreich.`);

            // Erstelle einen 16-Byte Buffer, der mit Leerzeichen gefüllt ist
            const dataBuffer = Buffer.alloc(16, ' ');
            dataBuffer.write(TEST_USER_ID, 0, 'utf8');
            console.log(`[TEST] Zu schreibender Buffer (Hex): ${dataBuffer.toString('hex')}`);

            // Sende den WRITE-Befehl (0xA0 wird intern verwendet) zum Schreiben des Blocks
            await reader.write(TEST_BLOCK, dataBuffer, 16);
            console.log(`[TEST] Schreiben in Block ${TEST_BLOCK} erfolgreich für userId: ${TEST_USER_ID}`);
            process.exit(0);
        } catch (err) {
            console.error("[TEST] Fehler bei Authentifizierung/Schreiben:", err.message);
            process.exit(1);
        }
    });

    reader.on('error', err => {
        console.error("[TEST] Reader-Fehler:", err);
    });
});

nfc.on('error', err => {
    console.error("[TEST] Globaler NFC-Fehler:", err);
});
