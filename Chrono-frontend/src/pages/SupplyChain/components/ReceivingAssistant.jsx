import React, { useCallback, useEffect, useRef, useState } from "react";

const BARCODE_FORMATS = [
    "qr_code",
    "code_128",
    "code_39",
    "ean_13",
    "ean_8",
    "itf",
    "upc_a",
    "upc_e",
];

const createDetector = () => {
    if (typeof BarcodeDetector === "undefined") {
        return null;
    }

    try {
        return new BarcodeDetector({ formats: BARCODE_FORMATS });
    } catch (error) {
        return new BarcodeDetector();
    }
};

const readImageSignals = async (file) => {
    const bitmap = await createImageBitmap(file);

    try {
        const detector = createDetector();
        const barcodeResults = detector ? await detector.detect(bitmap) : [];
        const detectedCodes = [...new Set(barcodeResults.map((entry) => entry.rawValue).filter(Boolean))];

        let documentText = "";
        if (typeof TextDetector !== "undefined") {
            const textDetector = new TextDetector();
            const textBlocks = await textDetector.detect(bitmap);
            documentText = textBlocks.map((block) => block.rawValue).filter(Boolean).join("\n").trim();
        }

        return { detectedCodes, documentText };
    } finally {
        if (typeof bitmap.close === "function") {
            bitmap.close();
        }
    }
};

