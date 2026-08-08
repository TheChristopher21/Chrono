// src/components/ChatWidget.jsx
import React, { useState, useEffect, useRef } from 'react';
import ReactMarkdown from 'react-markdown';
import api from '../utils/api';
import { useAuth } from '../context/AuthContext';
import { getUserDisplayName } from '../utils/userDisplay';
import '../styles/ChatWidget.css';

import chatIcon from '../assets/chat-icon.jpg';

function isSafeHref(href) {
    if (!href) {
        return false;
    }

    const trimmedHref = href.trim();
    if (trimmedHref.startsWith('/')) {
        return true;
    }

    try {
        const parsed = new URL(trimmedHref, window.location.origin);
        return ['http:', 'https:', 'mailto:'].includes(parsed.protocol);
    } catch {
        return false;
    }
}

function MarkdownMessage({ text }) {
    return (
        <ReactMarkdown
            components={{
                a: ({ href, children }) => isSafeHref(href)
                    ? <a href={href} rel="noopener noreferrer nofollow">{children}</a>
                    : <span>{children}</span>,
            }}
        >
            {String(text ?? '')}
        </ReactMarkdown>
    );
}

function normalizeSources(sources) {
    return Array.isArray(sources)
        ? sources.filter(source => typeof source === 'string' && source.trim()).slice(0, 4)
        : [];
}

function normalizeSuggestions(suggestions) {
    if (!Array.isArray(suggestions)) {
        return [];
    }
    return suggestions
        .filter(suggestion => suggestion?.label && isSafeHref(suggestion?.url))
        .slice(0, 3);
}

function buildMeta(data) {
    if (!data || (!data.model && !data.latencyMs && !data.remainingRequests && !data.retrievalMode)) {
        return null;
    }
    return {
        model: data.model,
        latencyMs: data.latencyMs,
        remainingRequests: data.remainingRequests,
        retrievalMode: data.retrievalMode,
    };
}

function renderMeta(meta) {
    if (!meta) {
        return null;
    }
    const parts = [];
    if (meta.model) {
        parts.push(meta.model);
    }
    if (Number.isFinite(meta.latencyMs)) {
        parts.push(`${meta.latencyMs} ms`);
    }
    if (Number.isFinite(meta.remainingRequests)) {
        parts.push(`${meta.remainingRequests} übrig`);
    }
    if (!parts.length) {
        return null;
    }
    return <div className="msg-meta">{parts.join(' · ')}</div>;
}

function renderSources(sources) {
    if (!sources?.length) {
        return null;
    }
    return (
        <div className="msg-sources">
            <span>Quellen</span>
            <ul>
                {sources.map(source => <li key={source}>{source}</li>)}
            </ul>
        </div>
    );
}

function renderSuggestions(suggestions) {
    if (!suggestions?.length) {
        return null;
    }
    return (
        <div className="msg-suggestions">
            {suggestions.map(suggestion => (
                <a key={`${suggestion.type}-${suggestion.url}-${suggestion.label}`} href={suggestion.url}>
                    {suggestion.label}
                </a>
            ))}
        </div>
    );
}

function getRandomFallback() {
    const fallbacks = [
        "Der KI-Dienst ist gerade nicht erreichbar. Bitte versuche es in einem Moment noch einmal.",
        "Die Antwort wurde unterbrochen. Sende deine Frage bitte erneut; der bisherige Chat bleibt erhalten."
    ];
    return fallbacks[Math.floor(Math.random() * fallbacks.length)];
}

