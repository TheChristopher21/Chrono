// src/components/ChatWidget.jsx
import React, { useState, useEffect, useRef } from 'react';
import api from '../utils/api';
import '../styles/ChatWidget.css';

import chatIcon from '../assets/chat-icon.jpg';

export default function ChatWidget() {
    const [open, setOpen] = useState(false);
    const [input, setInput] = useState('');
    const [messages, setMessages] = useState(() => {
        const savedMessages = sessionStorage.getItem('chatMessages');
        return savedMessages ? JSON.parse(savedMessages) : [{ sender: 'bot', text: 'Hallo! Wie kann ich dir helfen?' }];
    });
    const [loading, setLoading] = useState(false);
    const chatWindowRef = useRef(null);
    const abortControllerRef = useRef(null); // Ref für den AbortController

    useEffect(() => {
        sessionStorage.setItem('chatMessages', JSON.stringify(messages));
    }, [messages]);

    useEffect(() => {
        if (chatWindowRef.current) {
            chatWindowRef.current.scrollTop = chatWindowRef.current.scrollHeight;
        }
    }, [messages, open]);

    const toggle = () => {
        setOpen(o => !o);
        // Wenn das Widget geschlossen wird, brechen wir eine laufende Anfrage ab.
        if (open && abortControllerRef.current) {
            abortControllerRef.current.abort();
        }
    };

    const send = async () => {
        if (!input.trim() || loading) return;

        // --- NEUER CODE HIER ---
        // Erstelle einen neuen AbortController für diese Anfrage
        abortControllerRef.current = new AbortController();
        const { signal } = abortControllerRef.current;
        // -----------------------

        const userMsg = { sender: 'user', text: input };
        setMessages(m => [...m, userMsg]);
        setInput('');
        setLoading(true);

        try {
            // --- ÄNDERUNG HIER: Füge das Signal und einen längeren Timeout hinzu ---
            const resp = await api.post('/api/chat', { message: userMsg.text }, {
                signal, // Signal zum Abbrechen der Anfrage
                timeout: 3 * 60 * 1000 // 3 Minuten Timeout in Millisekunden
            });
            setMessages(m => [...m, { sender: 'bot', text: resp.data.answer }]);
        } catch (e) {
            if (e.name === 'CanceledError' || e.name === 'AbortError') {
                console.log('Request was canceled by the user.');
            } else {
                setMessages(m => [...m, { sender: 'bot', text: 'Entschuldigung, es gab einen Fehler. Bitte versuche es erneut.' }]);
            }
        } finally {
            setLoading(false);
            abortControllerRef.current = null; // Setze den Controller zurück
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
                    <img src={chatIcon} alt="Chat Assistent Icon" />
                </button>
            </div>
        </div>
    );
}