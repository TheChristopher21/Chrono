import React from 'react';
import ChatWidget from './ChatWidget';
import HelpButton from './HelpButton';

// Das Stylesheet für den Container, der die Position festlegt
import '../styles/FloatingButtons.css';

const ActionButtons = () => {
    return (
        // Dieser Container positioniert alles, was in ihm ist
        <div className="scoped-floating-buttons floating-buttons-container">
            <HelpButton />
            <ChatWidget />
        </div>
    );
};

export default ActionButtons;