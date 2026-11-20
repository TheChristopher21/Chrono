package com.chrono.chrono.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NfcServiceBlockValidationTest {
    @Test
    void rejectsByteAliasesBeforeAnyHardwareAccess() {
        NfcService service = new NfcService();
        for (int invalid : new int[] {257, -255, -1, 256, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> service.readBlock(invalid));
            // Validation must precede even payload parsing, so no hardware path
            // can be reached with an aliased block and null/invalid data.
            assertThrows(IllegalArgumentException.class, () -> service.writeBlock(invalid, null));
        }
    }

    @Test
    void preservesEveryRepresentableUnsignedByteBlock() {
        for (int block = 0; block <= 255; block++) {
            int valid = block;
            assertDoesNotThrow(() -> NfcService.validateBlockNumber(valid));
        }
    }
}
