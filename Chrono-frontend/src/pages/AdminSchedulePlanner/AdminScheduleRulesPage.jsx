import React, { useEffect, useMemo, useState } from "react";
import Navbar from "../../components/Navbar";
import "../../styles/AdminScheduleRulesPageScoped.css";

export default function AdminScheduleRulesPage() {
    const API_BASE =
        (import.meta && import.meta.env && import.meta.env.VITE_API_URL)
            ? import.meta.env.VITE_API_URL
            : "";
    const [rules, setRules] = useState([]);
    const [loading, setLoading] = useState(true);
    const [savingId, setSavingId] = useState(null);
    const endpoint = useMemo(
        () => `${API_BASE}/admin/schedule/rules`.replace(/(?<!:)\/{2,}/g, "/"),
        [API_BASE]
    );

    const pad2 = (n) => String(n).padStart(2, "0");
    const normalizeTime = (t) => {
        if (!t) return "00:00";
        const m = /^(\d{1,2}):(\d{1,2})$/.exec(t);
        if (!m) return t;
        const hh = Math.max(0, Math.min(23, parseInt(m[1], 10)));
        const mm = Math.max(0, Math.min(59, parseInt(m[2], 10)));
        return `${pad2(hh)}:${pad2(mm)}`;
    };

    useEffect(() => {
        let ignore = false;
        (async () => {
            try {
                setLoading(true);
                const res = await fetch(endpoint, { credentials: "include" });
                if (!res.ok) throw new Error(`GET ${endpoint} → ${res.status}`);
                const data = await res.json();
                const normalized = Array.isArray(data)
                    ? data.map((r) => ({
                        id: r.id ?? crypto.randomUUID(),
                        serverId: r.id ?? null,
                        label: r.label ?? "",
                        key: r.key ?? "",
                        startTime: normalizeTime(r.startTime ?? "00:00"),
                        endTime: normalizeTime(r.endTime ?? "00:00"),
                        active: !!r.active,
                    }))
                    : [];
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

    const handleField = (id, field, value) => {
        setRules((prev) => prev.map((r) => (r.id === id ? { ...r, [field]: value } : r)));
    };
    const handleBlurTime = (id, field, value) => {
        handleField(id, field, normalizeTime(value));
    };
    const addRule = () => {
        setRules((prev) => [
            ...prev,
            { id: crypto.randomUUID(), label: "", key: "", startTime: "08:00", endTime: "17:00", active: true },
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
            const saved = await res.json().catch(() => ({}));
            setRules((prev) =>
                prev.map((r) =>
                    r.id === rule.id ? { ...r, ...payload, serverId: saved.id ?? rule.serverId ?? rule.id } : r
                )
            );
        } catch (e) {
            console.error(e);
            alert("Speichern fehlgeschlagen.");
        } finally {
            setSavingId(null);
        }
    };

    return (
        <>
            <Navbar />
            <div className="schedule-rules-page scoped-dashboard">
                <div className="dashboard-header">
                    <h1>Schicht-Einstellungen</h1>
                    <p>Hier können die globalen Schichten und ihre Zeiten für den Dienstplan konfiguriert werden.</p>
                </div>

                {/* isolierter, eindeutiger Blockname */}
                <div className="shift-rules">
                    {loading ? (
                        <div className="shift-card" aria-busy="true">
                            <div className="shift-grid">
                                <div className="srp-group fg-label"><label>Bezeichnung</label><div className="skeleton" /></div>
                                <div className="srp-group fg-key"><label>Schlüssel (z.B. EARLY)</label><div className="skeleton" /></div>
                                <div className="srp-group fg-start"><label>Startzeit</label><div className="skeleton" /></div>
                                <div className="srp-group fg-end"><label>Endzeit</label><div className="skeleton" /></div>
                                <div className="srp-toggle fg-active"><label>Aktiv</label></div>
                                <div className="srp-actions fg-save"><button disabled>Speichern</button></div>
                            </div>
                        </div>
                    ) : rules.map((rule) => (
                        <div className="shift-card" key={rule.id}>
                            <div className="shift-grid">
                                <div className="srp-group fg-label">
                                    <label>Bezeichnung</label>
                                    <input
                                        type="text"
                                        value={rule.label}
                                        onChange={(e) => handleField(rule.id, "label", e.target.value)}
                                        placeholder="z. B. Frühschicht"
                                    />
                                </div>

                                <div className="srp-group fg-key">
                                    <label>Schlüssel (z.B. EARLY)</label>
                                    <input
                                        type="text"
                                        value={rule.key}
                                        onChange={(e) => handleField(rule.id, "key", e.target.value.toUpperCase())}
                                        placeholder="EARLY"
                                    />
                                </div>

                                <div className="srp-group fg-start">
                                    <label>Startzeit</label>
                                    <input
                                        type="time"
                                        value={rule.startTime || "00:00"}
                                        onChange={(e) => handleField(rule.id, "startTime", e.target.value)}
                                        onBlur={(e) => handleBlurTime(rule.id, "startTime", e.target.value)}
                                    />
                                </div>

                                <div className="srp-group fg-end">
                                    <label>Endzeit</label>
                                    <input
                                        type="time"
                                        value={rule.endTime || "00:00"}
                                        onChange={(e) => handleField(rule.id, "endTime", e.target.value)}
                                        onBlur={(e) => handleBlurTime(rule.id, "endTime", e.target.value)}
                                    />
                                </div>

                                <div className="srp-toggle fg-active">
                                    <label>Aktiv</label>
                                    <input
                                        type="checkbox"
                                        checked={!!rule.active}
                                        onChange={(e) => handleField(rule.id, "active", e.target.checked)}
                                    />
                                </div>

                                <div className="srp-actions fg-save">
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
        </>
    );
}
