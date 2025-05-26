// src/pages/PrintReport.jsx

import { useEffect, useState, useRef } from "react";
import { useLocation } from "react-router-dom";
import api from "../utils/api";

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
            .get("/api/timetracking/report", {
                params: { username, startDate, endDate },
            })
            .then((res) => {
                setRows(res.data ?? []);
            })
            .catch((err) => console.error("Report-Fehler:", err));
    }, [username, startDate, endDate]);

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
            r.date,
            r.workStart || "-",
            r.breakStart || "-",
            r.breakEnd || "-",
            r.workEnd || "-",
        ]);

        // **ÄNDERUNG HIER:**
        // Stelle sicher, dass autoTable direkt auf der jsPDF-Instanz aufgerufen wird,
        // was die übliche Methode ist, nachdem sich das Plugin registriert hat.
        // Wenn der `import 'jspdf-autotable';` korrekt funktioniert,
        // sollte `doc.autoTable` verfügbar sein.
        if (typeof doc.autoTable === 'function') {
            doc.autoTable({
                head: [
                    [
                        t("printReport.date", "Datum"),
                        t("printReport.workStart", "Work-Start"),
                        t("printReport.breakStart", "Break-Start"),
                        t("printReport.breakEnd", "Break-End"),
                        t("printReport.workEnd", "Work-End"),
                    ],
                ],
                body,
                startY: 28,
                margin: { left: 14, right: 14 },
                styles: { fontSize: 9, cellPadding: 2 },
                headStyles: { fillColor: [0, 123, 255], halign: "center", textColor: 255 }, // textColor für Header hinzugefügt
                didDrawPage: (data) => {
                    const pageCount = doc.internal.getNumberOfPages ? doc.internal.getNumberOfPages() : null; // Sicherer Zugriff
                    const pageStr = pageCount ? `${data.pageNumber} / ${pageCount}` : `${data.pageNumber}`;

                    const pageWidth = doc.internal.pageSize.getWidth
                        ? doc.internal.pageSize.getWidth()
                        : doc.internal.pageSize.width;

                    doc.text(pageStr, pageWidth - 14, 10, { align: "right" });
                },
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
                    <th>{t("printReport.date", "Datum")}</th>
                    <th>{t("printReport.workStart", "Work-Start")}</th>
                    <th>{t("printReport.breakStart", "Break-Start")}</th>
                    <th>{t("printReport.breakEnd", "Break-End")}</th>
                    <th>{t("printReport.workEnd", "Work-End")}</th>
                </tr>
                </thead>
                <tbody>
                {rows.length === 0 ? (
                    <tr>
                        <td colSpan={5} className="noRows">
                            {t("printReport.noEntries", "Keine Einträge")}
                        </td>
                    </tr>
                ) : (
                    rows.map((r, i) => (
                        <tr key={i}>
                            <td>{r.date}</td>
                            <td>{r.workStart || "-"}</td>
                            <td>{r.breakStart || "-"}</td>
                            <td>{r.breakEnd || "-"}</td>
                            <td>{r.workEnd || "-"}</td>
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