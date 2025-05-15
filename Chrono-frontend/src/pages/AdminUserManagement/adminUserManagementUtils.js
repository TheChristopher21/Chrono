// adminUserManagementUtils.js

export const STANDARD_COLORS = [
    "#FF5733", "#33FF57", "#3357FF", "#F0FF33",
    "#FF33F0", "#33FFF0", "#FF8C00", "#8A2BE2",
    "#FF1493", "#00BFFF", "#ADFF2F", "#FFD700",
    "#FF4500", "#00FA9A", "#7B68EE", "#FF6347"
];

export const defaultWeeklySchedule = {
    monday: 8.0,
    tuesday: 8.0,
    wednesday: 8.0,
    thursday: 8.0,
    friday: 8.0,
    saturday: 0.0,
    sunday: 0.0
};

// Beispiel-Hilfsfunktionen:
export function stringToHex16(text) {
    let asciiData = text.slice(0, 16);
    asciiData = asciiData.padEnd(16, '\0');
    let hexResult = '';
    for (let i = 0; i < asciiData.length; i++) {
        const code = asciiData.charCodeAt(i);
        hexResult += code.toString(16).padStart(2, '0');
    }
    return hexResult.toUpperCase();
}
