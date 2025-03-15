import React, { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import api from '../utils/api';
import { jsPDF } from 'jspdf';
import html2canvas from 'html2canvas';
import '../styles/PrintReport.css';

function useQuery() {
    return new URLSearchParams(useLocation().search);
}

const PrintReport = () => {
    const query = useQuery();
    const username = query.get('username') || 'Unknown';
    const startDate = query.get('startDate') || '';
    const endDate = query.get('endDate') || '';
    const [reportData, setReportData] = useState([]);

    useEffect(() => {
        if (username && startDate && endDate) {
            api
                .get(`/api/timetracking/report?username=${encodeURIComponent(username)}&startDate=${startDate}&endDate=${endDate}`)
                .then((res) => {
                    setReportData(res.data);
                })
                .catch((err) => {
                    console.error("Error loading report data:", err);
                });
        }
    }, [username, startDate, endDate]);

    const handlePrint = () => {
        window.print();
    };

    const handleGeneratePDF = async () => {
        const input = document.getElementById('reportContent');
        if (!input) {
            alert('Report-Inhalt nicht gefunden!');
            return;
        }
        try {
            const canvas = await html2canvas(input, { scale: 2 });
            const imgData = canvas.toDataURL('image/png');
            const pdf = new jsPDF('p', 'mm', 'a4');
            const pdfWidth = pdf.internal.pageSize.getWidth();
            const pdfHeight = (canvas.height * pdfWidth) / canvas.width;
            pdf.addImage(imgData, 'PNG', 0, 0, pdfWidth, pdfHeight);
            pdf.save(`${username}_report_${startDate}_bis_${endDate}.pdf`);
        } catch (err) {
            console.error("Error generating PDF:", err);
            alert("Fehler beim Erzeugen des PDFs: " + err.message);
        }
    };

    return (
        <div className="print-report" id="reportContent">
            <header>
                <h1>{username}&apos;s Time Report</h1>
                <p>From: {startDate} To: {endDate}</p>
            </header>
            <main>
                {reportData.length === 0 ? (
                    <p>No entries found for this period.</p>
                ) : (
                    reportData.map((entry, index) => (
                        <div key={index} className="report-day">
                            <h2>{entry.date}</h2>
                            <p>Work Start: {entry.workStart}</p>
                            <p>Break Start: {entry.breakStart}</p>
                            <p>Break End: {entry.breakEnd}</p>
                            <p>Work End: {entry.workEnd}</p>
                        </div>
                    ))
                )}
            </main>
            <footer className="print-buttons">
                <button onClick={handlePrint}>Print</button>
                <button onClick={handleGeneratePDF}>Generate PDF</button>
            </footer>
        </div>
    );
};

export default PrintReport;
