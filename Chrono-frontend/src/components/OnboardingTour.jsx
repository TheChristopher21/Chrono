import React, { useState, useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import { useOnboarding } from '../context/OnboardingContext';
import { useTranslation } from '../context/LanguageContext';
import '../styles/Onboarding.css';

const steps = [
    { id: 'punch-button', key: 'onboarding.punch' },
    { id: 'vacation-request-button', key: 'onboarding.vacation' },
    { id: 'payslips-link', key: 'onboarding.payslips' },
    { id: 'help-button', key: 'onboarding.help' }
];

export default function OnboardingTour() {
    const { show, finish } = useOnboarding();
    const { t } = useTranslation();
    const [index, setIndex] = useState(0);
    const [pos, setPos] = useState({ top: 0, left: 0 });
    const [showFeedback, setShowFeedback] = useState(false);
    const [feedback, setFeedback] = useState('');
    const tooltipRef = useRef(null);

    useEffect(() => {
        if (!show) {
            setIndex(0);
        }
    }, [show]);

    useEffect(() => {
        const step = steps[index];
        const target = document.getElementById(step.id);
        const rect = target ? target.getBoundingClientRect() : null;
        if (rect && tooltipRef.current) {
            const { offsetWidth: w, offsetHeight: h } = tooltipRef.current;
            let top = rect.bottom + 8;
            let left = rect.left;
            if (top + h > window.innerHeight) {
                top = rect.top - h - 8;
            }
            if (left + w > window.innerWidth) {
                left = window.innerWidth - w - 8;
            }
            setPos({ top, left });
        }
    }, [index, show]);

    const handleKey = (e) => {
        if (e.key === 'Escape') {
            handleFinish();
        } else if (e.key === 'ArrowRight' && index < steps.length - 1) {
            setIndex(i => i + 1);
        } else if (e.key === 'ArrowLeft' && index > 0) {
            setIndex(i => i - 1);
        }
    };

    if (!show) return null;

    const step = steps[index];

    const handleFinish = () => {
        finish();
        setShowFeedback(true);
    };

    return createPortal(
        <>
            <div className="onboarding-overlay" onKeyDown={handleKey} tabIndex="-1">
                <div
                    ref={tooltipRef}
                    className="onboarding-tooltip"
                    style={{ top: pos.top, left: pos.left }}
                    role="dialog"
                    aria-live="polite"
                >
                    <p>{t(step.key)}</p>
                    <div className="onboarding-actions">
                        {index > 0 && <button onClick={() => setIndex(index - 1)}>{t('onboarding.prev')}</button>}
                        {index < steps.length - 1 ? (
                            <button onClick={() => setIndex(index + 1)}>{t('onboarding.next')}</button>
                        ) : (
                            <button onClick={handleFinish}>{t('onboarding.done')}</button>
                        )}
                    </div>
                </div>
            </div>
            {showFeedback && (
                <div className="feedback-overlay">
                    <div className="feedback-popup">
                        <p>{t('onboarding.feedbackPrompt')}</p>
                        <textarea value={feedback} onChange={e => setFeedback(e.target.value)} placeholder={t('onboarding.feedbackPlaceholder')} />
                        <button onClick={() => { localStorage.setItem('onboardingFeedback', feedback); setShowFeedback(false); }}>{t('onboarding.feedbackSend')}</button>
                    </div>
                </div>
            )}
        </>,
        document.body
    );
}
