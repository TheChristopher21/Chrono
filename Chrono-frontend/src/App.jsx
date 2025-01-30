import { AuthProvider } from "./context/AuthContext";
import Navbar from "./components/Navbar.jsx";
import {Route, Routes} from "react-router-dom";
import Register from "./pages/Register.jsx";
import Login from "./pages/Login.jsx";
import Dashboard from "./pages/Dashboard.jsx";
import PrivateRoute from "./components/PrivateRoute.jsx";
import ManagerDashboard from "./pages/ManagerDashboard.jsx";
import NotFound from "./pages/NotFound.jsx"; // Stelle sicher, dass der Import korrekt ist

const App = () => {
    return (
        <AuthProvider>
            <Navbar />
            <Routes>
                <Route path="/" element={<h1>Willkommen bei Chrono</h1>} />
                <Route path="/login" element={<Login />} />
                <Route path="/register" element={<Register />} />

                <Route
                    path="/dashboard"
                    element={
                        <PrivateRoute>
                            <Dashboard />
                        </PrivateRoute>
                    }
                />
                <Route
                    path="/manager"
                    element={
                        <PrivateRoute>
                            <ManagerDashboard />
                        </PrivateRoute>
                    }
                />
                <Route path="*" element={<NotFound />} />
            </Routes>
        </AuthProvider>
    );
};

export default App;
