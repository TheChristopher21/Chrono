import { useEffect, useState, useRef } from "react";
import { useLocation } from "react-router-dom";
import api from "../utils/api";
import jsPDF from "jspdf";
import "jspdf-autotable";                 // ⬅ Plugin direkt laden
import "../styles/PrintReport.css";

function useQuery() {
    return new URLSearchParams(useLocation().search);
}

export default function PrintReport() {
    const query       = useQuery();
    const username    = query.get("username")  || "";
    const startDate   = query.get("startDate") || "";
    const endDate     = query.get("endDate")   || "";

    const [rows, setRows] = useState([]);
    const tableRef = useRef(null);            // für window.print()

    /*  Daten holen  */
    useEffect(() => {
        if (!username || !startDate || !endDate) return;
        api
            .get("/api/timetracking/report", {
                params: { username, startDate, endDate }
            })
            .then(res => {
                /*  Backend liefert ein Array mit { date, workStart … }  */
                setRows(res.data ?? []);
            })
            .catch(err => console.error("Report-Fehler:", err));
    }, [username, startDate, endDate]);

    /*  PDF-Export  */
    function handlePdf() {
        const doc = new jsPDF("p", "mm", "a4");
        doc.setFontSize(14);
        doc.text(`Zeitenbericht – ${username}`, 14, 15);
        doc.setFontSize(11);
        doc.text(`Zeitraum: ${startDate}  –  ${endDate}`, 14, 22);

        const body = rows.map(r => [
            r.date,
            r.workStart  || "-",
            r.breakStart || "-",
            r.breakEnd   || "-",
            r.workEnd    || "-"
        ]);

        doc.autoTable({
            head: [["Datum", "Work-Start", "Break-Start", "Break-End", "Work-End"]],
            body,
            startY: 28,
            margin: { left: 14, right: 14 },
            styles: { fontSize: 9, cellPadding: 2 },
            headStyles: { fillColor: [0, 123, 255], halign: "center" },
            didDrawPage: (d) => {
                const page = `${d.pageNumber}/${doc.getNumberOfPages()}`;
                doc.text(page, doc.internal.pageSize.getWidth() - 14, 10, { align: "right" });
            }
        });

        doc.save(`timesheet_${username}_${startDate}_${endDate}.pdf`);
    }

    return (
        <div className="print-wrapper">
            <h1 className="title">Zeitenbericht</h1>
            <p className="sub">
                <strong>User:</strong> {username} &nbsp;|&nbsp;
                <strong>Zeitraum:</strong> {startDate} – {endDate}
            </p>

            <table ref={tableRef}>
                <thead>
                <tr>
                    <th>Datum</th>
                    <th>Work-Start</th>
                    <th>Break-Start</th>
                    <th>Break-End</th>
                    <th>Work-End</th>
                </tr>
                </thead>
                <tbody>
                {rows.length === 0 ? (
                    <tr><td colSpan={5} className="noRows">keine Einträge</td></tr>
                ) : (
                    rows.map((r,i) => (
                        <tr key={i}>
                            <td>{r.date}</td>
                            <td>{r.workStart  || "-"}</td>
                            <td>{r.breakStart || "-"}</td>
                            <td>{r.breakEnd   || "-"}</td>
                            <td>{r.workEnd    || "-"}</td>
                        </tr>
                    ))
                )}
                </tbody>
            </table>

            <div className="btnRow">
                <button onClick={() => window.print()}>Drucken</button>
                <button onClick={handlePdf}>PDF speichern</button>
            </div>
        </div>
    );
}
