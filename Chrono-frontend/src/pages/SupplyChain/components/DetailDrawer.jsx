import React from "react";
import ActivityTimeline from "./ActivityTimeline.jsx";
import ApprovalBar from "./ApprovalBar.jsx";

const DetailDrawer = ({ open, title, record, timelineTitle, timelineItems, approvalLabel, onClose }) => {
    if (!open || !record) {
        return null;
    }

    return (
        <aside className="sc-drawer" aria-label={title}>
            <div className="sc-drawer-head">
                <h3>{title}</h3>
                <button type="button" onClick={onClose}>✕</button>
            </div>
            <dl className="sc-drawer-grid">
                {Object.entries(record).map(([key, value]) => (
                    <React.Fragment key={key}>
                        <dt>{key}</dt>
                        <dd>{String(value ?? "-")}</dd>
                    </React.Fragment>
                ))}
            </dl>
            <ApprovalBar label={approvalLabel} progress={record.approvalProgress ?? 0} />
            <ActivityTimeline title={timelineTitle} items={timelineItems} />
        </aside>
    );
};

export default DetailDrawer;
