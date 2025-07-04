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
const API_ENDPOINT_PATH = '/api/admin/timetracking/import-async';

function TimeTrackingImport() {
    const [selectedFile, setSelectedFile] = useState(null);
    const [isUploading, setIsUploading] = useState(false);
    const [uploadResponse, setUploadResponse] = useState(null);
    const [progress, setProgress] = useState(null);
    const [error, setError] = useState('');
    // const fileInputRef = useRef(null); // EINKOMMENTIEREN, falls benötigt

    const handleFileChange = (event) => {
        setSelectedFile(event.target.files[0]);
        setUploadResponse(null); // Reset previous response
        setError('');
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

        const formData = new FormData();
        formData.append('file', selectedFile);

        try {
            const res = await api.post(API_ENDPOINT_PATH, formData, {
                headers: { 'Content-Type': 'multipart/form-data' },
            });
            const { importId } = res.data;
            setProgress({ processedRows: 0, totalRows: 0 });
            const interval = setInterval(async () => {
                try {
                    const statusRes = await api.get(`/api/admin/timetracking/import-status/${importId}`);
                    setProgress({ processedRows: statusRes.data.processedRows, totalRows: statusRes.data.totalRows });
                    if (statusRes.data.completed) {
                        clearInterval(interval);
                        setUploadResponse(statusRes.data);
                        setIsUploading(false);
                        setSelectedFile(null);
                    }
                } catch (e) {
                    clearInterval(interval);
                    setIsUploading(false);
                }
            }, 1000);

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
            } else if (err.request) {
                setError('Keine Antwort vom Server erhalten. Bitte Netzwerk überprüfen.');
            } else {
                setError('Ein unerwarteter Fehler ist aufgetreten: ' + err.message);
            }
        } finally {
            setIsUploading(false);
        }
    };

    const renderFeedback = () => {
        if (!uploadResponse) return null;

        const { message, importedCount, successMessages, errors: responseErrors } = uploadResponse;

        return (
            <div style={{ marginTop: '20px', padding: '10px', border: '1px solid #eee' }}>
                <h4>Import Ergebnis:</h4>
                {message && <p style={{ color: responseErrors && responseErrors.length > 0 ? 'orange' : 'green' }}>{message}</p>}
                {typeof importedCount === 'number' && <p>Erfolgreich importierte Einträge: {importedCount}</p>}

                {successMessages && successMessages.length > 0 && (
                    <div>
                        <h5>Erfolgsmeldungen:</h5>
                        <ul style={{ listStyleType: 'disc', paddingLeft: '20px', maxHeight: '150px', overflowY: 'auto' }}>
                            {successMessages.map((msg, index) => (
                                <li key={`succ-${index}`} style={{ color: 'green', fontSize: '0.9em' }}>{msg}</li>
                            ))}
                        </ul>
                    </div>
                )}

                {responseErrors && responseErrors.length > 0 && (
                    <div style={{ marginTop: '10px' }}>
                        <h5>Fehlerdetails ({responseErrors.length}):</h5>
                        <ul style={{ listStyleType: 'disc', paddingLeft: '20px', maxHeight: '150px', overflowY: 'auto', color: 'red' }}>
                            {responseErrors.map((err, index) => (
                                <li key={`err-${index}`} style={{ fontSize: '0.9em' }}>{typeof err === 'object' ? JSON.stringify(err) : err}</li>
                            ))}
                        </ul>
                    </div>
                )}
            </div>
        );
    };


    return (
        <div style={{ margin: '20px', padding: '20px', border: '1px solid #ccc', borderRadius: '5px' }}>
            <h3>Stempelzeiten aus Excel importieren</h3>
            <form onSubmit={handleSubmit}>
                <div>
                    <input
                        type="file"
                        onChange={handleFileChange}
                        accept=".xlsx, .xls"
                        // ref={fileInputRef} // EINKOMMENTIEREN, falls benötigt
                        style={{ marginBottom: '10px', padding: '8px', border: '1px solid #ddd', borderRadius: '4px' }}
                    />
                </div>
                <button
                    type="submit"
                    disabled={isUploading || !selectedFile}
                    style={{
                        padding: '10px 15px',
                        backgroundColor: isUploading || !selectedFile ? '#ccc' : '#007bff',
                        color: 'white',
                        border: 'none',
                        borderRadius: '4px',
                        cursor: isUploading || !selectedFile ? 'not-allowed' : 'pointer'
                    }}
                >
                    {isUploading ? 'Wird hochgeladen...' : 'Importieren'}
                </button>
            </form>
            {error && <p style={{ color: 'red', marginTop: '10px' }}>{error}</p>}
            {renderFeedback()}
            {progress && !uploadResponse && (
                <div style={{ marginTop: '10px' }}>
                    <div style={{ height: '8px', background: '#eee', borderRadius: '4px' }}>
                        <div style={{ width: `${progress.totalRows ? (progress.processedRows / progress.totalRows) * 100 : 0}%`, height: '100%', background: '#007bff', borderRadius: '4px' }} />
                    </div>
                    <small>{progress.processedRows}/{progress.totalRows} verarbeitet</small>
                </div>
            )}
            <div style={{marginTop: '20px', fontSize: '0.9em', color: '#555'}}>
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
    );
}

export default TimeTrackingImport;