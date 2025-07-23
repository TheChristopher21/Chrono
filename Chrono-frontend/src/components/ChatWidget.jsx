import React, { useState } from 'react';
import api from '../utils/api';
import '../styles/ChatWidget.css';

export default function ChatWidget() {
    const [open, setOpen] = useState(false);
    const [input, setInput] = useState('');
    const [messages, setMessages] = useState([]);

    const toggle = () => setOpen(o => !o);

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
        <div className={`chat-widget ${open ? 'open' : ''}`}>
            {open ? (
                <div className="chat-box">
                    <div className="chat-header">
                        <button className="close-btn" onClick={toggle}>×</button>
                    </div>
                    <div className="chat-window">
                        {messages.map((m, i) => (
                            <div key={i} className={`msg msg-${m.sender}`}>{m.text}</div>
                        ))}
                    </div>
                    <div className="chat-input">
                        <input
                            value={input}
                            onChange={e => setInput(e.target.value)}
                            onKeyDown={e => e.key === 'Enter' ? send() : null}
                        />
                        <button onClick={send}>Senden</button>
                    </div>
                </div>
            ) : (
                <button className="chat-toggle" onClick={toggle}>Chat</button>
            )}
        </div>
    );
}
