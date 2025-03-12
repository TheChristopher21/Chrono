package com.chrono.chrono.services;

import com.chrono.chrono.exceptions.NfcNoCardException;
import org.springframework.stereotype.Service;

import javax.smartcardio.*;
import java.util.List;

@Service
public class NfcService {

    public String readBlock(int blockNumber) throws Exception {
        TerminalFactory factory = TerminalFactory.getDefault();
        List<CardTerminal> terminals = factory.terminals().list();

        if (terminals.isEmpty()) {
            throw new NfcNoCardException("Kein NFC-Leser gefunden");
        }

        CardTerminal terminal = terminals.get(0);
        Card card;
        try {
            card = terminal.connect("*");
        } catch (CardNotPresentException cne) {
            throw new NfcNoCardException("Keine Karte erkannt");
        } catch (Exception e) {
            throw new Exception("Verbindungsfehler mit dem NFC-Reader: " + e.getMessage());
        }

        CardChannel channel = card.getBasicChannel();

        // Versuche, den Standard-Schlüssel für den betreffenden Sektor zu laden.
        // Für Block 4 (Sektor 1) nehmen wir hier als Beispiel den Key "FFFFFFFFFFFF".
        // Du kannst auch weitere Schlüssel versuchen, falls nötig.
        String[] possibleKeys = { "FFFFFFFFFFFF", "A0A1A2A3A4A5" };
        boolean authSuccess = false;
        Exception authException = null;
        for (String key : possibleKeys) {
            try {
                byte[] keyBytes = hexStringToByteArray(key);
                byte[] loadKeyCommand = new byte[]{
                        (byte)0xFF, (byte)0x82, 0x00, 0x00, 0x06,
                        keyBytes[0], keyBytes[1], keyBytes[2], keyBytes[3], keyBytes[4], keyBytes[5]
                };
                ResponseAPDU loadKeyResponse = channel.transmit(new CommandAPDU(loadKeyCommand));
                if (loadKeyResponse.getSW() != 0x9000) {
                    continue; // Ladeversuch fehlgeschlagen, versuche den nächsten Schlüssel
                }
                // Sende Authentifizierung – für Key A (0x60)
                byte[] authCommand = new byte[]{
                        (byte)0xFF, (byte)0x86, 0x00, 0x00,
                        0x05,
                        0x01, 0x00, (byte) blockNumber, 0x60, 0x00
                };
                ResponseAPDU authResponse = channel.transmit(new CommandAPDU(authCommand));
                if (authResponse.getSW() == 0x9000) {
                    authSuccess = true;
                    break;
                } else {
                    authException = new Exception("Authentifizierung fehlgeschlagen mit Key: " + key + " SW: " + Integer.toHexString(authResponse.getSW()));
                }
            } catch (Exception e) {
                authException = e;
            }
        }

        if (!authSuccess) {
            card.disconnect(false);
            if (authException != null) {
                throw authException;
            } else {
                throw new Exception("Keine der Authentifizierungen war erfolgreich!");
            }
        }

        // Block lesen
        byte[] readCommand = new byte[]{
                (byte)0xFF, (byte)0xB0, 0x00, (byte) blockNumber, 0x10
        };
        ResponseAPDU response = channel.transmit(new CommandAPDU(readCommand));
        card.disconnect(false);

        if (response.getSW() == 0x9000) {
            return bytesToHex(response.getData());
        } else {
            throw new Exception("Fehler beim Lesen des Blocks!");
        }
    }


    public String writeSector0Block1(String hexData) throws Exception {
        if (hexData.length() != 32) {
            throw new Exception("Ungültige Länge! 32 Hex-Zeichen (16 Bytes) erwartet.");
        }
        int blockNumber = 1; // Block 1 in Sektor 0

        TerminalFactory factory = TerminalFactory.getDefault();
        List<CardTerminal> terminals = factory.terminals().list();
        if (terminals.isEmpty()) {
            throw new NfcNoCardException("Kein NFC-Leser gefunden");
        }
        CardTerminal terminal = terminals.get(0);

        // Versuch 1: mit Key A = "A0A1A2A3A4A5"
        String keyAString = "A0A1A2A3A4A5";
        try {
            String result = writeBlockWithKey(terminal, blockNumber, hexData, keyAString, 0x60);
            return result;
        } catch (Exception e) {
            // Falls Key A nicht funktioniert, versuche Key B
        }

        // Versuch 2: mit Key B = "FFFFFFFFFFFF"
        String keyBString = "FFFFFFFFFFFF";
        String result = writeBlockWithKey(terminal, blockNumber, hexData, keyBString, 0x61);
        return result;
    }

