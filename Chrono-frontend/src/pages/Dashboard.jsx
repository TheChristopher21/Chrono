import { useEffect, useState } from "react";
import { useAuth } from "../hooks/useAuth";
import api from "../utils/api";
import "../styles/Dashboard.css";

const Dashboard = () => {
    const { currentUser } = useAuth();
    const [history, setHistory] = useState([]);
    const [correctionRequests, setCorrectionRequests] = useState([]);
    const [loading, setLoading] = useState(true);
    const [editCorrectionId, setEditCorrectionId] = useState(null);
    const [desiredStart, setDesiredStart] = useState("");
    const [desiredEnd, setDesiredEnd] = useState("");
    const [reason, setReason] = useState("");

    const authToken = localStorage.getItem("token");
    const isAdmin = currentUser?.roles?.includes("ROLE_ADMIN");
    const isUser = currentUser?.roles?.includes("ROLE_USER");

    useEffect(() => {
        if (!currentUser) return;
        setLoading(true);

        if (isAdmin) {
            api.get("/api/correction/open", {
                headers: { Authorization: `Bearer ${authToken}` }
            })
                .then(res => {
                    setCorrectionRequests(res.data);
                    setLoading(false);
                })
                .catch(err => {
                    console.error("❌ Error fetching correction requests:", err);
                    setLoading(false);
                });
        } else if (isUser) {
            api.get(`/api/timetracking/history?username=${currentUser.username}`, {
                headers: { Authorization: `Bearer ${authToken}` }
            })
                .then(res => {
                    setHistory(res.data);
                    setLoading(false);
                })
                .catch(err => {
                    console.error("❌ Error fetching user history:", err);
                    setLoading(false);
                });
        }
    }, [currentUser, isAdmin, isUser, authToken]);

    const refreshAdminCorrections = () => {
        api.get("/api/correction/open", {
            headers: { Authorization: `Bearer ${authToken}` }
        })
            .then(res => setCorrectionRequests(res.data))
            .catch(err => console.error("❌ Refreshing admin corrections failed", err));
    };

    const handleOpenCorrectionForm = (entry) => {
        setEditCorrectionId(entry.id);
        const date = new Date(entry.startTime).toISOString().split("T")[0];
        setDesiredStart(`${date}T00:00`);
        setDesiredEnd(`${date}T00:00`);
        setReason("");
    };

    const handleCancelCorrection = () => {
        setEditCorrectionId(null);
    };

    const handleCreateCorrection = (e) => {
        e.preventDefault();
        const payload = new URLSearchParams({
            username: currentUser.username,
            desiredStart,
            desiredEnd,
            reason
        });

        api.post(`/api/correction/create?${payload.toString()}`, {}, {
            headers: { Authorization: `Bearer ${authToken}` }
        })
            .then(() => {
                alert("Correction request sent!");
                setEditCorrectionId(null);
                refreshAdminCorrections();
            })
            .catch(err => console.error("❌ Correction request failed", err));
    };

    return (
        <div className="dashboard-container">
            <h2>Dashboard</h2>
            <p>Welcome, {currentUser.username}!</p>

            {isAdmin && (
                <>
                    <h3>Correction Requests</h3>
                    {loading ? (
                        <p>Loading...</p>
                    ) : correctionRequests.length === 0 ? (
                        <p>No correction requests found.</p>
                    ) : (
                        <table className="correction-table">
                            <thead>
                            <tr>
                                <th>User</th>
                                <th>Original Start</th>
                                <th>Original End</th>
                                <th>Corrected Start</th>
                                <th>Corrected End</th>
                                <th>Reason</th>
                            </tr>
                            </thead>
                            <tbody>
                            {correctionRequests.map(req => {
                                let originalStart = req.originalTimeTracking?.startTime ? new Date(req.originalTimeTracking.startTime) : null;
                                let originalEnd = req.originalTimeTracking?.endTime ? new Date(req.originalTimeTracking.endTime) : null;
                                let correctedStart = req.desiredStart ? new Date(req.desiredStart) : null;
                                let correctedEnd = req.desiredEnd ? new Date(req.desiredEnd) : null;

                                return (
                                    <tr key={req.id}>
                                        <td>{req.user?.username || "Unknown"}</td>
                                        <td>{originalStart ? originalStart.toLocaleString() : "-"}</td>
                                        <td>{originalEnd ? originalEnd.toLocaleString() : "-"}</td>
                                        <td>{correctedStart ? correctedStart.toLocaleString() : "-"}</td>
                                        <td>{correctedEnd ? correctedEnd.toLocaleString() : "-"}</td>
                                        <td>{req.reason || "-"}</td>
                                    </tr>
                                );
                            })}
                            </tbody>


                        </table>
                    )}
                </>
            )}

            {isUser && (
                <>
                    <h3>Your Time Entries</h3>
                    {loading ? (
                        <p>Loading...</p>
                    ) : history.length === 0 ? (
                        <p>No entries yet</p>
                    ) : (
                        <ul>
                            {history.map(entry => (
                                <li key={entry.id}>
                                    Start: {new Date(entry.startTime).toLocaleString()} -
                                    End: {entry.endTime ? new Date(entry.endTime).toLocaleString() : "..."}
                                    <button onClick={() => handleOpenCorrectionForm(entry)}>Request Correction</button>
                                    {editCorrectionId === entry.id && (
                                        <form onSubmit={handleCreateCorrection}>
                                            <input type="time" value={desiredStart.split("T")[1]} onChange={(e) => setDesiredStart(`${desiredStart.split("T")[0]}T${e.target.value}`)} required />
                                            <input type="time" value={desiredEnd.split("T")[1]} onChange={(e) => setDesiredEnd(`${desiredEnd.split("T")[0]}T${e.target.value}`)} required />
                                            <input type="text" placeholder="Reason" value={reason} onChange={(e) => setReason(e.target.value)} required />
                                            <button type="submit">Submit</button>
                                            <button type="button" onClick={handleCancelCorrection}>Cancel</button>
                                        </form>
                                    )}
                                </li>
                            ))}
                        </ul>
                    )}
                </>
            )}
        </div>
    );
};

export default Dashboard;
