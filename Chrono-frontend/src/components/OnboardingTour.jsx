import React, { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { useOnboarding } from '../context/OnboardingContext';
import '../styles/Onboarding.css';

const steps = [
    { id: 'punch-button', text: 'Hier klickst du, um zu stempeln.' },
    { id: 'vacation-request-button', text: 'Hier stellst du deinen Urlaubsantrag.' },
    { id: 'payslips-link', text: 'Hier findest du deine Abrechnungen.' },
    { id: 'help-button', text: 'Bei Fragen gelangst du hier zur Hilfe.' }
];

export default function OnboardingTour() {
    const { show, finish } = useOnboarding();
    const [index, setIndex] = useState(0);

    useEffect(() => {
        if (!show) setIndex(0);
    }, [show]);

    if (!show) return null;

    const step = steps[index];
    const target = document.getElementById(step.id);
    const rect = target ? target.getBoundingClientRect() : null;

    return createPortal(
        <div className="onboarding-overlay">
            {rect && (
                <div className="onboarding-tooltip" style={{ top: rect.bottom + 8, left: rect.left }}>
                    <p>{step.text}</p>
                    <div className="onboarding-actions">
                        {index > 0 && <button onClick={() => setIndex(index - 1)}>Zurück</button>}
                        {index < steps.length - 1 ? (
                            <button onClick={() => setIndex(index + 1)}>Weiter</button>
                        ) : (
                            <button onClick={finish}>Fertig</button>
                        )}
                    </div>
                </div>
            )}
        </div>,
        document.body
    );
}
