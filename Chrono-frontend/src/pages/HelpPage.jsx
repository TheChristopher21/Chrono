import React from 'react';
import Navbar from '../components/Navbar';

const HelpPage = () => (
    <div>
        <Navbar />
        <div className="page-container">
            <h1>Hilfe &amp; Support</h1>
            <p>Hier findest du Antworten auf häufige Fragen.</p>
            <p>Videoanleitung:</p>
            <video controls width="320">
                <source src="https://archive.org/download/ElephantsDream/ed_1024_512kb.mp4" type="video/mp4" />
            </video>
        </div>
    </div>
);

export default HelpPage;
