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

        // Kein NFC-Leser angeschlossen?
        if (terminals.isEmpty()) {
            // => Wirft "no card"-Exception => NfcController fängt ab => 200 + status=no-card
            throw new NfcNoCardException("Kein NFC-Leser gefunden");
        }

        CardTerminal terminal = terminals.get(0);
        Card card;
        try {
            // Versuche Karte zu verbinden
            card = terminal.connect("*");
        } catch (CardNotPresentException cne) {
            // => Keine Karte liegt auf dem Leser
            throw new NfcNoCardException("Keine Karte erkannt");
        } catch (Exception e) {
            // => Irgendein anderer Fehler
            throw new Exception("Verbindungsfehler mit dem NFC-Reader: " + e.getMessage());
        }

        CardChannel channel = card.getBasicChannel();

        // Authentifizierung (Key A = 0x60)
        byte[] authCommand = new byte[]{
                (byte)0xFF, (byte)0x86, 0x00, 0x00,
                0x05,
                0x01, 0x00, (byte) blockNumber, 0x60, 0x00
        };
        ResponseAPDU authResponse = channel.transmit(new CommandAPDU(authCommand));
        if (authResponse.getSW() != 0x9000) {
            throw new Exception("Authentifizierung fehlgeschlagen!");
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
                (byte)0xFF, (byte)0x86, 0x00, 0x00,
                0x05,
                0x01, 0x00, (byte) blockNumber, 0x60, 0x00
        };
        ResponseAPDU authResponse = channel.transmit(new CommandAPDU(authCommand));
        if (authResponse.getSW() != 0x9000) {
            throw new Exception("Authentifizierung fehlgeschlagen!");
        }

        byte[] dataBytes = hexStringToByteArray(hexData);
        byte[] writeCommand = new byte[21];
        writeCommand[0] = (byte)0xFF;
        writeCommand[1] = (byte)0xD6;
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
        // "41 42 43 ..." -> "41 42 43 ..."
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
