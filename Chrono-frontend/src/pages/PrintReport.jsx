// src/pages/PrintReport.jsx

import { useEffect, useState, useRef } from "react";
import { useLocation } from "react-router-dom";
import api from "../utils/api";
import {
    formatDate,
    formatTime,
    minutesToHHMM,
} from "./HourlyDashboard/hourDashUtils";

import jsPDF from "jspdf";
// Stelle sicher, dass jspdf-autotable korrekt importiert wird und sich selbst an jsPDF bindet.
// Manchmal ist kein expliziter 'from' Name nötig, oder er wird so verwendet:
import 'jspdf-autotable'; // Oft reicht dieser Import, damit es sich global an jsPDF hängt
// ODER, falls das nicht geht und du eine spezifische Funktion brauchst:
// import autoTable from 'jspdf-autotable'; // Behalte diesen Import bei

import "../styles/PrintReportScoped.css";
import { useTranslation } from "../context/LanguageContext";

function useQuery() {
    return new URLSearchParams(useLocation().search);
}

export default function PrintReport() {
    const query = useQuery();
    const username = query.get("username") || "";
    const startDate = query.get("startDate") || "";
    const endDate = query.get("endDate") || "";

    const { t } = useTranslation();

    const [rows, setRows] = useState([]);
    const tableRef = useRef(null);

    useEffect(() => {
        if (!username || !startDate || !endDate) return;
        api
            .get("/api/timetracking/history", { params: { username } })
            .then((res) => {
                const filtered = (res.data || [])
                    .filter((r) => r.date >= startDate && r.date <= endDate)
                    .sort((a, b) => a.date.localeCompare(b.date));

                const mapped = filtered.map((r) => {
                    const pt = r.primaryTimes || {};
                    const workStart = pt.firstStartTime
                        ? pt.firstStartTime.substring(0, 5)
                        : "-";
                    const workEnd = pt.lastEndTime
                        ? pt.lastEndTime.substring(0, 5)
                        : pt.isOpen
                          ? t("printReport.open")
                          : "-";

                    const punches = (r.entries || [])
                        .map((e) =>
                            `${e.punchType.substring(0, 1)}:${formatTime(
                                e.entryTimestamp
                            )}${
                                e.source === "SYSTEM_AUTO_END" && !e.correctedByUser
                                    ? "(A)"
                                    : ""
                            }`
                        )
                        .join(" | ");

                    return {
                        date: r.date,
                        workStart,
                        workEnd,
                        pause: minutesToHHMM(r.breakMinutes),
                        total: minutesToHHMM(r.workedMinutes),
                        punches,
                        note: r.dailyNote || "",
                    };
                });

                setRows(mapped);
            })
            .catch((err) => console.error("Report-Fehler:", err));
    }, [username, startDate, endDate, t]);

    // PDF-Export
    function handlePdf() {
        const doc = new jsPDF("p", "mm", "a4");

        doc.setFontSize(14);
        doc.text(`${t("printReport.title", "Zeitenbericht")} – ${username}`, 14, 15);

        doc.setFontSize(11);
        doc.text(
            `${t("printReport.periodLabel", "Zeitraum")}: ${startDate} – ${endDate}`,
            14,
            22
        );

        const body = rows.map((r) => [
            formatDate(r.date),
            r.workStart,
            r.workEnd,
            r.pause,
            r.total,
            r.punches,
            r.note,
        ]);

        // **ÄNDERUNG HIER:**
        // Stelle sicher, dass autoTable direkt auf der jsPDF-Instanz aufgerufen wird,
        // was die übliche Methode ist, nachdem sich das Plugin registriert hat.
        // Wenn der `import 'jspdf-autotable';` korrekt funktioniert,
        // sollte `doc.autoTable` verfügbar sein.
        if (typeof doc.autoTable === 'function') {
            doc.autoTable({
                head: [[
                    t("printReport.date"),
                    t("printReport.workStart"),
                    t("printReport.workEnd"),
                    t("printReport.pause"),
                    t("printReport.total"),
                    t("printReport.punches"),
                    t("printReport.note"),
                ]],
                body,
                startY: 28,
                margin: { left: 14, right: 14 },
                styles: { fontSize: 9, cellPadding: 2, halign: "center" },
                headStyles: { fillColor: [71, 91, 255], textColor: 255, halign: "center", fontStyle: 'bold' },
                alternateRowStyles: { fillColor: [245, 245, 245] },
                didDrawPage: (data) => {
                    const pageCount = doc.internal.getNumberOfPages ? doc.internal.getNumberOfPages() : null;
                    const pageStr = pageCount ? `${data.pageNumber} / ${pageCount}` : `${data.pageNumber}`;
                    const pageWidth = doc.internal.pageSize.getWidth ? doc.internal.pageSize.getWidth() : doc.internal.pageSize.width;
                    doc.text(pageStr, pageWidth - 14, 10, { align: "right" });
                }
            });
        } else {
            console.error("jspdf-autotable ist nicht korrekt als Funktion auf der jsPDF Instanz registriert.");
            // Fallback oder Fehlermeldung, falls autoTable nicht existiert
            alert("Fehler: PDF-Tabellenerstellung ist nicht verfügbar.");
            return;
        }

        doc.save(`timesheet_${username}_${startDate}_${endDate}.pdf`);
    }

    return (
        // ... dein JSX bleibt gleich ...
        <div className="print-report-page scoped-print">
            <h1 className="title">{t("printReport.title", "Zeitenbericht")}</h1>
            <p className="sub">
                <strong>{t("printReport.userLabel", "User")}:</strong> {username} &nbsp;|&nbsp;
                <strong>{t("printReport.periodLabel", "Zeitraum")}:</strong>{" "}
                {startDate} – {endDate}
            </p>

            <table ref={tableRef}>
                <thead>
                <tr>
                    <th>{t("printReport.date")}</th>
                    <th>{t("printReport.workStart")}</th>
                    <th>{t("printReport.workEnd")}</th>
                    <th>{t("printReport.pause")}</th>
                    <th>{t("printReport.total")}</th>
                    <th>{t("printReport.punches")}</th>
                    <th>{t("printReport.note")}</th>
                </tr>
                </thead>
                <tbody>
                {rows.length === 0 ? (
                    <tr>
                        <td colSpan={7} className="noRows">
                            {t("printReport.noEntries", "Keine Einträge")}
                        </td>
                    </tr>
                ) : (
                    rows.map((r, i) => (
                        <tr key={i}>
                            <td>{formatDate(r.date)}</td>
                            <td>{r.workStart}</td>
                            <td>{r.workEnd}</td>
                            <td>{r.pause}</td>
                            <td>{r.total}</td>
                            <td>{r.punches}</td>
                            <td>{r.note}</td>
                        </tr>
                    ))
                )}
                </tbody>
            </table>

            <div className="btnRow">
                <button onClick={() => window.print()}>
                    {t("printReport.printButton", "Drucken")}
                </button>
                <button onClick={handlePdf}>
                    {t("printReport.pdfButton", "PDF speichern")}
                </button>
            </div>
        </div>
    );
}