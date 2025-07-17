import { useState } from 'react';
import api from './utils/api'; // Geändert: Importiere die globale api-Instanz
// Stelle sicher, dass der Pfad zu 'api.js' korrekt ist,
// ausgehend von der aktuellen Position von TimeTrackingImport.jsx.
// Wenn TimeTrackingImport.jsx im Root-Verzeichnis (gleiche Ebene wie src/) liegt,
// wäre es './src/utils/api' oder wenn es in src/pages/ liegt, dann '../utils/api'.
// Annahme: TimeTrackingImport.jsx ist im Root des src-Ordners oder direkt darunter.
// Wenn TimeTrackingImport.jsx z.B. in src/pages/AdminDashboard/ liegt, wäre der Pfad: ../../utils/api

// Der API-Endpunkt-Pfad bleibt gleich, da die baseURL der api-Instanz
// entweder die VITE_API_BASE_URL ist (z.B. http://localhost:8080)
// oder "/api". In beiden Fällen ist der folgende Pfad korrekt, um
// auf /api/admin/timetracking/import zu zielen.
import './styles/TimeTrackingImport.css';
const API_ENDPOINT_PATH = '/api/admin/timetracking/import';

function TimeTrackingImport() {
    const [selectedFile, setSelectedFile] = useState(null);
    const [isUploading, setIsUploading] = useState(false);
    const [progress, setProgress] = useState(0);
    const [uploadResponse, setUploadResponse] = useState(null);
    const [error, setError] = useState('');
    const [invalidRows, setInvalidRows] = useState([]);
    const [isReimporting, setIsReimporting] = useState(false);
    const [reimportProgress, setReimportProgress] = useState(0);
    // const fileInputRef = useRef(null); // EINKOMMENTIEREN, falls benötigt

    const handleFileChange = (event) => {
        setSelectedFile(event.target.files[0]);
        setUploadResponse(null); // Reset previous response
        setError('');
        setProgress(0);
        setInvalidRows([]);
    };

    const handleSubmit = async (event) => {
        event.preventDefault();
        if (!selectedFile) {
            setError('Bitte wählen Sie zuerst eine Datei aus.');
            return;
        }

        setIsUploading(true);
        setError('');
        setUploadResponse(null);
        setProgress(0);

        const formData = new FormData();
        formData.append('file', selectedFile);

        try {
            // Der Authorization-Header wird jetzt automatisch durch den Interceptor
            // in der globalen api-Instanz hinzugefügt.
            // Die manuelle Token-Abfrage und -Hinzufügung ist nicht mehr nötig.

            const response = await api.post(API_ENDPOINT_PATH, formData, {
                headers: {
                    'Content-Type': 'multipart/form-data',
                },
                onUploadProgress: (e) => {
                    if (e.total) {
                        setProgress(Math.round((e.loaded * 100) / e.total));
                    }
                }
            });

            setUploadResponse(response.data);
            if (response.status === 200 || response.status === 201) {
                setSelectedFile(null);
                // if (fileInputRef.current) { // EINKOMMENTIEREN, falls benötigt
                //     fileInputRef.current.value = null;
                // }
            }

        } catch (err) {
            console.error('Upload error:', err);
            if (err.response) {
                const backendError = err.response.data?.error || err.response.data?.message || 'Ein Fehler ist beim Hochladen aufgetreten.';
                const backendErrorList = err.response.data?.errors;
                setError(backendError);
                if (backendErrorList) {
                    setUploadResponse({ errors: backendErrorList, importedCount: err.response.data?.importedCount || 0, successMessages: err.response.data?.successMessages || [] });
                } else {
                    setUploadResponse({ errors: [backendError], importedCount: 0, successMessages: []});
                }
                setInvalidRows(err.response.data?.invalidRows || []);
            } else if (err.request) {
                setError('Keine Antwort vom Server erhalten. Bitte Netzwerk überprüfen.');
            } else {
                setError('Ein unerwarteter Fehler ist aufgetreten: ' + err.message);
            }
        } finally {
            setIsUploading(false);
            setProgress(0);
        }
    };

    const handleRowChange = (index, field, value) => {
        const rows = [...invalidRows];
        rows[index][field] = value;
        setInvalidRows(rows);
    };

    const handleReimport = async () => {
        if (invalidRows.length === 0) return;
        setIsReimporting(true);
        setReimportProgress(0);
        try {
            const res = await api.post('/api/admin/timetracking/import/json', invalidRows, {
                onUploadProgress: (e) => {
                    if (e.total) {
                        setReimportProgress(Math.round((e.loaded * 100) / e.total));
                    }
                }
            });
            setUploadResponse(res.data);
            setInvalidRows(res.data.invalidRows || []);
        } catch (err) {
            console.error('Reimport error', err);
            setError(err.response?.data?.error || 'Fehler beim Reimport');
        } finally {
            setIsReimporting(false);
            setReimportProgress(0);
        }
    };

    const renderFeedback = () => {
        if (!uploadResponse) return null;

        const { message, importedCount, successMessages, errors: responseErrors } = uploadResponse;

        const msgClass = responseErrors && responseErrors.length > 0 ? 'feedback-warning' : 'feedback-success';

        return (
            <div className="feedback-container">
                <h4>Import Ergebnis:</h4>
                {message && <p className={msgClass}>{message}</p>}
                {typeof importedCount === 'number' && <p>Erfolgreich importierte Einträge: {importedCount}</p>}

                {successMessages && successMessages.length > 0 && (
                    <div>
                        <h5>Erfolgsmeldungen:</h5>
                        <ul style={{ listStyleType: 'disc', paddingLeft: '20px', maxHeight: '150px', overflowY: 'auto' }}>
                            {successMessages.map((msg, index) => (
                                <li key={`succ-${index}`} className="feedback-success" style={{ fontSize: '0.9em' }}>{msg}</li>
                            ))}
                        </ul>
                    </div>
                )}

                {responseErrors && responseErrors.length > 0 && (
                    <div style={{ marginTop: '10px' }}>
                        <h5>Fehlerdetails ({responseErrors.length}):</h5>
                        <ul style={{ listStyleType: 'disc', paddingLeft: '20px', maxHeight: '150px', overflowY: 'auto' }} className="feedback-warning">
                            {responseErrors.map((err, index) => (
                                <li key={`err-${index}`} style={{ fontSize: '0.9em' }}>{typeof err === 'object' ? JSON.stringify(err) : err}</li>
                            ))}
                        </ul>
                    </div>
                )}
                {invalidRows.length > 0 && (
                    <div style={{ marginTop: '10px' }}>
                        <h5>Korrigierbare Zeilen:</h5>
                        <table className="invalid-table">
                            <thead>
                                <tr>
                                    <th>Nr</th>
                                    <th>User</th>
                                    <th>Timestamp</th>
                                    <th>Type</th>
                                    <th>Source</th>
                                    <th>Note</th>
                                    <th>Fehler</th>
                                </tr>
                            </thead>
                            <tbody>
                                {invalidRows.map((row, idx) => (
                                    <tr key={idx}>
                                        <td>{row.rowNumber}</td>
                                        <td><input value={row.username || ''} onChange={e => handleRowChange(idx, 'username', e.target.value)} /></td>
                                        <td><input value={row.timestamp || ''} onChange={e => handleRowChange(idx, 'timestamp', e.target.value)} /></td>
                                        <td><input value={row.punchType || ''} onChange={e => handleRowChange(idx, 'punchType', e.target.value)} /></td>
                                        <td><input value={row.source || ''} onChange={e => handleRowChange(idx, 'source', e.target.value)} /></td>
                                        <td><input value={row.note || ''} onChange={e => handleRowChange(idx, 'note', e.target.value)} /></td>
                                        <td className="error-cell">{row.error}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                        <button onClick={handleReimport} disabled={isReimporting} className="import-button" style={{marginTop:'10px'}}>
                            {isReimporting ? 'Importiere...' : 'Korrigierte Zeilen importieren'}
                        </button>
                        {isReimporting && <div className="import-progress"><div className="import-progress-bar" style={{width: `${reimportProgress}%`}}/></div>}
                    </div>
                )}
            </div>
        );
    };


    return (
        <div className="time-import-page">
            <div className="time-import-container">
                <h3>Stempelzeiten aus Excel importieren</h3>
                <form onSubmit={handleSubmit} className="import-form">
                    <div>
                        <input
                            type="file"
                            onChange={handleFileChange}
                            accept=".xlsx, .xls"
                            // ref={fileInputRef} // EINKOMMENTIEREN, falls benötigt
                            className="file-input"
                        />
                    </div>
                    <button
                        type="submit"
                        disabled={isUploading || !selectedFile}
                        className="import-button"
                    >
                        {isUploading ? 'Wird hochgeladen...' : 'Importieren'}
                    </button>
                    {isUploading && <div className="import-progress"><div className="import-progress-bar" style={{width:`${progress}%`}} /></div>}
                </form>
                {error && <p className="error-message">{error}</p>}
                {renderFeedback()}
                <div className="hint-text">
                <p><strong>Hinweis zum Excel-Format:</strong></p>
                <ul>
                    <li>Die erste Zeile wird als Kopfzeile ignoriert.</li>
                    <li>Spalte A: Username</li>
                    <li>Spalte B: Datum (Format: JJJJ-MM-TT, z.B. 2024-12-31)</li>
                    <li>Spalte C: Arbeitsbeginn (Format: HH:mm, z.B. 08:00)</li>
                    <li>Spalte D: Pausenbeginn (Format: HH:mm, z.B. 12:00, optional)</li>
                    <li>Spalte E: Pausenende (Format: HH:mm, z.B. 12:45, optional)</li>
                    <li>Spalte F: Arbeitsende (Format: HH:mm, z.B. 17:00)</li>
                    <li>Spalte G: Tagesnotiz (optional)</li>
                </ul>
                </div>
            </div>
        </div>
    );
}

export default TimeTrackingImport;