export default function ChatWidget() {
    const { currentUser } = useAuth();
    const currentUserDisplayName = getUserDisplayName(currentUser);

    const getInitialMessages = () => {
        const saved = sessionStorage.getItem('chatMessages');
        if (saved) return JSON.parse(saved);
        const greet = currentUser ? `Hallo ${currentUserDisplayName}! Wie kann ich dir helfen?` : 'Hallo! Wie kann ich dir helfen?';
        return [{ sender: 'bot', text: greet }];
    };

    const [open, setOpen] = useState(false);
    const [input, setInput] = useState('');
    const [messages, setMessages] = useState(getInitialMessages);
    const [loading, setLoading] = useState(false);
    const [status, setStatus] = useState(null);
    const [statusLoading, setStatusLoading] = useState(false);
    const chatWindowRef = useRef(null);
    const abortControllerRef = useRef(null); // Ref für den AbortController

    useEffect(() => {
        sessionStorage.setItem('chatMessages', JSON.stringify(messages));
    }, [messages]);

    useEffect(() => {
        const greet = currentUser ? `Hallo ${currentUserDisplayName}! Wie kann ich dir helfen?` : 'Hallo! Wie kann ich dir helfen?';
        if (messages.length === 1 && messages[0].sender === 'bot') {
            setMessages([{ sender: 'bot', text: greet }]);
        }
    }, [currentUser, currentUserDisplayName]);

    useEffect(() => {
        if (chatWindowRef.current) {
            chatWindowRef.current.scrollTop = chatWindowRef.current.scrollHeight;
        }
    }, [messages, open]);

    useEffect(() => {
        if (!open || status || statusLoading) {
            return;
        }
        let isMounted = true;
        setStatusLoading(true);
        api.get('/api/chat/status')
            .then(resp => {
                if (isMounted) {
                    setStatus(resp.data);
                }
            })
            .catch(() => {
                if (isMounted) {
                    setStatus({ enabled: false, message: 'Status nicht verfügbar' });
                }
            })
            .finally(() => {
                if (isMounted) {
                    setStatusLoading(false);
                }
            });
        return () => {
            isMounted = false;
        };
    }, [open, status, statusLoading]);

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
        const history = messages
            .filter(message => message?.text)
            .slice(-16)
            .map(message => ({
                sender: message.sender,
                text: message.text,
            }));
        setMessages(m => [...m, userMsg]);
        setInput('');
        setLoading(true);

        try {
            // --- ÄNDERUNG HIER: Füge das Signal und einen längeren Timeout hinzu ---
            const resp = await api.post('/api/chat', { message: userMsg.text, history }, {
                signal, // Signal zum Abbrechen der Anfrage
                timeout: 3 * 60 * 1000 // 3 Minuten Timeout in Millisekunden
            });
            const data = resp.data ?? {};
            const text = data.answer ?? getRandomFallback();
            const finalText = text.startsWith('Entschuldigung, es gab einen Fehler') ? getRandomFallback() : text;
            setMessages(m => [...m, {
                sender: 'bot',
                text: finalText,
                sources: normalizeSources(data.sources),
                suggestions: normalizeSuggestions(data.suggestions),
                meta: buildMeta(data),
            }]);
        } catch (e) {
            if (e.name === 'CanceledError' || e.name === 'AbortError') {
                console.log('Request was canceled by the user.');
            } else {
                const text = e.response?.data?.answer || getRandomFallback();
                setMessages(m => [...m, {
                    sender: 'bot',
                    text,
                    meta: buildMeta(e.response?.data),
                }]);
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
                        <div className="chat-title">
                            <h3>Chrono Assistent</h3>
                            {status && (
                                <span className={`chat-model-status ${status.enabled ? 'ready' : 'locked'}`}>
                                    {status.enabled ? `Bereit · ${status.model ?? 'KI'}` : 'Nicht freigeschaltet'}
                                </span>
                            )}
                        </div>
                        <button className="close-btn" onClick={toggle}>×</button>
                    </div>
                    <div className="chat-window" ref={chatWindowRef}>
                        {messages.map((m, i) => (
                            <div key={i} className={`msg-container msg-${m.sender}`}>
                                <div className="msg-bubble">
                                    <div className="msg-text">
                                        {m.sender === 'bot' ? <MarkdownMessage text={m.text} /> : String(m.text ?? '')}
                                    </div>
                                    {m.sender === 'bot' && renderMeta(m.meta)}
                                    {m.sender === 'bot' && renderSources(m.sources)}
                                    {m.sender === 'bot' && renderSuggestions(m.suggestions)}
                                </div>
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
