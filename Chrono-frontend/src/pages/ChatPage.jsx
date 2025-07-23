import React, { useState } from 'react';
import Navbar from '../components/Navbar';
import api from '../utils/api';
import '../styles/ChatPage.css';

const ChatPage = () => {
    const [input, setInput] = useState('');
    const [messages, setMessages] = useState([]);

    const send = async () => {
        if (!input) return;
        const userMsg = { sender: 'user', text: input };
        setMessages(m => [...m, userMsg]);
        setInput('');
        try {
            const resp = await api.post('/api/chat', { message: userMsg.text });
            setMessages(m => [...m, { sender: 'bot', text: resp.data.answer }]);
        } catch (e) {
            setMessages(m => [...m, { sender: 'bot', text: 'Fehler bei Anfrage' }]);
        }
    };

    return (
        <div>
            <Navbar />
            <div className="page-container">
                <h1>Chatbot</h1>
                <div className="chat-window">
                    {messages.map((m, i) => (
                        <div key={i} className={`msg msg-${m.sender}`}>{m.text}</div>
                    ))}
                </div>
                <div className="chat-input">
                    <input value={input} onChange={e => setInput(e.target.value)} />
                    <button onClick={send}>Senden</button>
                </div>
            </div>
        </div>
    );
};

export default ChatPage;