    /**
     * Hilfsmethode, die den Schreibvorgang mit einem bestimmten Schlüssel durchführt.
     *
     * @param terminal   der NFC-Terminal
     * @param blockNumber zu schreibender Block
     * @param hexData    zu schreibende Daten (32 Hex-Zeichen)
     * @param keyString  der zu ladende Schlüssel als Hex-String (z. B. "A0A1A2A3A4A5")
     * @param keyCode    Authentifizierungscode: 0x60 für Key A, 0x61 für Key B
     */
    private String writeBlockWithKey(CardTerminal terminal, int blockNumber, String hexData, String keyString, int keyCode) throws Exception {
        Card card;
        try {
            card = terminal.connect("*");
        } catch (CardNotPresentException cne) {
            throw new NfcNoCardException("Keine Karte erkannt");
        } catch (Exception e) {
            throw new Exception("Verbindungsfehler beim Writer: " + e.getMessage());
        }
        CardChannel channel = card.getBasicChannel();

        byte[] keyBytes = hexStringToByteArray(keyString);
        byte[] loadKeyCommand = new byte[]{
                (byte) 0xFF, (byte) 0x82, 0x00, 0x00, 0x06,
                keyBytes[0], keyBytes[1], keyBytes[2], keyBytes[3], keyBytes[4], keyBytes[5]
        };
        ResponseAPDU loadKeyResponse = channel.transmit(new CommandAPDU(loadKeyCommand));
        if (loadKeyResponse.getSW() != 0x9000) {
            card.disconnect(false);
            throw new Exception("Laden des Schlüssels " + keyString + " fehlgeschlagen! SW: " + Integer.toHexString(loadKeyResponse.getSW()));
        }

        byte[] authCommand = new byte[]{
                (byte) 0xFF, (byte) 0x86, 0x00, 0x00,
                0x05,
                0x01, 0x00, (byte) blockNumber, (byte) keyCode, 0x00
        };
        ResponseAPDU authResponse = channel.transmit(new CommandAPDU(authCommand));
        if (authResponse.getSW() != 0x9000) {
            card.disconnect(false);
            throw new Exception("Authentifizierung fehlgeschlagen mit Key " + keyString + "! SW: " + Integer.toHexString(authResponse.getSW()));
        }

        byte[] dataBytes = hexStringToByteArray(hexData);
        byte[] writeCommand = new byte[21];
        writeCommand[0] = (byte) 0xFF;
        writeCommand[1] = (byte) 0xD6;
        writeCommand[2] = 0x00;
        writeCommand[3] = (byte) blockNumber;
        writeCommand[4] = 0x10;
        System.arraycopy(dataBytes, 0, writeCommand, 5, 16);

        ResponseAPDU response = channel.transmit(new CommandAPDU(writeCommand));
        card.disconnect(false);

        if (response.getSW() == 0x9000) {
            return "Block " + blockNumber + " erfolgreich mit Key " + keyString + " beschrieben!";
        } else {
            throw new Exception("Fehler beim Schreiben des Blocks mit Key " + keyString + "! SW: " + Integer.toHexString(response.getSW()));
        }
    }

    /**
     * Die bestehende writeBlock-Methode – ohne Schlüssel-Iteration.
     */
    public String writeBlock(int blockNumber, String hexData) throws Exception {
        if (hexData.length() != 32) {
            throw new Exception("Ungültige Länge! 32 Hex-Zeichen (16 Bytes) erwartet.");
        }

        TerminalFactory factory = TerminalFactory.getDefault();
        List<CardTerminal> terminals = factory.terminals().list();

        if (terminals.isEmpty()) {
            throw new NfcNoCardException("Kein NFC-Leser gefunden");
        }

        CardTerminal terminal = terminals.get(0);
        Card card;
        try {
            card = terminal.connect("*");
        } catch (CardNotPresentException cne) {
            throw new NfcNoCardException("Keine Karte erkannt");
        } catch (Exception e) {
            throw new Exception("Verbindungsfehler beim Writer: " + e.getMessage());
        }

        CardChannel channel = card.getBasicChannel();

        byte[] authCommand = new byte[]{
                (byte) 0xFF, (byte) 0x86, 0x00, 0x00,
                0x05,
                0x01, 0x00, (byte) blockNumber, 0x60, 0x00
        };
        ResponseAPDU authResponse = channel.transmit(new CommandAPDU(authCommand));
        if (authResponse.getSW() != 0x9000) {
            throw new Exception("Authentifizierung fehlgeschlagen!");
        }

        byte[] dataBytes = hexStringToByteArray(hexData);
        byte[] writeCommand = new byte[21];
        writeCommand[0] = (byte) 0xFF;
        writeCommand[1] = (byte) 0xD6;
        writeCommand[2] = 0x00;
        writeCommand[3] = (byte) blockNumber;
        writeCommand[4] = 0x10;
        System.arraycopy(dataBytes, 0, writeCommand, 5, 16);

        ResponseAPDU response = channel.transmit(new CommandAPDU(writeCommand));
        card.disconnect(false);

        if (response.getSW() == 0x9000) {
            return "Block erfolgreich geschrieben!";
        } else {
            throw new Exception("Fehler beim Schreiben des Blocks!");
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    private byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) (
                    (Character.digit(s.charAt(i), 16) << 4)
                            + Character.digit(s.charAt(i + 1), 16)
            );
        }
        return data;
    }
}
