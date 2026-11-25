import React, { useMemo, useState } from "react";

const emptyLine = () => ({ productId: "", quantity: "1", unitAmount: "0" });

const OrderComposer = ({ kind, products, text, onSubmit, onClose }) => {
    const [orderNumber, setOrderNumber] = useState("");
    const [partnerName, setPartnerName] = useState("");
    const [dueDate, setDueDate] = useState("");
    const [lines, setLines] = useState([emptyLine()]);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const total = useMemo(() => lines.reduce((sum, line) => sum
        + Number(line.quantity || 0) * Number(line.unitAmount || 0), 0), [lines]);

    const updateLine = (index, field, value) => setLines((current) => current.map((line, lineIndex) =>
        lineIndex === index ? { ...line, [field]: value } : line
    ));

    const submit = async (event) => {
        event.preventDefault();
        setBusy(true);
        setError("");
        try {
            const validLines = lines.filter((line) => line.productId && Number(line.quantity) > 0);
            if (!orderNumber.trim() || !partnerName.trim() || validLines.length === 0) {
                setError(text.required);
                return;
            }
            const ok = await onSubmit({
                orderNumber: orderNumber.trim(),
                partnerName: partnerName.trim(),
                dueDate: dueDate || null,
                lines: validLines.map((line) => ({
                    productId: Number(line.productId),
                    quantity: Number(line.quantity),
                    unitAmount: Number(line.unitAmount),
                })),
            });
            if (ok !== false) onClose();
        } catch (requestError) {
            setError(requestError?.response?.data?.message ?? text.failed);
        } finally {
            setBusy(false);
        }
    };

    return (
        <form className="sc-order-composer card" onSubmit={submit}>
            <div className="sc-quick-entry-head">
                <h3>{text.title}</h3>
                <p className="muted">{text.subtitle}</p>
            </div>
            <div className="sc-quick-entry-grid">
                <label><span>{text.orderNumber}</span><input value={orderNumber} onChange={(event) => setOrderNumber(event.target.value)} placeholder={text.orderNumberPlaceholder} required /></label>
                <label><span>{text.partner}</span><input value={partnerName} onChange={(event) => setPartnerName(event.target.value)} placeholder={text.partnerPlaceholder} required /></label>
                <label><span>{text.date}</span><input type="date" value={dueDate} onChange={(event) => setDueDate(event.target.value)} /></label>
            </div>
            <div className="sc-order-lines">
                <div className="sc-order-line sc-order-line-head">
                    <span>{text.product}</span><span>{text.quantity}</span><span>{text.unitAmount}</span><span />
                </div>
                {lines.map((line, index) => (
                    <div className="sc-order-line" key={`${index}-${line.productId}`}>
                        <select value={line.productId} onChange={(event) => updateLine(index, "productId", event.target.value)} required>
                            <option value="">{text.selectProduct}</option>
                            {products.map((product) => <option key={product.id} value={product.id}>{product.sku} · {product.name}</option>)}
                        </select>
                        <input type="number" min="0.0001" step="0.0001" value={line.quantity} onChange={(event) => updateLine(index, "quantity", event.target.value)} required />
                        <input type="number" min="0" step="0.01" value={line.unitAmount} onChange={(event) => updateLine(index, "unitAmount", event.target.value)} required />
                        <button type="button" className="secondary" disabled={lines.length === 1} onClick={() => setLines((current) => current.filter((_, lineIndex) => lineIndex !== index))}>×</button>
                    </div>
                ))}
                <button type="button" className="secondary" onClick={() => setLines((current) => [...current, emptyLine()])}>{text.addLine}</button>
            </div>
            <div className="sc-order-composer-footer">
                <strong>{text.total}: {total.toFixed(2)}</strong>
                {error ? <span className="sc-process-error">{error}</span> : null}
                <div className="panel-actions">
                    <button type="submit" disabled={busy}>{busy ? text.working : text.submit}</button>
                    <button type="button" className="secondary" onClick={onClose}>{text.close}</button>
                </div>
            </div>
        </form>
    );
};

export default OrderComposer;
