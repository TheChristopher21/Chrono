import React from "react";
import ActivityTimeline from "./ActivityTimeline.jsx";
import ApprovalBar from "./ApprovalBar.jsx";
import HelpLabel from "./HelpLabel.jsx";

const formatKey = (key) => String(key)
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .replace(/[_-]+/g, " ")
    .trim();

const hiddenRecordKeys = new Set(["statusLabel"]);

const formatValue = (key, value, record, locale) => {
    if (key === "status") {
        return record?.statusLabel ?? String(value ?? "-");
    }

    if (key === "approvalProgress") {
        return `${Number(value ?? 0)}%`;
    }

    if (typeof value === "number" && Number.isFinite(value)) {
        return new Intl.NumberFormat(locale === "de" ? "de-CH" : "en-US").format(value);
    }

    return String(value ?? "-");
};

const DetailDrawer = ({ open, title, record, fieldLabels, fieldHelp, timelineTitle, timelineItems, approvalLabel, locale, actionContent, onClose }) => {
    if (!open || !record) {
        return null;
    }

    return (
        <aside className="sc-drawer" aria-label={title}>
            <div className="sc-drawer-head">
                <h3>{title}</h3>
                <button type="button" className="secondary" onClick={onClose}>x</button>
            </div>
            <dl className="sc-drawer-grid">
                {Object.entries(record)
                    .filter(([key]) => !hiddenRecordKeys.has(key))
                    .map(([key, value]) => (
                    <React.Fragment key={key}>
                        <dt>
                            <HelpLabel label={fieldLabels?.[key] ?? formatKey(key)} help={fieldHelp?.[key]} />
                        </dt>
                        <dd>{formatValue(key, value, record, locale)}</dd>
                    </React.Fragment>
                    ))}
            </dl>
            {actionContent}
            <ApprovalBar label={approvalLabel} progress={record.approvalProgress ?? 0} />
            <ActivityTimeline title={timelineTitle} items={timelineItems} locale={locale} />
        </aside>
    );
};

export default DetailDrawer;
