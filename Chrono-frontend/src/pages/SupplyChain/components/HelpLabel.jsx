import React from "react";
import InlineHelp from "./InlineHelp.jsx";

const HelpLabel = ({ label, help, className = "" }) => (
    <span className={`sc-help-label ${className}`.trim()}>
        <span>{label}</span>
        {help ? <InlineHelp title={help.title ?? String(label)} description={help.description} /> : null}
    </span>
);

export default HelpLabel;
