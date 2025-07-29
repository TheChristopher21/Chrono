import React, { useState, useCallback, useMemo } from 'react';
import { useDropzone } from 'react-dropzone';
import api from './utils/api';
import { useNotification } from './context/NotificationContext';
import Navbar from './components/Navbar';
import './styles/TimeTrackingImportScooped.css';

function TimeTrackingImport() {
    // State-Hooks bleiben unverändert...
    const [file, setFile] = useState(null);
    const [isUploading, setIsUploading] = useState(false);
    const [progress, setProgress] = useState(0);
    const [uploadResponse, setUploadResponse] = useState(null);
    const [error, setError] = useState('');
    const [invalidRows, setInvalidRows] = useState([]);
    const [isReimporting, setIsReimporting] = useState(false);
    const { notify } = useNotification();

    const resetState = useCallback(() => {
        setFile(null);
        setUploadResponse(null);
        setError('');
        setProgress(0);
        setInvalidRows([]);
        setIsUploading(false);
        setIsReimporting(false);
    }, []);

    const onDrop = useCallback(acceptedFiles => {
        resetState();
        const selectedFile = acceptedFiles[0];
        if (selectedFile) {
            setFile(selectedFile);
        } else {
            notify("Dateiauswahl abgebrochen.", "info");
        }
    }, [notify, resetState]);

    const { getRootProps, getInputProps, isDragActive } = useDropzone({
        onDrop,
        accept: { 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': ['.xlsx'] },
        multiple: false
    });

    const handleInitialImport = async () => {
        if (!file) { return; }
        setIsUploading(true);
        setError('');
        const formData = new FormData();
        formData.append('file', file);

        try {
            const response = await api.post('/api/admin/timetracking/import', formData, {
                onUploadProgress: e => {
                    if (e.total) setProgress(Math.round((e.loaded * 100) / e.total));
                }
            });
            setUploadResponse(response.data);
            setInvalidRows(response.data.invalidRows || []);
            notify(response.data.message || "Import teilweise erfolgreich.", "success");
        } catch (err) {
            const errorData = err.response?.data;
            const errorMessage = errorData?.error || errorData?.message || 'Ein Fehler ist aufgetreten.';
            setError(errorMessage);
            notify(errorMessage, "error");
            if (errorData) {
                setUploadResponse(errorData);
                setInvalidRows(errorData.invalidRows || []);
            }
        } finally {
            setIsUploading(false);
        }
    };

    const handleReimport = async () => {
        if (invalidRows.length === 0) return;
        setIsReimporting(true);
        setError('');
        try {
            const res = await api.post('/api/admin/timetracking/import/json', invalidRows);
            notify(res.data.message || `${res.data.importedCount} Zeilen erfolgreich re-importiert!`, "success");
            setUploadResponse(res.data);
            setInvalidRows(res.data.invalidRows || []);
        } catch (err) {
            const errorMessage = err.response?.data?.error || 'Fehler beim Re-Import.';
            setError(errorMessage);
            notify(errorMessage, "error");
        } finally {
            setIsReimporting(false);
        }
    };

    const handleRowChange = (index, field, value) => {
        const updatedRows = [...invalidRows];
        updatedRows[index] = { ...updatedRows[index], [field]: value };
        setInvalidRows(updatedRows);
    };

    const tableHeaders = useMemo(() => {
        if (invalidRows.length === 0) return [];
        const headers = ['rowNumber', 'username', 'timestamp', 'punchType', 'source', 'note', 'error'];
        return headers.filter(header => header in invalidRows[0]);
    }, [invalidRows]);

    return (
        <>
            <Navbar />
            <div className="time-import-page scoped-import">
                {/* Dieser neue Wrapper zentriert den Inhalt */}
                <div className="page-content-wrapper">
                    <div className="header">
                        <h1>Stempelzeiten aus Excel importieren</h1>
                        <p>Laden Sie eine formatierte .xlsx-Datei hoch, um Arbeitszeiten für mehrere Benutzer gleichzeitig zu verarbeiten.</p>
                    </div>

                    <div className="card">
                        <h2>Schritt 1: Datei auswählen</h2>
                        <div {...getRootProps()} className={`upload-area ${isDragActive ? 'active' : ''}`}>
                            <input {...getInputProps()} />
                            {isDragActive ? <p>Lassen Sie die Datei jetzt los...</p> : file ? <p>Ausgewählt: <strong>{file.name}</strong><br/><span className="upload-hint">Klicken oder neue Datei hierher ziehen, um zu ändern.</span></p> : <p>Excel-Datei hierher ziehen oder klicken.</p>}
                        </div>
                        {file && !isUploading && (
                            <button onClick={handleInitialImport} disabled={isUploading} className="action-button">
                                {`Datei prüfen & importieren`}
                            </button>
                        )}
                        {isUploading && (
                            <div className="progress-bar">
                                <div className="progress-bar-inner" style={{ width: `${progress}%` }}></div>
                            </div>
                        )}
                    </div>

                    {uploadResponse && (
                        <div className="card">
                            <h2>Schritt 2: Ergebnis des Imports</h2>
                            <div className="feedback-summary">
                                {typeof uploadResponse.importedCount === 'number' && (
                                    <div className="summary-item success">
                                        <span className="count">{uploadResponse.importedCount}</span>
                                        <span className="label">Einträge erfolgreich importiert</span>
                                    </div>
                                )}
                                {(uploadResponse.errors?.length > 0 || invalidRows.length > 0) && (
                                    <div className="summary-item error">
                                        <span className="count">{invalidRows.length || uploadResponse.errors.length}</span>
                                        <span className="label">Fehler gefunden</span>
                                    </div>
                                )}
                            </div>
                            {error && <p className="error-message">{error}</p>}

                            {invalidRows.length > 0 && (
                                <div className="correction-section">
                                    <h4>Fehlerhafte Zeilen korrigieren</h4>
                                    <p>Bearbeiten Sie die Daten direkt in der Tabelle und importieren Sie die korrigierten Zeilen erneut.</p>
                                    <div className="table-container">
                                        <table className="correction-table">
                                            <thead>
                                            <tr>{tableHeaders.map(header => <th key={header}>{header}</th>)}</tr>
                                            </thead>
                                            <tbody>
                                            {invalidRows.map((row, idx) => (
                                                <tr key={idx}>
                                                    {tableHeaders.map(header => (
                                                        <td key={header} data-label={header}>
                                                            {header !== 'error' && header !== 'rowNumber' ? <input value={row[header] || ''} onChange={e => handleRowChange(idx, header, e.target.value)} /> : <span>{row[header]}</span>}
                                                        </td>
                                                    ))}
                                                </tr>
                                            ))}
                                            </tbody>
                                        </table>
                                    </div>
                                    <button onClick={handleReimport} disabled={isReimporting} className="action-button reimport">
                                        {isReimporting ? 'Re-Import läuft...' : `Korrigierte ${invalidRows.length} Zeilen importieren`}
                                    </button>
                                </div>
                            )}
                        </div>
                    )}

                    <div className="card">
                        <h2>Hinweis zum Excel-Format</h2>
                        <p>Bitte stellen Sie sicher, dass Ihre .xlsx-Datei die folgenden Spalten enthält. Die erste Zeile wird als Kopfzeile ignoriert.</p>
                        <ul className="instructions-list">
                            <li><strong>Spalte A:</strong> Username</li>
                            <li><strong>Spalte B:</strong> Datum (Format: JJJJ-MM-TT)</li>
                            <li><strong>Spalte C:</strong> Arbeitsbeginn (Format: HH:mm)</li>
                            <li><strong>Spalte D:</strong> Pausenbeginn (Format: HH:mm, optional)</li>
                            <li><strong>Spalte E:</strong> Pausenende (Format: HH:mm, optional)</li>
                            <li><strong>Spalte F:</strong> Arbeitsende (Format: HH:mm)</li>
                            <li><strong>Spalte G:</strong> Tagesnotiz (optional)</li>
                        </ul>
                    </div>
                </div>
            </div>
        </>
    );
}

export default TimeTrackingImport;