// src/autoKeyTestNfcWriteToCard.js
import { NFC } from 'nfc-pcsc';
import { Buffer } from 'buffer';

console.log("[TEST] Starte automatisierten NFC-Schlüssel-Test...");

// Liste möglicher Schlüsselvarianten (kannst du erweitern)
const POSSIBLE_KEYS = [
    { keyType: 'KEY_A', keyHex: 'FFFFFFFFFFFF' },
    { keyType: 'KEY_B', keyHex: 'FFFFFFFFFFFF' },
    { keyType: 'KEY_A', keyHex: 'A0A1A2A3A4A5' },
    { keyType: 'KEY_B', keyHex: 'A0A1A2A3A4A5' },
    { keyType: 'KEY_A', keyHex: 'D3F7D3F7D3F7' },
    { keyType: 'KEY_B', keyHex: 'D3F7D3F7D3F7' },
    { keyType: 'KEY_A', keyHex: '000000000000' },
    { keyType: 'KEY_B', keyHex: '000000000000' },
    // Falls dir weitere Schlüssel bekannt sind, hier ergänzen…
];

const POSSIBLE_KEYS_WITH_BUFFERS = POSSIBLE_KEYS.map(item => ({
    keyType: item.keyType,
    key: Buffer.from(item.keyHex, 'hex'),
    keyHex: item.keyHex
}));

// Wir testen hier Block 4 (Sektor 1, erster Datenblock)
const TEST_BLOCK = 4;
const TEST_USER_ID = "admin";

const nfc = new NFC();

nfc.on('reader', reader => {
    // SAM-Reader ignorieren
    if (reader.reader.name.includes('SAM')) {
        console.log("[TEST] Ignoriere SAM Reader:", reader.reader.name);
        return;
    }

    console.log(`[TEST] PICC Reader erkannt: ${reader.reader.name}`);
    reader.autoProcessing = false;

    const timeoutId = setTimeout(() => {
        console.warn("[TEST] Timeout: Keine Karte innerhalb von 15 Sekunden erkannt.");
        process.exit(1);
    }, 15000);

    reader.on('card', async card => {
        clearTimeout(timeoutId);
        console.log("[TEST] Karte erkannt:", card);

        let authenticatedBlock = null;
        let usedKeyType = null;
        let usedKeyHex = null;
        let lastError = null;

        // Iteriere über die möglichen Schlüssel
        for (const { keyType, key, keyHex } of POSSIBLE_KEYS_WITH_BUFFERS) {
            try {
                console.log(`[TEST] Versuche Authentifizierung für Block ${TEST_BLOCK} mit ${keyType}: ${keyHex}`);
                await reader.authenticate(TEST_BLOCK, keyType, key);
                console.log(`[TEST] Authentifizierung für Block ${TEST_BLOCK} erfolgreich mit ${keyType}: ${keyHex}`);
                authenticatedBlock = TEST_BLOCK;
                usedKeyType = keyType;
                usedKeyHex = keyHex;
                break;
            } catch (err) {
                console.warn(`[TEST] Authentifizierung für Block ${TEST_BLOCK} mit ${keyType}: ${keyHex} fehlgeschlagen: ${err.message}`);
                lastError = err;
            }
        }

        if (authenticatedBlock === null) {
            console.error("[TEST] Keine Authentifizierung erfolgreich. Letzter Fehler:", lastError.message);
            console.error("[TEST] Bitte prüfen Sie mit externen Tools (mfoc/mfcuk) oder kontaktieren Sie den Kartenhersteller, um den korrekten Schlüssel zu ermitteln.");
            process.exit(1);
            return;
        }

        try {
            const buffer = Buffer.alloc(16, ' ');
            buffer.write(TEST_USER_ID, 0, 'utf8');
            console.log(`[TEST] Schreibe Buffer (Hex): ${buffer.toString('hex')}`);
            await reader.write(authenticatedBlock, buffer, 16);
            console.log(`[TEST] Schreiben erfolgreich für Block ${authenticatedBlock} mit ${usedKeyType} (${usedKeyHex}) für userId: ${TEST_USER_ID}`);
            process.exit(0);
        } catch (writeError) {
            console.error("[TEST] Fehler beim Schreiben:", writeError.message);
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
