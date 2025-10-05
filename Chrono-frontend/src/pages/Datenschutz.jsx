import React, { useEffect } from 'react';
import Navbar from '../components/Navbar';
import '../styles/LegalPages.css';

const Datenschutz = () => {
    useEffect(() => {
        // Script einbinden, sobald die Seite geladen wird
        const script = document.createElement("script");
        script.src = "https://app.privacybee.io/widget.js";
        script.defer = true;
        document.body.appendChild(script);

        return () => {
            // Script beim Verlassen der Seite wieder entfernen
            document.body.removeChild(script);
        };
    }, []);

    return (
        <>
            <Navbar />
            <div className="legal-wrapper">
                <div className="legal-page">
                    <h1>Datenschutzerklärung</h1>

                    <privacybee-widget
                        website-id="cmg29z1in00v4boymy5yx1ttp"
                        type="dsg"
                        lang="de"
                    ></privacybee-widget>
                </div>
            </div>
        </>
    );
};

export default Datenschutz;
