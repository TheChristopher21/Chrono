// src/pages/PrintReport.jsx

import { useEffect, useState } from "react";
import { useLocation } from "react-router-dom";
import api from "../utils/api";
import { formatDate, formatDateWithWeekday, minutesToHHMM, formatTime } from "./HourlyDashboard/hourDashUtils";
import jsPDF from "jspdf";
import { useTranslation } from "../context/LanguageContext";
import "../styles/PrintReportScoped.css";

function useQuery() {
    return new URLSearchParams(useLocation().search);
}

// Hilfsfunktion zur Berechnung von Arbeits- und Pausenblöcken
const processEntriesForReport = (entries) => {
    const blocks = { work: [], break: [] };
    let lastStartTime = null;

    (entries || []).sort((a, b) => new Date(a.entryTimestamp) - new Date(b.entryTimestamp)).forEach(entry => {
        if (entry.punchType === 'START') {
            lastStartTime = new Date(entry.entryTimestamp);
        } else if (entry.punchType === 'ENDE' && lastStartTime) {
            const endTime = new Date(entry.entryTimestamp);
            const duration = (endTime - lastStartTime) / (1000 * 60);
            const block = {
                start: formatTime(lastStartTime),
                end: formatTime(endTime),
                duration: minutesToHHMM(duration),
                description: entry.customerName || entry.projectName || ''
            };

            // Unterscheidung zwischen Arbeit und Pause
            if (blocks.work.length > 0 && blocks.work[blocks.work.length - 1].end === formatTime(lastStartTime)) {
                blocks.break.push(block);
            } else {
                blocks.work.push(block);
            }
            lastStartTime = null;
        }
    });
    return blocks;
};


