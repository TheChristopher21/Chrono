import  { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';

const Login = () => {
    const navigate = useNavigate();
    const { login, error } = useAuth();

    const [form, setForm] = useState({ username: '', password: '' });

    const handleChange = (e) => {
        setForm({ ...form, [e.target.name]: e.target.value });
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        const result = await login(form.username, form.password);
        if (result.success) {
            // Erfolg: weiter zum Dashboard
            navigate('/dashboard');
        } else {
            // Fehler: Wir können error von AuthContext anzeigen
            // oder die "message" in result.message loggen usw.
            console.log('Login error detail:', result.message);
        }
    };

    return (
        <div className="login-container">
            <h2>Login</h2>
            {error && <p style={{ color: 'red' }}>{error}</p>}
            <form onSubmit={handleSubmit}>
                <div>
                    <label>Username:
                        <input
                            name="username"
                            value={form.username}
                            onChange={handleChange}
                        />
                    </label>
                </div>
                <div>
                    <label>Password:
                        <input
                            name="password"
                            type="password"
                            value={form.password}
                            onChange={handleChange}
                        />
                    </label>
                </div>
                <button type="submit">Login</button>
            </form>
        </div>
    );
};

export default Login;
