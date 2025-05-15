// src/pages/Impressum.jsx
import React from 'react';

const Impressum = () => {
    return (
        <div style={{ maxWidth: '800px', margin: '0 auto', padding: '20px' }}>
            <h1>Impressum</h1>

            <p>
                <strong>Chrono-Logisch</strong><br />
                Einzelunternehmen<br />
                Inhaber: Christopher Siefert<br />
                Lettenstrasse 20<br />
                CH-9122 Mogelsberg
            </p>
            <p>
                Telefon: <a href="tel:+41765467960">+41 76 546 79 60</a><br />
                E-Mail: <a href="mailto:siefertchristopher@chrono-logisch.ch">
                siefertchristopher@chrono-logisch.ch
            </a>
            </p>
            <p>
                Verantwortlich für den Inhalt dieser Website: <br />
                Christopher Siefert (Inhaber)
            </p>
            <p>
                <strong>Haftungsausschluss:</strong><br />
                Für die Inhalte externer Links übernehmen wir keine Haftung. Für den
                Inhalt der verlinkten Seiten sind ausschließlich deren Betreiber
                verantwortlich.
            </p>
            <p>
                <strong>Copyright:</strong><br />
                Sämtliche Inhalte (Texte, Bilder, Grafiken) auf dieser Website sind
                urheberrechtlich geschützt. Jegliche Nutzung ohne ausdrückliche
                Zustimmung ist untersagt.
            </p>
            <p>
                <em>Stand: Mai 2025</em>
            </p>
        </div>
    );
};

export default Impressum;
