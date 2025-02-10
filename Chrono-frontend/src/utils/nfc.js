const API_BASE_URL = "http://localhost:8080/api/nfc";

export const readNfcBlock = async (block) => {
    try {
        const response = await fetch(`${API_BASE_URL}/read/${block}`);
        const data = await response.json();
        return data.status === "success" ? data.data : null;
    } catch (error) {
        console.error("Fehler beim NFC-Lesen:", error);
        return null;
    }
};

export const writeNfcBlock = async (block, hexData) => {
    try {
        const response = await fetch(`${API_BASE_URL}/write`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ block, data: hexData })
        });

        const data = await response.json();
        return data.status === "success" ? data.message : null;
    } catch (error) {
        console.error("Fehler beim NFC-Schreiben:", error);
        return null;
    }
};
