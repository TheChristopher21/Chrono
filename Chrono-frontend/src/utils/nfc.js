const API = (import.meta.env.VITE_API_BASE_URL || "/api") + "/nfc";

export const readNfcBlock = (block) =>
    fetch(`${API}/read/${block}`).then((r) => r.json());

export const writeNfcBlock = (block, hex) =>
    fetch(`${API}/write`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ block, data: hex }),
    }).then((r) => r.json());
