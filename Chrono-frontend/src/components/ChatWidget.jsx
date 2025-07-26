import React, { useState, useEffect, useRef } from 'react';
import api from '../utils/api';

// Scoped styles for the widget itself
import '../styles/ChatWidget.css';

export default function ChatWidget() {
    const [open, setOpen] = useState(false);
    const [input, setInput] = useState('');
    const [messages, setMessages] = useState(() => {
        const savedMessages = sessionStorage.getItem('chatMessages');
        return savedMessages ? JSON.parse(savedMessages) : [{ sender: 'bot', text: 'Hallo! Wie kann ich dir helfen?' }];
    });
    const [loading, setLoading] = useState(false);
    const chatWindowRef = useRef(null);

    useEffect(() => {
        sessionStorage.setItem('chatMessages', JSON.stringify(messages));
    }, [messages]);

    useEffect(() => {
        if (chatWindowRef.current) {
            chatWindowRef.current.scrollTop = chatWindowRef.current.scrollHeight;
        }
    }, [messages, open]);

    const toggle = () => setOpen(o => !o);

    const send = async () => {
        if (!input.trim() || loading) return;
        const userMsg = { sender: 'user', text: input };
        setMessages(m => [...m, userMsg]);
        setInput('');
        setLoading(true);
        try {
            const resp = await api.post('/api/chat', { message: userMsg.text });
            setMessages(m => [...m, { sender: 'bot', text: resp.data.answer }]);
        } catch (e) {
            setMessages(m => [...m, { sender: 'bot', text: 'Entschuldigung, es gab einen Fehler.' }]);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="scoped-chat-widget">
            <div className="chat-widget">
                <div className={`chat-box ${open ? 'open' : 'closed'}`}>
                    <div className="chat-header">
                        <h3>Chrono Assistent</h3>
                        <button className="close-btn" onClick={toggle}>×</button>
                    </div>
                <div className="chat-window" ref={chatWindowRef}>
                    {messages.map((m, i) => (
                        <div key={i} className={`msg-container msg-${m.sender}`}>
                            <div className="msg-bubble">{m.text}</div>
                        </div>
                    ))}
                    {loading && (
                        <div className="msg-container msg-bot">
                            <div className="msg-bubble typing-indicator">
                                <span></span><span></span><span></span>
                            </div>
                        </div>
                    )}
                </div>
                <div className="chat-input">
                    <input
                        value={input}
                        onChange={e => setInput(e.target.value)}
                        onKeyDown={e => e.key === 'Enter' ? send() : null}
                        placeholder="Stelle eine Frage..."
                        disabled={loading}
                    />
                    <button onClick={send} disabled={loading} className="send-btn">
                        <svg xmlns="http://www.w3.org/2000/svg" height="24px" viewBox="0 -960 960 960" width="24px">
                            <path d="M120-160v-240l320-80-320-80v-240l760 320-760 320Z"/>
                        </svg>
                    </button>
                </div>
            </div>
                <button className="chat-toggle" onClick={toggle} aria-label="Chat öffnen">
                    <svg xmlns="http://www.w3.org/2000/svg" height="24px" viewBox="0 -960 960 960" width="24px">
                        <path d="M80-240v-480q0-33 23.5-56.5T160-800h640q33 0 56.5 23.5T880-720v480q0 33-23.5 56.5T800-160H160q-33 0-56.5-23.5T80-240Zm120-80h40l40 40v-40h400q33 0 56.5-23.5T760-400v-320q0-33-23.5-56.5T680-800H280q-33 0-56.5 23.5T200-720v320h-40l-40 40v-40Z"/>
                    </svg>
                </button>
            </div>
        </div>
    );
}