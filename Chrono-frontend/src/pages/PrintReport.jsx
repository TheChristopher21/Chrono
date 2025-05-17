// src/pages/PrintReport.jsx

import  { useEffect, useState, useRef } from "react";
import { useLocation } from "react-router-dom";
import api from "../utils/api";

import jsPDF from "jspdf";
import autoTable from "jspdf-autotable";

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
        // 1) Neues jsPDF-Objekt
        const doc = new jsPDF("p", "mm", "a4");

        doc.setFontSize(14);
        doc.text(`${t("printReport.title", "Zeitenbericht")} – ${username}`, 14, 15);

        doc.setFontSize(11);
        doc.text(
            `${t("printReport.periodLabel", "Zeitraum")}: ${startDate} – ${endDate}`,
            14,
            22
        );

        // 2) Body-Daten
        const body = rows.map((r) => [
            r.date,
            r.workStart || "-",
            r.breakStart || "-",
            r.breakEnd || "-",
            r.workEnd || "-",
        ]);

        // 3) autoTable-Aufruf über die *Funktion* autoTable(doc, { ... })
        autoTable(doc, {
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
            headStyles: { fillColor: [0, 123, 255], halign: "center" },
            // 4) Falls du Seitenzahlen etc. einfügen willst:
            didDrawPage: (data) => {
                // doc.getNumberOfPages() o. doc.internal.getNumberOfPages(), je nach Version
                // doc.internal.pageSize.getWidth() oder doc.internal.pageSize.width
                const pageCount = doc.getNumberOfPages();
                const pageStr = `${data.pageNumber} / ${pageCount}`;
                // Manche Versionen brauchen doc.internal.pageSize.width anstelle von getWidth()
                const pageWidth = doc.internal.pageSize.getWidth
                    ? doc.internal.pageSize.getWidth()
                    : doc.internal.pageSize.width; // Fallback

                // Text an rechter Kante
                doc.text(pageStr, pageWidth - 14, 10, { align: "right" });
            },
        });

        // 5) PDF speichern
        doc.save(`timesheet_${username}_${startDate}_${endDate}.pdf`);
    }

    return (
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
                {/* 6) "Drucken" => window.print() */}
                <button onClick={() => window.print()}>
                    {t("printReport.printButton", "Drucken")}
                </button>
                {/* 7) PDF -> handlePdf */}
                <button onClick={handlePdf}>
                    {t("printReport.pdfButton", "PDF speichern")}
                </button>
            </div>
        </div>
    );
}
