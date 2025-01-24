import { useEffect, useState } from "react";
import { getLatestTimeTracking } from "../utils/api"; // API-Aufruf
import { useAuth } from "../context/AuthContext";

const Dashboard = () => {
    const { user } = useAuth();
    const [timeData, setTimeData] = useState(null);
    const [errorMessage, setErrorMessage] = useState("");

    useEffect(() => {
        const fetchData = async () => {
            try {
                const data = await getLatestTimeTracking(user.userId); // Übermittle die userId
                setTimeData(data);
            } catch (error) {
                if (error.response) {
                    setErrorMessage("Fehler beim Abrufen der Zeitdaten: " + error.response.data);
                } else {
                    setErrorMessage("Netzwerkfehler: Bitte versuche es später erneut.");
                }
            }
        };

        fetchData();
    }, [user]);

    if (!user) {
        return <p>Bitte einloggen...</p>;
    }

    return (
        <div>
            <h1>Willkommen, {user.username}</h1>
            <p>Rolle: {user.role}</p>
            <h2>Aktuelle Zeiten</h2>
            {errorMessage ? (
                <p style={{ color: "red" }}>{errorMessage}</p>
            ) : timeData ? (
                <div>
                    <p>Check-In: {timeData.punchIn || "Nicht eingestempelt"}</p>
                    <p>Check-Out: {timeData.punchOut || "Nicht ausgestempelt"}</p>
                    <p>Gesamtstunden: {timeData.totalHours || "0h"}</p>
                </div>
            ) : (
                <p>Loading...</p>
            )}
            <button>Einchecken</button>
            <button>Auschecken</button>
        </div>
    );
};

export default Dashboard;
