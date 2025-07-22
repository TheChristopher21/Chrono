import React, { useState, useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import { useOnboarding } from '../context/OnboardingContext';
import { useTranslation } from '../context/LanguageContext';
import '../styles/Onboarding.css';

const steps = [
    { id: 'punch-button', key: 'onboarding.punch', icon: '🕒' },
    { id: 'vacation-request-button', key: 'onboarding.vacation', icon: '🏖️' },
    { id: 'payslips-link', key: 'onboarding.payslips', icon: '📄' },
    { id: 'help-button', key: 'onboarding.help', icon: '❓' }
];

export default function OnboardingTour() {
    const { show, finish } = useOnboarding();
    const { t } = useTranslation();
    const [index, setIndex] = useState(0);
    const [pos, setPos] = useState({ top: 0, left: 0 });
    const [visible, setVisible] = useState(false);
    const [showFeedback, setShowFeedback] = useState(false);
    const [feedback, setFeedback] = useState('');
    const [showConfetti, setShowConfetti] = useState(false);
    const tooltipRef = useRef(null);
    const prevTargetRef = useRef(null);

    useEffect(() => {
        if (!show) {
            setIndex(0);
        }
    }, [show]);

    useEffect(() => {
        if (!show) return;

        const updatePos = () => {
            const step = steps[index];
            const target = document.getElementById(step.id);

            if (prevTargetRef.current && prevTargetRef.current !== target) {
                prevTargetRef.current.classList.remove('onboarding-highlight');
            }

            if (target) {
                target.classList.add('onboarding-highlight');
                prevTargetRef.current = target;
                target.scrollIntoView({ block: 'center', behavior: 'smooth' });

                const rect = target.getBoundingClientRect();
                if (tooltipRef.current) {
                    const { offsetWidth: w, offsetHeight: h } = tooltipRef.current;
                    let top = rect.top - h - 12;
                    if (top < 8) top = rect.bottom + 12;

                    let left = rect.left + rect.width / 2 - w / 2;
                    left = Math.max(8, Math.min(left, window.innerWidth - w - 8));

                    setPos({ top, left });
                    setVisible(true);
                    return;
                }
            }

            setVisible(false);
        };

        updatePos();
        window.addEventListener('resize', updatePos);
        window.addEventListener('scroll', updatePos, true);
        const interval = setInterval(updatePos, 500);
        return () => {
            window.removeEventListener('resize', updatePos);
            window.removeEventListener('scroll', updatePos, true);
            clearInterval(interval);
            if (prevTargetRef.current) {
                prevTargetRef.current.classList.remove('onboarding-highlight');
                prevTargetRef.current = null;
            }
        };
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

    if (!show || !visible) return null;

    const step = steps[index];

    const handleFinish = () => {
        finish();
        if (prevTargetRef.current) {
            prevTargetRef.current.classList.remove('onboarding-highlight');
            prevTargetRef.current = null;
        }
        setShowConfetti(true);
        setTimeout(() => {
            setShowConfetti(false);
            setShowFeedback(true);
        }, 1500);
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
                    <span className="step-icon">{step.icon}</span>
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
            {showConfetti && (
                <div className="confetti-overlay">
                    {[...Array(30)].map((_, i) => (
                        <span
                            key={i}
                            className="confetti"
                            style={{
                                '--color': `hsl(${Math.random()*360},70%,60%)`,
                                left: Math.random()*100 + '%',
                                top: '-10px'
                            }}
                        />
                    ))}
                    <div className="onboarding-tooltip" role="alert">
                        {t('onboarding.congrats')}
                    </div>
                </div>
            )}
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
