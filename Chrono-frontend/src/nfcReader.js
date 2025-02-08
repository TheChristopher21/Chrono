// src/nfcReader.js
import { NFC } from 'nfc-pcsc';
import axios from 'axios';
import { Buffer } from 'buffer';

// Standard-Key: FFFFFFFFFFFF (wird als Buffer benötigt)
const DEFAULT_KEY_HEX = 'FFFFFFFFFFFF';
const DEFAULT_KEY = Buffer.from(DEFAULT_KEY_HEX, 'hex');

// Mögliche Datenblöcke, in denen geschrieben werden kann (z. B. Sektor 1 und 2)
const POSSIBLE_BLOCKS = [4, 5, 6, 8, 9, 10];

// Erzeuge eine globale NFC-Instanz, die von allen Funktionen genutzt wird
const nfc = new NFC();

export function startNfcReader() {
    console.log("[NFC] Initialisiere NFC...");

    nfc.on('reader', reader => {
        // Filtere den SAM-Reader heraus – dieser unterstützt nicht die MIFARE Classic Befehle
        if (reader.reader.name.includes('SAM')) {
            console.log("[NFC] Ignoriere SAM Reader:", reader.reader.name);
            return;
        }

        console.log("[NFC] PICC Reader erkannt:", reader.reader.name);
        reader.autoProcessing = false;

        reader.on('card', async card => {
            console.log("[NFC] Karte erkannt:", card);
            try {
                console.log("[NFC] Versuche Authentifizierung für Block 4 mit KEY_A:", DEFAULT_KEY_HEX);
                await reader.authenticate(4, 'KEY_A', DEFAULT_KEY);
                console.log("[NFC] Authentifizierung an Block 4 erfolgreich.");
                const data = await reader.read(4, 16);
                let userId = data.toString('utf8').trim();
                console.log("[NFC] Ausgelesener UserID:", userId);
                if (!userId) {
                    console.warn("[NFC] Keine UserID gefunden.");
                    return;
                }
                await axios.post('http://localhost:8080/api/timetracking/work-start', null, {
                    params: { username: userId }
                });
                console.log("[NFC] Work-Start ausgeführt für:", userId);
            } catch (err) {
                console.error("[NFC] Fehler beim Auslesen:", err);
            }
        });

        reader.on('error', err => {
            console.error("[NFC] Reader-Error:", err);
        });

        reader.on('end', () => {
            console.log("[NFC] Karte entfernt / Reader idle.");
        });
    });

    nfc.on('error', err => {
        console.error("[NFC] Globaler Fehler:", err);
    });
}

/**
 * Schreibt die übergebene userId in einen der möglichen Datenblöcke.
 */
export async function writeUserIdToCard(userId) {
    console.log("[NFC] Warte auf Karte zum Beschreiben...");

    return new Promise((resolve, reject) => {
        nfc.on('reader', reader => {
            // Nur den PICC-Reader verwenden – SAM ignorieren
            if (reader.reader.name.includes('SAM')) {
                console.log("[NFC] Ignoriere SAM Reader:", reader.reader.name);
                return;
            }

            console.log("[NFC] Reader bereit zum Schreiben:", reader.reader.name);
            reader.autoProcessing = false;

            // Timeout: Falls innerhalb von 15 Sekunden keine Karte präsentiert wird
            const timeoutId = setTimeout(() => {
                console.warn("[NFC] Timeout: Keine Karte innerhalb von 15 Sekunden erkannt.");
                reject(new Error("Timeout: Keine Karte erkannt"));
            }, 15000);

            reader.on('card', async card => {
                clearTimeout(timeoutId);
                console.log("[NFC] Karte zum Beschreiben erkannt:", card);
                let authenticatedBlock = null;
                let usedKeyType = null;
                let lastError = null;

                // Versuche alle möglichen Blöcke mit KEY_A und KEY_B
                for (const block of POSSIBLE_BLOCKS) {
                    for (const keyType of ['KEY_A', 'KEY_B']) {
                        try {
                            console.log(`[NFC] Versuche Authentifizierung für Block ${block} mit ${keyType}: ${DEFAULT_KEY_HEX}`);
                            await reader.authenticate(block, keyType, DEFAULT_KEY);
                            console.log(`[NFC] Authentifizierung in Block ${block} mit ${keyType} erfolgreich.`);
                            authenticatedBlock = block;
                            usedKeyType = keyType;
                            break;
                        } catch (err) {
                            console.warn(`[NFC] Authentifizierung in Block ${block} mit ${keyType} fehlgeschlagen: ${err.message}`);
                            lastError = err;
                        }
                    }
                    if (authenticatedBlock !== null) {
                        console.log(`[NFC] Erfolgreich authentifiziert in Block ${authenticatedBlock} (mit ${usedKeyType}).`);
                        break;
                    }
                }

                if (authenticatedBlock === null) {
                    console.error("[NFC] Keine Authentifizierung erfolgreich. Letzter Fehler:", lastError.message);
                    reject(lastError);
                    return;
                }

                try {
                    // Erstelle einen 16-Byte Buffer, der mit Leerzeichen aufgefüllt wird
                    const buffer = Buffer.alloc(16, ' ');
                    buffer.write(userId, 0, 'utf8');
                    console.log(`[NFC] Buffer, der geschrieben werden soll (Hex): ${buffer.toString('hex')}`);

                    await reader.write(authenticatedBlock, buffer, 16);
                    console.log(`[NFC] Karte erfolgreich beschrieben mit ${userId} in Block ${authenticatedBlock} (verwendet ${usedKeyType}).`);
                    resolve(true);
                } catch (writeError) {
                    console.error("[NFC] Fehler beim Schreiben:", writeError);
                    reject(writeError);
                }
            });

            reader.on('error', err => {
                console.error("[NFC] Writer Reader-Error:", err);
                reject(err);
            });

            reader.on('end', () => {
                console.log("[NFC] Karte entfernt / Writer idle.");
            });
        });

        nfc.on('error', err => {
            console.error("[NFC] Globaler Fehler beim writeUserId:", err);
            reject(err);
        });
    });
}
