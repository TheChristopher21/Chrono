import React from "react";

const statusClassMap = {
    OPEN: "neutral",
    PLANNED: "neutral",
    IN_PROGRESS: "info",
    BLOCKED: "danger",
    CANCELLED: "neutral",
    COMPLETED: "success",
    CLOSED: "success",
    RESOLVED: "success",
};

const StatusChip = ({ value, label }) => {
    const tone = statusClassMap[value] ?? "neutral";
    return <span className={`sc-status-chip ${tone}`}>{label ?? value ?? "-"}</span>;
};

export default StatusChip;