export default function PrintReport() {
    const query = useQuery();
    const username = query.get("username") || "";
    const startDate = query.get("startDate") || "";
    const endDate = query.get("endDate") || "";

    const { t } = useTranslation();

    const [reportData, setReportData] = useState([]);
    const [totals, setTotals] = useState({ work: 0, pause: 0 });
    const [userProfile, setUserProfile] = useState(null);

useEffect(() => {
    if (!username || !startDate || !endDate) return;
    Promise.all([
        api.get("/api/timetracking/history", { params: { username } }),
        api.get(`/api/users/profile/${username}`)
    ])
        .then(([res, profileRes]) => {
                const filtered = (res.data || [])
                    .filter((r) => r.date >= startDate && r.date <= endDate)
                    .sort((a, b) => a.date.localeCompare(b.date));

                let totalWorkMinutes = 0;
                let totalBreakMinutes = 0;

                const mapped = filtered.map((day) => {
                    totalWorkMinutes += day.workedMinutes || 0;
                    totalBreakMinutes += day.breakMinutes || 0;
                    return {
                        date: day.date,
                        workedMinutes: day.workedMinutes || 0,
                        breakMinutes: day.breakMinutes || 0,
                        blocks: processEntriesForReport(day.entries),
                        note: day.dailyNote || ""
                    };
                });

                setReportData(mapped);
                setTotals({ work: totalWorkMinutes, pause: totalBreakMinutes });
                setUserProfile(profileRes.data);
            })
            .catch((err) => console.error("Report-Fehler:", err));
    }, [username, startDate, endDate]);

    const handlePdf = () => {
        const doc = new jsPDF("p", "mm", "a4");
        const pageMargin = 15;
        const pageWidth = doc.internal.pageSize.getWidth();
        const contentWidth = pageWidth - (2 * pageMargin);
        let yPos = 20;

        // Header
        doc.setFontSize(22);
        doc.setFont("helvetica", "bold");
        doc.text("Zeitenbericht", pageWidth / 2, yPos, { align: "center" });
        yPos += 10;

        doc.setFontSize(12);
        doc.setFont("helvetica", "normal");
        doc.text(`für ${username}`, pageWidth / 2, yPos, { align: "center" });
        yPos += 6;
        doc.text(`Zeitraum: ${formatDate(startDate)} - ${formatDate(endDate)}`, pageWidth / 2, yPos, { align: "center" });
        yPos += 6;
        const overtimeStr = minutesToHHMM(userProfile?.trackingBalanceInMinutes || 0);
        doc.text(`${t('overtimeBalance')}: ${overtimeStr}`, pageWidth / 2, yPos, { align: "center" });
        yPos += 15;

        // Summary Box
        doc.setFillColor(248, 249, 250); // Light grey
        doc.rect(pageMargin, yPos, contentWidth, 25, 'F');
        const summaryTextY = yPos + 15;
        const summaryCol1 = pageMargin + contentWidth / 4;
        const summaryCol2 = pageMargin + (contentWidth / 4) * 3;

        doc.setFontSize(10);
        doc.setTextColor(108, 117, 125);
        doc.text(t("printReport.summaryWork"), summaryCol1, summaryTextY - 8, { align: 'center' });
        doc.text(t("printReport.summaryBreak"), summaryCol2, summaryTextY - 8, { align: 'center' });

        doc.setFontSize(16);
        doc.setFont("helvetica", "bold");
        doc.setTextColor(41, 128, 185);
        doc.text(minutesToHHMM(totals.work), summaryCol1, summaryTextY, { align: 'center' });
        doc.setTextColor(44, 62, 80);
        doc.text(minutesToHHMM(totals.pause), summaryCol2, summaryTextY, { align: 'center' });

        yPos += 35;

        // Day Cards
        reportData.forEach(day => {
            if (yPos > 260) { // Check for new page
                doc.addPage();
                yPos = 20;
            }

            const cardStartY = yPos;
            doc.setFillColor(236, 240, 241); // Card Header bg
            doc.roundedRect(pageMargin, yPos, contentWidth, 10, 3, 3, 'F');

            doc.setFontSize(14);
            doc.setFont("helvetica", "bold");
            doc.setTextColor(44, 62, 80);
            doc.text(formatDateWithWeekday(day.date), pageMargin + 5, yPos + 7);
            yPos += 10;

            // Card Body
            const bodyYStart = yPos;
            let leftColY = bodyYStart + 10;
            let rightColY = bodyYStart + 10;
            const rightColX = pageMargin + contentWidth / 2.5;

            // Left column (Summary)
            doc.setFontSize(11);
            doc.setFont("helvetica", "bold");
            doc.text(t("printReport.overview"), pageMargin + 5, leftColY);
            leftColY += 7;

            doc.setFontSize(10);
            doc.setFont("helvetica", "normal");
            doc.text(`${t('printReport.worked')}: ${minutesToHHMM(day.workedMinutes)}`, pageMargin + 5, leftColY);
            leftColY += 6;
            doc.text(`${t('printReport.pause')}: ${minutesToHHMM(day.breakMinutes)}`, pageMargin + 5, leftColY);
            leftColY += 10;

            if (day.note) {
                doc.setFont("helvetica", "bold");
                doc.text(`${t('printReport.note')}:`, pageMargin + 5, leftColY);
                leftColY += 6;
                doc.setFont("helvetica", "italic");
                const noteLines = doc.splitTextToSize(day.note, (contentWidth / 2.5) - 10);
                doc.text(noteLines, pageMargin + 5, leftColY);
                leftColY += noteLines.length * 5;
            }


            // Right column (Blocks)
            doc.setFontSize(11);
            doc.setFont("helvetica", "bold");
            doc.text(t("printReport.blocks"), rightColX, rightColY);
            rightColY += 7;

            doc.setFontSize(10);
            doc.setFont("helvetica", "normal");
            day.blocks.work.forEach(block => {
                const text = `${block.description ? `${block.description}:` : ''} ${block.start} - ${block.end} (${block.duration})`;
                const textLines = doc.splitTextToSize(text, contentWidth - rightColX - 5);
                doc.text(textLines, rightColX, rightColY);
                rightColY += textLines.length * 5 + 2;
            });

            const cardEndY = Math.max(leftColY, rightColY) + 5;
            doc.setDrawColor(236, 240, 241);
            doc.roundedRect(pageMargin, cardStartY, contentWidth, cardEndY - cardStartY, 3, 3, 'S');

            yPos = cardEndY + 10;
        });

        doc.save(`Zeitenbericht_${username}_${startDate}_${endDate}.pdf`);
    };

    return (
        <div className="print-report-page">
            <header className="report-header">
                <h1>{t("printReport.title", "Zeitenbericht")}</h1>
                <p>{t("printReport.userLabel", "User")}: {username}</p>
                <p>{t("printReport.periodLabel", "Zeitraum")}: {formatDate(startDate)} – {formatDate(endDate)}</p>
                <p>{t('overtimeBalance')}: {minutesToHHMM(userProfile?.trackingBalanceInMinutes || 0)}</p>
            </header>

            <section className="report-summary">
                <div>
                    <span className="label">{t('printReport.summaryWork')}</span>
                    <span className="value primary">{minutesToHHMM(totals.work)}</span>
                </div>
                <div>
                    <span className="label">{t('printReport.summaryBreak')}</span>
                    <span className="value">{minutesToHHMM(totals.pause)}</span>
                </div>
            </section>

            <main className="report-body">
                {reportData.length === 0 ? (
                    <div className="no-entries-card">{t("printReport.noEntries", "Keine Einträge für diesen Zeitraum")}</div>
                ) : (
                    reportData.map(day => (
                        <div key={day.date} className="day-card-report">
                            <div className="day-card-report-header">
                                <h2>{formatDateWithWeekday(day.date)}</h2>
                            </div>
                            <div className="day-card-report-body">
                                <div className="overview-col">
                                    <h4>{t('printReport.overview')}</h4>
                                    <p><strong>{t('printReport.worked')}:</strong> {minutesToHHMM(day.workedMinutes)}</p>
                                    <p><strong>{t('printReport.pause')}:</strong> {minutesToHHMM(day.breakMinutes)}</p>
                                    {day.note && <>
                                        <h4>{t('printReport.note')}</h4>
                                        <p className="note-text">{day.note}</p>
                                    </>}
                                </div>
                                <div className="blocks-col">
                                    <h4>{t('printReport.blocks')}</h4>
                                    {day.blocks.work.map((b, i) => (
                                        <div key={`w-${i}`} className="work-block">
                                            <span>{b.description ? `${b.description}:` : `${t('printReport.workLabel')}:`}</span>
                                            <span>{b.start} - {b.end} ({b.duration})</span>
                                        </div>
                                    ))}
                                    {day.blocks.break.length > 0 && <h4>{t('printReport.breaks')}</h4>}
                                    {day.blocks.break.map((b, i) => (
                                        <div key={`b-${i}`} className="break-block">
                                            <span>{t('printReport.pause')}:</span>
                                            <span>{b.start} - {b.end} ({b.duration})</span>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        </div>
                    ))
                )}
            </main>

            <div className="btnRow">
                <button onClick={() => window.print()}>{t("printReport.printButton", "Drucken")}</button>
                <button onClick={handlePdf}>{t("printReport.pdfButton", "PDF speichern")}</button>
            </div>
        </div>
    );
}