import React, { useEffect, useState } from "react";

const CycleCountDrawerActions = ({ record, text, onSubmitCount, onApprove }) => {
    const [countedQuantity, setCountedQuantity] = useState(record?.countedQuantity ?? record?.expectedQuantity ?? "");
    const [submitting, setSubmitting] = useState(false);
    const [approving, setApproving] = useState(false);

    useEffect(() => {
        setCountedQuantity(record?.countedQuantity ?? record?.expectedQuantity ?? "");
    }, [record]);

    if (!record) {
        return null;
    }

    const handleSubmit = async (event) => {
        event.preventDefault();
        if (!onSubmitCount || submitting) return;
        setSubmitting(true);
        try {
            await onSubmitCount(Number(countedQuantity));
        } finally {
            setSubmitting(false);
        }
    };

    const handleApprove = async () => {
        if (!onApprove || approving) return;
        setApproving(true);
        try {
            await onApprove();
        } finally {
            setApproving(false);
        }
    };

    if (record.status === "APPROVAL_REQUIRED") {
        return (
            <section className="sc-drawer-action-panel">
                <h4>{text.approvalTitle}</h4>
                <p>{text.approvalText}</p>
                <div className="sc-drawer-action-metrics">
                    <span>{text.expectedLabel}: {record.expectedQuantity ?? "-"}</span>
                    <span>{text.countedLabel}: {record.countedQuantity ?? "-"}</span>
                    <span>{text.varianceLabel}: {record.variance ?? "-"}</span>
                </div>
                <div className="panel-actions">
                    <button type="button" onClick={handleApprove} disabled={approving}>
                        {approving ? text.approvingLabel : text.approveLabel}
                    </button>
                </div>
            </section>
        );
    }

    if (record.status !== "PLANNED") {
        return (
            <section className="sc-drawer-action-panel">
                <h4>{text.completedTitle}</h4>
                <p>{text.completedText}</p>
            </section>
        );
    }

    return (
        <form className="sc-drawer-action-panel" onSubmit={handleSubmit}>
            <h4>{text.countTitle}</h4>
            <p>{text.countText}</p>
            <label className="sc-drawer-action-field">
                <span>{text.countedLabel}</span>
                <input
                    type="number"
                    step="0.01"
                    min="0"
                    value={countedQuantity}
                    onChange={(event) => setCountedQuantity(event.target.value)}
                    required
                />
            </label>
            <div className="panel-actions">
                <button type="submit" disabled={submitting}>
                    {submitting ? text.submittingLabel : text.submitLabel}
                </button>
            </div>
        </form>
    );
};

export default CycleCountDrawerActions;
