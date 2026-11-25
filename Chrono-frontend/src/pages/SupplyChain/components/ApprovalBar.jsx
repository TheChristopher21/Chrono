import React from "react";

const ApprovalBar = ({ label, progress }) => (
    <div className="sc-approval">
        <div className="sc-approval-head">
            <span>{label}</span>
            <strong>{Math.round(progress)}%</strong>
        </div>
        <div className="sc-approval-track" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={progress}>
            <span style={{ width: `${progress}%` }} />
        </div>
    </div>
);

export default ApprovalBar;
