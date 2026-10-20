import React from "react";

const ActivityTimeline = ({ title, items, locale }) => (
    <section className="sc-timeline">
        <h4>{title}</h4>
        <ul>
            {items.slice(0, 8).map((item) => (
                <li key={item.id}>
                    <strong>{item.action}</strong>
                    <span>{item.user}</span>
                    <time>{item.timestamp ? new Date(item.timestamp).toLocaleString(locale === "de" ? "de-CH" : "en-US") : "-"}</time>
                    <small>{item.reference}</small>
                </li>
            ))}
        </ul>
    </section>
);

export default ActivityTimeline;
