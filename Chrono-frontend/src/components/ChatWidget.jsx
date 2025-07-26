import React, { useState, useEffect, useRef } from 'react';
import api from '../utils/api';
import '../styles/ChatWidget.css';

export default function ChatWidget() {
    const [open, setOpen] = useState(false);
    const [input, setInput] = useState('');
    const [messages, setMessages] = useState(() => {
        // Lade Nachrichten aus dem Session Storage oder starte mit einer Willkommensnachricht
        const savedMessages = sessionStorage.getItem('chatMessages');
        return savedMessages ? JSON.parse(savedMessages) : [{ sender: 'bot', text: 'Hallo! Wie kann ich dir helfen?' }];
    });
    const [loading, setLoading] = useState(false);
    const chatWindowRef = useRef(null);

    // Speichere Nachrichten im Session Storage, wenn sie sich ändern
    useEffect(() => {
        sessionStorage.setItem('chatMessages', JSON.stringify(messages));
    }, [messages]);

    // Automatisch nach unten scrollen, wenn neue Nachrichten hinzukommen
    useEffect(() => {
        if (chatWindowRef.current) {
            chatWindowRef.current.scrollTop = chatWindowRef.current.scrollHeight;
        }
    }, [messages, open]);


    const toggle = () => setOpen(o => !o);

    const send = async () => {
        if (!input || loading) return;

        const userMsg = { sender: 'user', text: input };
        setMessages(m => [...m, userMsg]);
        setInput('');
        setLoading(true);

        try {
            const resp = await api.post('/api/chat', { message: userMsg.text });
            setMessages(m => [...m, { sender: 'bot', text: resp.data.answer }]);
        } catch (e) {
            setMessages(m => [...m, { sender: 'bot', text: 'Entschuldigung, es gab einen Fehler bei der Anfrage.' }]);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className={`chat-widget ${open ? 'open' : ''}`}>
            {open ? (
                <div className="chat-box">
                    <div className="chat-header">
                        <span>Chrono-Assistent</span>
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
                        <button onClick={send} disabled={loading}>Senden</button>
                    </div>
                </div>
            ) : (
                <button className="chat-toggle" onClick={toggle}>
                    <svg xmlns="http://www.w3.org/2000/svg" height="24px" viewBox="0 -960 960 960" width="24px" fill="#FFFFFF"><path d="M800-360q-33 0-56.5-23.5T720-440q0-33 23.5-56.5T800-520q33 0 56.5 23.5T880-440q0 33-23.5 56.5T800-360Zm-320 0q-33 0-56.5-23.5T400-440q0-33 23.5-56.5T480-520q33 0 56.5 23.5T560-440q0 33-23.5 56.5T480-360Zm-320 0q-33 0-56.5-23.5T80-440q0-33 23.5-56.5T160-520q33 0 56.5 23.5T240-440q0 33-23.5 56.5T160-360ZM80-80v-720q0-33 23.5-56.5T160-880h640q33 0 56.5 23.5T880-800v480q0 33-23.5 56.5T800-240H240L80-80Z"/></svg>
                </button>
            )}
        </div>
    );
}