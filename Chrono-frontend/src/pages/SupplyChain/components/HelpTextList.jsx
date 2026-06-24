import React from "react";
import HelpLabel from "./HelpLabel.jsx";

const HelpTextList = ({ items = [], separator = ", " }) => (
    <span className="sc-help-text-list">
        {items.map((item, index) => {
            const normalized = typeof item === "string" ? { label: item } : item;
            const key = normalized.id ?? `${normalized.label}-${index}`;

            return (
                <React.Fragment key={key}>
                    {index > 0 ? <span className="sc-help-text-separator">{separator}</span> : null}
                    <HelpLabel label={normalized.label} help={normalized.help} className="sc-help-text-item" />
                </React.Fragment>
            );
        })}
    </span>
);

export default HelpTextList;
