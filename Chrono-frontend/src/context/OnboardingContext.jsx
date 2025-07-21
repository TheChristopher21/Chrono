import React, { createContext, useContext, useState } from 'react';

const OnboardingContext = createContext({
    show: false,
    start: () => {},
    finish: () => {}
});

export function OnboardingProvider({ children }) {
    const [show, setShow] = useState(false);

    const start = () => {
        if (localStorage.getItem('onboardingDone') !== 'true') {
            setShow(true);
        }
    };
    const finish = () => {
        setShow(false);
        localStorage.setItem('onboardingDone', 'true');
    };

    return (
        <OnboardingContext.Provider value={{ show, start, finish }}>
            {children}
        </OnboardingContext.Provider>
    );
}

export function useOnboarding() {
    return useContext(OnboardingContext);
}
