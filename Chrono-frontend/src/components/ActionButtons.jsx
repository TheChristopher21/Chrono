import React from 'react';
import ChatWidget from './ChatWidget';
import HelpButton from './HelpButton';
// Wir importieren das NEUE, zentrale Stylesheet
import '../styles/FloatingButtons.css';

const ActionButtons = () => {
    return (
        // Dieser Container wird vom neuen Stylesheet korrekt positioniert
        <div className="scoped-floating-buttons floating-buttons-container">
            <HelpButton />
            <ChatWidget />
        </div>
    );
};

export default ActionButtons;