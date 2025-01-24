import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { login } from "../utils/api"; // API-Aufruf für Login

const Login = () => {
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [errorMessage, setErrorMessage] = useState("");
    const navigate = useNavigate();
    const { login: setAuthUser } = useAuth();

    const handleLogin = async (e) => {
        e.preventDefault();

        try {
            const response = await login({ username, password });

            console.log("Erfolgreich eingeloggt:", response);

            // Speichere den Token im Local Storage
            localStorage.setItem("token", response.token);

            // Speichere die Benutzerdaten im Auth-Kontext
            setAuthUser({
                username: response.username,
                role: response.role,
                userId: response.userId,
            });

            // Weiterleitung zum Dashboard
            navigate("/dashboard");
        } catch (error) {
            if (error.response) {
                // Fehler vom Server
                console.error("Login fehlgeschlagen:", error.response.data);
                setErrorMessage("Login fehlgeschlagen: " + error.response.data);
            } else {
                // Netzwerkfehler
                console.error("Netzwerkfehler:", error.message);
                setErrorMessage("Netzwerkfehler: Bitte versuche es später erneut.");
            }
        }
    };

    return (
        <div>
            <h1>Login</h1>
            <form onSubmit={handleLogin}>
                <input
                    type="text"
                    placeholder="Benutzername"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                />
                <input
                    type="password"
                    placeholder="Passwort"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                />
                <button type="submit">Einloggen</button>
            </form>
            {errorMessage && <p style={{ color: "red" }}>{errorMessage}</p>}
        </div>
    );
};

export default Login;
