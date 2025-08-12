import React, { useEffect, useMemo, useState } from "react";
import "../../styles/AdminScheduleRulesPageScoped.css";

/**
 * AdminScheduleRulesPage
 * - Pflegt globale Schichten (Bezeichnung, Schlüssel, Start-, Endzeit, Aktiv)
 * - Zeitfelder werden als HH:MM normalisiert (Fix: leere Anzeige bei "17:0")
 *
 * Endpoints (anpassen, falls nötig):
 *   GET    /admin/schedule/rules           → Liste aller Regeln
 *   POST   /admin/schedule/rules           → neue Regel anlegen
 *   PUT    /admin/schedule/rules/:id       → Regel aktualisieren
 */
export default function AdminScheduleRulesPage() {
    const API_BASE = (import.meta && import.meta.env && import.meta.env.VITE_API_URL) ? import.meta.env.VITE_API_URL : "";
    const [rules, setRules] = useState([]);
    const [loading, setLoading] = useState(true);
    const [savingId, setSavingId] = useState(null);
    const endpoint = useMemo(() => `${API_BASE}/admin/schedule/rules`.replace(/\/{2,}/g, "/"), [API_BASE]);

    // -------- Utilities: Zeitformat normalisieren --------
    const pad2 = (n) => String(n).padStart(2, "0");
    const normalizeTime = (t) => {
        if (!t) return "00:00";
        // akzeptiere 1- oder 2-stellige Stunden/Minuten → formatiere zu HH:MM
        const m = /^(\d{1,2}):(\d{1,2})$/.exec(t);
        if (!m) return t; // Browser-Picker liefert sowieso korrekt; hier nur "reparieren"
        const hh = Math.max(0, Math.min(23, parseInt(m[1], 10)));
        const mm = Math.max(0, Math.min(59, parseInt(m[2], 10)));
        return `${pad2(hh)}:${pad2(mm)}`;
    };

    // -------- Daten laden --------
    useEffect(() => {
        let ignore = false;
        (async () => {
            try {
                setLoading(true);
                const res = await fetch(endpoint, { credentials: "include" });
                if (!res.ok) throw new Error(`GET ${endpoint} → ${res.status}`);
                const data = await res.json();

                const normalized = Array.isArray(data) ? data.map((r) => ({
                    id: r.id ?? crypto.randomUUID(),
                    label: r.label ?? "",
                    key: r.key ?? "",
                    startTime: normalizeTime(r.startTime ?? "00:00"),
                    endTime: normalizeTime(r.endTime ?? "00:00"),
                    active: !!r.active,
                })) : [];

                if (!ignore) setRules(normalized);
            } catch (e) {
                console.error(e);
                if (!ignore) setRules([]);
            } finally {
                if (!ignore) setLoading(false);
            }
        })();
        return () => { ignore = true; };
    }, [endpoint]);

    // -------- Handlers --------
    const handleField = (id, field, value) => {
        setRules((prev) =>
            prev.map((r) => (r.id === id ? { ...r, [field]: value } : r))
        );
    };

    const handleBlurTime = (id, field, value) => {
        handleField(id, field, normalizeTime(value));
    };

    const addRule = () => {
        setRules((prev) => [
            ...prev,
            {
                id: crypto.randomUUID(),
                label: "",
                key: "",
                startTime: "08:00",
                endTime: "17:00",
                active: true,
            },
        ]);
    };

    const saveRule = async (rule) => {
        try {
            setSavingId(rule.id);
            const payload = {
                label: rule.label?.trim() ?? "",
                key: (rule.key ?? "").toUpperCase().trim(),
                startTime: normalizeTime(rule.startTime),
                endTime: normalizeTime(rule.endTime),
                active: !!rule.active,
            };

            const method = rule.serverId ? "PUT" : "POST";
            const url = rule.serverId ? `${endpoint}/${encodeURIComponent(rule.serverId)}` : endpoint;

            const res = await fetch(url, {
                method,
                headers: { "Content-Type": "application/json" },
                credentials: "include",
                body: JSON.stringify(payload),
            });

            if (!res.ok) throw new Error(`${method} ${url} → ${res.status}`);

            // Server kann id/serverId zurückgeben → wieder einhängen
            const saved = await res.json().catch(() => ({}));
            setRules((prev) =>
                prev.map((r) =>
                    r.id === rule.id ? { ...r, ...payload, serverId: saved.id ?? rule.serverId ?? rule.id } : r
                )
            );
        } catch (e) {
            console.error(e);
            // Hier kannst du deinen NotificationContext verwenden
            alert("Speichern fehlgeschlagen.");
        } finally {
            setSavingId(null);
        }
    };

    // -------- UI --------
    return (
        <div className="schedule-rules-page scoped-dashboard">
            <div className="dashboard-header">
                <h1>Schicht-Einstellungen</h1>
                <p>Hier können die globalen Schichten und ihre Zeiten für den Dienstplan konfiguriert werden.</p>
            </div>

            <div className="rules-list">
                {loading && (
                    <div className="rule-card">
                        <div className="form-grid" aria-busy="true">
                            <div className="form-group"><label>Bezeichnung</label><div className="skeleton" /></div>
                            <div className="form-group"><label>Schlüssel (z.B. EARLY)</label><div className="skeleton" /></div>
                            <div className="form-group"><label>Startzeit</label><div className="skeleton" /></div>
                            <div className="form-group"><label>Endzeit</label><div className="skeleton" /></div>
                            <div className="checkbox-group"><label>Aktiv</label></div>
                            <div className="form-group-actions"><button disabled>Speichern</button></div>
                        </div>
                    </div>
                )}

                {!loading && rules.map((rule) => (
                    <div className="rule-card" key={rule.id}>
                        <div className="form-grid">
                            {/* Bezeichnung */}
                            <div className="form-group">
                                <label>Bezeichnung</label>
                                <input
                                    type="text"
                                    value={rule.label}
                                    onChange={(e) => handleField(rule.id, "label", e.target.value)}
                                    placeholder="z. B. Frühschicht"
                                />
                            </div>

                            {/* Key */}
                            <div className="form-group">
                                <label>Schlüssel (z.B. EARLY)</label>
                                <input
                                    type="text"
                                    value={rule.key}
                                    onChange={(e) => handleField(rule.id, "key", e.target.value.toUpperCase())}
                                    placeholder="EARLY"
                                />
                            </div>

                            {/* Startzeit */}
                            <div className="form-group">
                                <label>Startzeit</label>
                                <input
                                    type="time"
                                    value={rule.startTime || "00:00"}
                                    onChange={(e) => handleField(rule.id, "startTime", e.target.value)}
                                    onBlur={(e) => handleBlurTime(rule.id, "startTime", e.target.value)}
                                />
                            </div>

                            {/* Endzeit */}
                            <div className="form-group">
                                <label>Endzeit</label>
                                <input
                                    type="time"
                                    value={rule.endTime || "00:00"}
                                    onChange={(e) => handleField(rule.id, "endTime", e.target.value)}
                                    onBlur={(e) => handleBlurTime(rule.id, "endTime", e.target.value)}
                                />
                            </div>

                            {/* Aktiv */}
                            <div className="checkbox-group">
                                <label>Aktiv</label>
                                <input
                                    type="checkbox"
                                    checked={!!rule.active}
                                    onChange={(e) => handleField(rule.id, "active", e.target.checked)}
                                />
                            </div>

                            {/* Speichern */}
                            <div className="form-group-actions">
                                <button
                                    onClick={() => saveRule(rule)}
                                    disabled={savingId === rule.id}
                                    aria-busy={savingId === rule.id}
                                >
                                    {savingId === rule.id ? "Speichern…" : "Speichern"}
                                </button>
                            </div>
                        </div>
                    </div>
                ))}
            </div>

            <button className="add-new-button" onClick={addRule} style={{ marginTop: "1.25rem" }}>
                + Neue Schicht hinzufügen
            </button>
        </div>
    );
}

/* -------- Optional: kleines Skeleton für Ladezustand -------- */
const style = document.createElement("style");
style.innerHTML = `
  .schedule-rules-page.scoped-dashboard .skeleton {
    height: 44px;
    border-radius: 10px;
    background: linear-gradient(90deg, rgba(255,255,255,.06), rgba(255,255,255,.13), rgba(255,255,255,.06));
    animation: skel 1.2s infinite;
    border: 1px solid var(--c-border);
  }
  @keyframes skel { 0%{background-position:-200px 0} 100%{background-position:200px 0} }
`;
document.head.appendChild(style);
