import React from 'react';
import { Link } from 'react-router-dom';
import '../styles/HelpButton.css';

export default function HelpButton() {
    return (
        <Link to="/help" id="help-button" className="help-button">?</Link>
    );
}