const ReceivingAssistant = ({ text, warehouses, bins = [], onPreview, onPreviewDocument, onApply }) => {
    const [scanValue, setScanValue] = useState("");
    const [warehouseId, setWarehouseId] = useState("");
    const [reference, setReference] = useState("");
    const [preview, setPreview] = useState(null);
    const [editableItems, setEditableItems] = useState([]);
    const [completePurchaseOrder, setCompletePurchaseOrder] = useState(true);
    const [busy, setBusy] = useState(false);
    const [cameraOpen, setCameraOpen] = useState(false);
    const [cameraError, setCameraError] = useState("");
    const [documentStatus, setDocumentStatus] = useState("");
    const videoRef = useRef(null);

    const barcodeSupported = typeof BarcodeDetector !== "undefined";
    const textSupported = typeof TextDetector !== "undefined";
    const cameraSupported = barcodeSupported && typeof navigator !== "undefined" && Boolean(navigator.mediaDevices?.getUserMedia);
    const previewModeLabel = preview ? (text.preview.modeLabels?.[preview.mode] ?? preview.mode) : "";
    const localizedWarnings = preview?.warnings?.map((warning) => (
        text.preview.warningTranslations?.[warning] ?? warning
    )) ?? [];
    const previewMessage = preview
        ? (text.preview.modeDescriptions?.[preview.mode] ?? text.preview.messageTranslations?.[preview.message] ?? preview.message ?? text.preview.empty)
        : text.preview.empty;

    const hydratePreview = useCallback((result, fallbackReference = "") => {
        setPreview(result);
        setEditableItems((result?.items ?? []).map((item) => ({
            ...item,
            quantity: String(item.quantity ?? "1"),
            warehouseBinId: "",
            lotNumber: "",
            serialNumber: "",
            expirationDate: "",
            inventoryStatus: "AVAILABLE",
        })));
        setReference(result?.reference ?? fallbackReference ?? "");
        setCompletePurchaseOrder(Boolean(result?.canCompletePurchaseOrder));
    }, []);

    const runPreview = useCallback(async (payload, fallbackReference = "") => {
        setBusy(true);
        setCameraError("");
        try {
            const result = await onPreview(payload);
            hydratePreview(result, fallbackReference);
            return result;
        } finally {
            setBusy(false);
        }
    }, [hydratePreview, onPreview]);

    const submitScan = async (event) => {
        event.preventDefault();
        const trimmed = scanValue.trim();
        if (!trimmed) {
            return;
        }
        await runPreview({ scanValue: trimmed, detectedCodes: [trimmed] }, trimmed);
    };

    const handleDocumentUpload = async (event) => {
        const file = event.target.files?.[0];
        const inputElement = event.target;
        if (!file) {
            return;
        }

        setDocumentStatus(text.document.processing);
        setCameraError("");
        setBusy(true);

        try {
            let result;

            if (file.type.startsWith("image/")) {
                const { detectedCodes, documentText } = await readImageSignals(file);
                result = await onPreview({
                    fileName: file.name,
                    detectedCodes,
                    documentText,
                });
                if (!documentText && !detectedCodes.length) {
                    setDocumentStatus(text.document.noSignal);
                } else if (!documentText && !textSupported) {
                    setDocumentStatus(text.document.imageFallback);
                } else {
                    setDocumentStatus(text.document.success);
                }
            } else {
                result = await onPreviewDocument(file);
                setDocumentStatus(text.document.success);
            }

            hydratePreview(result, file.name);
        } catch (error) {
            setDocumentStatus(text.document.error);
        } finally {
            setBusy(false);
            inputElement.value = "";
        }
    };

    useEffect(() => {
        if (!cameraOpen || !cameraSupported || !videoRef.current) {
            return undefined;
        }

        let active = true;
        let animationFrame = null;
        let stream = null;
        const detector = createDetector();

        const cleanup = () => {
            if (animationFrame) {
                window.cancelAnimationFrame(animationFrame);
            }
            if (stream) {
                stream.getTracks().forEach((track) => track.stop());
            }
            if (videoRef.current) {
                videoRef.current.srcObject = null;
            }
        };

        const scanFrame = async () => {
            if (!active || !videoRef.current || !detector) {
                return;
            }

            try {
                if (videoRef.current.readyState >= 2) {
                    const codes = await detector.detect(videoRef.current);
                    const rawValue = codes.find((entry) => entry.rawValue)?.rawValue;
                    if (rawValue) {
                        setScanValue(rawValue);
                        setCameraOpen(false);
                        await runPreview({ scanValue: rawValue, detectedCodes: [rawValue], fileName: "camera-scan" }, rawValue);
                        return;
                    }
                }
                animationFrame = window.requestAnimationFrame(scanFrame);
            } catch (error) {
                setCameraError(text.device.cameraError);
                setCameraOpen(false);
            }
        };

        const startCamera = async () => {
            try {
                stream = await navigator.mediaDevices.getUserMedia({
                    video: {
                        facingMode: { ideal: "environment" },
                    },
                    audio: false,
                });

                if (!active || !videoRef.current) {
                    cleanup();
                    return;
                }

                videoRef.current.srcObject = stream;
                await videoRef.current.play();
                animationFrame = window.requestAnimationFrame(scanFrame);
            } catch (error) {
                setCameraError(text.device.cameraError);
                setCameraOpen(false);
            }
        };

        startCamera();

        return () => {
            active = false;
            cleanup();
        };
    }, [cameraOpen, cameraSupported, runPreview, text.device.cameraError]);

    const handleItemQuantityChange = (productId, quantity) => {
        setEditableItems((prev) => prev.map((item) => (
            item.productId === productId ? { ...item, quantity } : item
        )));
    };

    const handleItemChange = (productId, field, value) => {
        setEditableItems((prev) => prev.map((item) => (
            item.productId === productId ? { ...item, [field]: value } : item
        )));
    };

    const applyPreview = async () => {
        if (!preview?.ready || !warehouseId || busy) {
            return;
        }

        setBusy(true);
        try {
            const payload = {
                warehouseId: Number(warehouseId),
                reference: reference.trim(),
                purchaseOrderId: preview.purchaseOrderId ?? null,
                completePurchaseOrder,
                items: editableItems.map((item) => ({
                    productId: item.productId,
                    warehouseBinId: item.warehouseBinId ? Number(item.warehouseBinId) : null,
                    quantity: item.quantity === "" ? "0" : item.quantity,
                    lotNumber: item.lotNumber || null,
                    serialNumber: item.serialNumber || null,
                    expirationDate: item.expirationDate || null,
                    inventoryStatus: item.inventoryStatus,
                })),
            };

            await onApply(payload);
            setPreview(null);
            setEditableItems([]);
            setReference("");
            setScanValue("");
            setDocumentStatus("");
        } finally {
            setBusy(false);
        }
    };

    const resetAssistant = () => {
        setPreview(null);
        setEditableItems([]);
        setReference("");
        setScanValue("");
        setDocumentStatus("");
        setCameraError("");
        setCameraOpen(false);
        setCompletePurchaseOrder(true);
    };

    return (
        <section className="card sc-receiving-assistant">
            <div className="sc-receiving-head">
                <div>
                    <h3>{text.title}</h3>
                    <p className="muted">{text.subtitle}</p>
                </div>
                <div className="sc-receiving-support">
                    <span className={`sc-status-chip ${barcodeSupported ? "success" : "neutral"}`}>{barcodeSupported ? text.device.barcodeReady : text.device.barcodeMissing}</span>
                    <span className={`sc-status-chip ${textSupported ? "success" : "neutral"}`}>{textSupported ? text.document.ocrReady : text.document.ocrMissing}</span>
                </div>
            </div>

            <div className="sc-receiving-grid">
                <article className="sc-receiving-block">
                    <div className="sc-receiving-block-head">
                        <h4>{text.device.title}</h4>
                        <p className="muted">{text.device.subtitle}</p>
                    </div>
                    <form className="sc-receiving-form" onSubmit={submitScan}>
                        <label>
                            <span>{text.device.inputLabel}</span>
                            <input
                                type="text"
                                value={scanValue}
                                onChange={(event) => setScanValue(event.target.value)}
                                placeholder={text.device.placeholder}
                            />
                        </label>
                        <div className="panel-actions">
                            <button type="submit" disabled={busy || !scanValue.trim()}>
                                {busy ? text.actions.working : text.actions.scan}
                            </button>
                            <button
                                type="button"
                                className="secondary"
                                disabled={!cameraSupported}
                                onClick={() => {
                                    setCameraError("");
                                    setCameraOpen((prev) => !prev);
                                }}
                            >
                                {cameraOpen ? text.actions.stopCamera : text.actions.startCamera}
                            </button>
                        </div>
                    </form>
                    <p className="muted sc-receiving-inline-note">
                        {cameraSupported ? text.device.cameraHint : text.device.cameraUnsupported}
                    </p>
                    {cameraError ? <p className="sc-receiving-error">{cameraError}</p> : null}
                    {cameraOpen ? (
                        <div className="sc-receiving-camera">
                            <video ref={videoRef} muted playsInline />
                        </div>
                    ) : null}
                </article>

                <article className="sc-receiving-block">
                    <div className="sc-receiving-block-head">
                        <h4>{text.document.title}</h4>
                        <p className="muted">{text.document.subtitle}</p>
                    </div>
                    <label className="sc-receiving-upload">
                        <span>{text.document.choose}</span>
                        <input
                            type="file"
                            accept="image/*,.pdf,.txt,text/plain,application/pdf"
                            onChange={handleDocumentUpload}
                            disabled={busy}
                        />
                    </label>
                    <p className="muted sc-receiving-inline-note">{text.document.hint}</p>
                    {documentStatus ? <p className="sc-receiving-inline-note">{documentStatus}</p> : null}
                </article>
            </div>

            <article className="sc-receiving-preview">
                <div className="sc-receiving-block-head">
                    <h4>{text.preview.title}</h4>
                    <p className="muted">{previewMessage}</p>
                </div>

                {preview ? (
                    <>
                        <div className="sc-receiving-summary">
                            <label>
                                <span>{text.labels.warehouse}</span>
                                <select value={warehouseId} onChange={(event) => setWarehouseId(event.target.value)}>
                                    <option value="">{text.placeholders.warehouse}</option>
                                    {warehouses.map((warehouse) => (
                                        <option key={warehouse.id} value={warehouse.id}>
                                            {warehouse.name} · {warehouse.location ?? warehouse.code}
                                        </option>
                                    ))}
                                </select>
                            </label>
                            <label>
                                <span>{text.labels.reference}</span>
                                <input
                                    type="text"
                                    value={reference}
                                    onChange={(event) => setReference(event.target.value)}
                                    placeholder={text.placeholders.reference}
                                />
                            </label>
                        </div>

                        <div className="sc-receiving-keyvals">
                            <span><strong>{text.labels.mode}</strong>{previewModeLabel}</span>
                            {preview.purchaseOrderNumber ? <span><strong>{text.labels.purchaseOrder}</strong>{preview.purchaseOrderNumber}</span> : null}
                            {preview.vendorName ? <span><strong>{text.labels.vendor}</strong>{preview.vendorName}</span> : null}
                            {preview.documentDate ? <span><strong>{text.labels.documentDate}</strong>{preview.documentDate}</span> : null}
                            {preview.detectedCodes?.length ? <span><strong>{text.labels.codes}</strong>{preview.detectedCodes.join(", ")}</span> : null}
                        </div>

                        {preview.purchaseOrderId ? (
                            <label className="sc-receiving-checkbox">
                                <input
                                    type="checkbox"
                                    checked={completePurchaseOrder}
                                    onChange={(event) => setCompletePurchaseOrder(event.target.checked)}
                                />
                                <span>{text.preview.completePurchaseOrder}</span>
                            </label>
                        ) : null}
                        {preview.purchaseOrderId ? <p className="muted sc-receiving-inline-note">{text.preview.completePurchaseOrderHint}</p> : null}

                        {preview.items?.length ? (
                            <div className="sc-receiving-items">
                                <div className="sc-receiving-items-head">
                                    <span>{text.columns.sku}</span>
                                    <span>{text.columns.name}</span>
                                    <span>{text.columns.quantity}</span>
                                    <span>{text.columns.unit}</span>
                                    <span>{text.columns.bin}</span>
                                    <span>{text.columns.lot}</span>
                                    <span>{text.columns.expiration}</span>
                                    <span>{text.columns.status}</span>
                                </div>
                                {editableItems.map((item) => (
                                    <div key={item.productId} className="sc-receiving-item-row">
                                        <span>{item.sku}</span>
                                        <span>{item.productName}</span>
                                        <input
                                            type="number"
                                            min="0"
                                            step="0.01"
                                            value={item.quantity}
                                            onChange={(event) => handleItemQuantityChange(item.productId, event.target.value)}
                                        />
                                        <span>{item.unitOfMeasure ?? "-"}</span>
                                        <select value={item.warehouseBinId} onChange={(event) => handleItemChange(item.productId, "warehouseBinId", event.target.value)}>
                                            <option value="">{text.placeholders.bin}</option>
                                            {bins.filter((bin) => String(bin.warehouseId) === String(warehouseId)).map((bin) => (
                                                <option key={bin.id} value={bin.id}>{bin.code}</option>
                                            ))}
                                        </select>
                                        <input type="text" value={item.lotNumber} onChange={(event) => handleItemChange(item.productId, "lotNumber", event.target.value)} placeholder={text.placeholders.lot} />
                                        <input type="date" value={item.expirationDate} onChange={(event) => handleItemChange(item.productId, "expirationDate", event.target.value)} />
                                        <select value={item.inventoryStatus} onChange={(event) => handleItemChange(item.productId, "inventoryStatus", event.target.value)}>
                                            <option value="AVAILABLE">{text.statuses.available}</option>
                                            <option value="QUALITY_INSPECTION">{text.statuses.quality}</option>
                                            <option value="QUARANTINE">{text.statuses.quarantine}</option>
                                            <option value="BLOCKED">{text.statuses.blocked}</option>
                                        </select>
                                    </div>
                                ))}
                            </div>
                        ) : null}

                        {localizedWarnings.length ? (
                            <div className="sc-receiving-warnings">
                                <strong>{text.preview.warnings}</strong>
                                <ul>
                                    {localizedWarnings.map((warning) => (
                                        <li key={warning}>{warning}</li>
                                    ))}
                                </ul>
                            </div>
                        ) : null}

                        {preview.extractedTextSnippet ? (
                            <div className="sc-receiving-text-snippet">
                                <strong>{text.preview.extractedText}</strong>
                                <pre>{preview.extractedTextSnippet}</pre>
                            </div>
                        ) : null}

                        <div className="panel-actions">
                            <button type="button" disabled={busy || !preview.ready || !warehouseId} onClick={applyPreview}>
                                {busy ? text.actions.working : text.actions.apply}
                            </button>
                            <button type="button" className="secondary" onClick={resetAssistant}>
                                {text.actions.reset}
                            </button>
                        </div>
                    </>
                ) : null}
            </article>
        </section>
    );
};

export default ReceivingAssistant;
