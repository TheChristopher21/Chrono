import React, { useEffect, useMemo, useState } from "react";
import Navbar from "../../components/Navbar.jsx";
import api from "../../utils/api.js";
import { useNotification } from "../../context/NotificationContext.jsx";
import { useTranslation } from "../../context/LanguageContext.jsx";
import "../../styles/SupplyChainDashboardScoped.css";

const defaultWorkflowTemplate = JSON.stringify(
    {
        workflowId: "inbound-qc-putaway",
        label: "Inbound QC & Putaway",
        description: "Scan Wareneingänge, führe Qualitätskontrolle durch und lagere chargenpflichtige Artikel ein.",
        roles: ["RECEIVING", "QC"],
        steps: [
            {
                id: "scan-po",
                type: "scan",
                prompt: "Purchase Order scannen",
                binding: { field: "purchaseOrderNumber" },
            },
            {
                id: "scan-item",
                type: "scan",
                prompt: "Artikel-Barcode scannen",
                binding: { field: "productSku" },
                requires: ["scan-po"],
            },
            {
                id: "qc-check",
                type: "checklist",
                prompt: "QC-Prüfung durchführen",
                checklist: [
                    { id: "packaging", label: "Verpackung unbeschädigt?", type: "boolean" },
                    { id: "quantity", label: "Menge bestätigt?", type: "number", binding: { field: "receivedQuantity" } },
                    { id: "expirationDate", label: "MHD (YYYY-MM-DD)", type: "date" },
                ],
                requires: ["scan-item"],
            },
            {
                id: "photo",
                type: "photo",
                prompt: "Beweisfoto aufnehmen",
                optional: true,
                requires: ["qc-check"],
            },
            {
                id: "adjust-stock",
                type: "stock-adjustment",
                prompt: "Bestandsbuchung durchführen",
                defaults: {
                    movementType: "RECEIPT",
                },
                bindings: {
                    productId: "scan-item",
                    quantityChange: "qc-check.quantity",
                    lotNumber: "scan-item",
                    expirationDate: "qc-check.expirationDate",
                },
                endpoint: "/api/supply-chain/stock/adjust",
                requires: ["qc-check"],
            },
        ],
        completion: {
            type: "api-call",
            endpoint: "/api/mobile/workflows/complete",
            payload: {
                workflowId: "inbound-qc-putaway",
                source: "mobile-app",
            },
        },
    },
    null,
    2
);

const SupplyChainDashboard = () => {
    const { notify } = useNotification();
    const { t } = useTranslation();
    const [products, setProducts] = useState([]);
    const [warehouses, setWarehouses] = useState([]);
    const [stock, setStock] = useState([]);
    const [productionOrders, setProductionOrders] = useState([]);
    const [serviceRequests, setServiceRequests] = useState([]);
    const [loading, setLoading] = useState(true);
    const [refreshFlag, setRefreshFlag] = useState(0);

    const [productForm, setProductForm] = useState({ sku: "", name: "", description: "", unitCost: "", unitPrice: "" });
    const [warehouseForm, setWarehouseForm] = useState({ name: "", location: "" });
    const [adjustForm, setAdjustForm] = useState({
        productId: "",
        warehouseId: "",
        quantity: "",
        type: "ADJUSTMENT",
        reference: "",
        lotNumber: "",
        serialNumber: "",
        expirationDate: "",
    });
    const [purchaseForm, setPurchaseForm] = useState({ orderNumber: "", vendorName: "", expectedDate: "", warehouseId: "" });
    const [purchaseLines, setPurchaseLines] = useState([{ productId: "", quantity: "", unitCost: "" }]);
    const [receiveForm, setReceiveForm] = useState({ orderId: "", warehouseId: "" });
    const [salesForm, setSalesForm] = useState({ orderNumber: "", customerName: "", dueDate: "", warehouseId: "" });
    const [salesLines, setSalesLines] = useState([{ productId: "", quantity: "", unitPrice: "" }]);
    const [fulfillForm, setFulfillForm] = useState({ orderId: "", warehouseId: "" });
    const [productionOrderForm, setProductionOrderForm] = useState({ orderNumber: "", productId: "", quantity: "", status: "PLANNED", startDate: "", completionDate: "" });
    const [productionStatusForm, setProductionStatusForm] = useState({ orderId: "", status: "IN_PROGRESS", startDate: "", completionDate: "" });
    const [serviceRequestForm, setServiceRequestForm] = useState({ customerName: "", subject: "", description: "" });
    const [serviceStatusForm, setServiceStatusForm] = useState({ requestId: "", status: "IN_PROGRESS", closedDate: "" });
    const [workflowSchema, setWorkflowSchema] = useState(defaultWorkflowTemplate);

    const productionStatusOptions = useMemo(() => [
        { value: "PLANNED", label: t("supplyChain.productionStatus.planned", "Geplant") },
        { value: "IN_PROGRESS", label: t("supplyChain.productionStatus.inProgress", "In Bearbeitung") },
        { value: "COMPLETED", label: t("supplyChain.productionStatus.completed", "Abgeschlossen") },
        { value: "CANCELLED", label: t("supplyChain.productionStatus.cancelled", "Storniert") },
    ], [t]);

    const serviceStatusOptions = useMemo(() => [
        { value: "OPEN", label: t("supplyChain.serviceStatus.open", "Offen") },
        { value: "IN_PROGRESS", label: t("supplyChain.serviceStatus.inProgress", "In Bearbeitung") },
        { value: "RESOLVED", label: t("supplyChain.serviceStatus.resolved", "Gelöst") },
        { value: "CLOSED", label: t("supplyChain.serviceStatus.closed", "Geschlossen") },
    ], [t]);

    const workflowPreview = useMemo(() => {
        try {
            return { data: JSON.parse(workflowSchema), error: null };
        } catch (error) {
            return { data: null, error: error instanceof Error ? error.message : String(error) };
        }
    }, [workflowSchema]);

    useEffect(() => {
        let isMounted = true;
        const load = async () => {
            setLoading(true);
            try {
                const [productRes, warehouseRes, stockRes, productionRes, serviceRes] = await Promise.all([
                    api.get("/api/supply-chain/products"),
                    api.get("/api/supply-chain/warehouses"),
                    api.get("/api/supply-chain/stock"),
                    api.get("/api/supply-chain/production-orders"),
                    api.get("/api/supply-chain/service-requests"),
                ]);

                if (!isMounted) {
                    return;
                }

                const productData = productRes?.data;
                const stockData = stockRes?.data;
                const productionData = productionRes?.data;
                const serviceData = serviceRes?.data;

                setProducts(Array.isArray(productData) ? productData : productData?.content ?? []);
                setWarehouses(warehouseRes?.data ?? []);
                setStock(Array.isArray(stockData) ? stockData : stockData?.content ?? []);
                setProductionOrders(Array.isArray(productionData) ? productionData : productionData?.content ?? []);
                setServiceRequests(Array.isArray(serviceData) ? serviceData : serviceData?.content ?? []);
            } catch (error) {
                console.error("Failed to load supply chain data", error);
                if (isMounted) {
                    notify(t("supplyChain.loadError", "Supply-Chain-Daten konnten nicht geladen werden."), "error");
                }
            } finally {
                if (isMounted) {
                    setLoading(false);
                }
            }
        };

        load();
        return () => {
            isMounted = false;
        };
    }, [notify, t, refreshFlag]);

    const totalInventoryValue = useMemo(() => {
        return stock.reduce((sum, entry) => {
            const product = products.find((p) => p.id === entry.product?.id || p.id === entry.productId);
            const cost = product?.unitCost ?? 0;
            return sum + (entry.quantity ?? 0) * cost;
        }, 0);
    }, [stock, products]);

    const activeProductionOrders = useMemo(() => {
        return productionOrders.filter((order) => order.status !== "COMPLETED" && order.status !== "CANCELLED").length;
    }, [productionOrders]);

    const openServiceTickets = useMemo(() => {
        return serviceRequests.filter((request) => request.status !== "CLOSED").length;
    }, [serviceRequests]);

    const handleProductSubmit = async (event) => {
        event.preventDefault();
        try {
            const payload = {
                sku: productForm.sku || undefined,
                name: productForm.name,
                description: productForm.description,
                unitCost: productForm.unitCost ? Number(productForm.unitCost) : undefined,
                unitPrice: productForm.unitPrice ? Number(productForm.unitPrice) : undefined,
            };
            await api.post("/api/supply-chain/products", payload);
            notify(t("supplyChain.productCreated", "Produkt angelegt."), "success");
            setProductForm({ sku: "", name: "", description: "", unitCost: "", unitPrice: "" });
            setLoading(true);
            setRefreshFlag((value) => value + 1);
        } catch (error) {
            console.error("Failed to create product", error);
            notify(t("supplyChain.productCreateFailed", "Produkt konnte nicht angelegt werden."), "error");
        }
    };

    const handleWarehouseSubmit = async (event) => {
        event.preventDefault();
        try {
            await api.post("/api/supply-chain/warehouses", warehouseForm);
            notify(t("supplyChain.warehouseCreated", "Lager angelegt."), "success");
            setWarehouseForm({ name: "", location: "" });
            setLoading(true);
            setRefreshFlag((value) => value + 1);
        } catch (error) {
            console.error("Failed to create warehouse", error);
            notify(t("supplyChain.warehouseCreateFailed", "Lager konnte nicht angelegt werden."), "error");
        }
    };

    const handleAdjustmentSubmit = async (event) => {
        event.preventDefault();
        try {
            const payload = {
                productId: Number(adjustForm.productId),
                warehouseId: Number(adjustForm.warehouseId),
                quantityChange: Number(adjustForm.quantity),
                type: adjustForm.type,
                reference: adjustForm.reference || undefined,
            };
            if (adjustForm.lotNumber) {
                payload.lotNumber = adjustForm.lotNumber;
            }
            if (adjustForm.serialNumber) {
                payload.serialNumber = adjustForm.serialNumber;
            }
            if (adjustForm.expirationDate) {
                payload.expirationDate = adjustForm.expirationDate;
            }
            await api.post("/api/supply-chain/stock/adjust", payload);
            notify(t("supplyChain.adjustmentSaved", "Bestandskorrektur gebucht."), "success");
            setAdjustForm({
                productId: "",
                warehouseId: "",
                quantity: "",
                type: "ADJUSTMENT",
                reference: "",
                lotNumber: "",
                serialNumber: "",
                expirationDate: "",
            });
            setLoading(true);
            setRefreshFlag((value) => value + 1);
        } catch (error) {
            console.error("Failed to adjust stock", error);
            notify(t("supplyChain.adjustmentFailed", "Bestandskorrektur fehlgeschlagen."), "error");
        }
    };

    const addPurchaseLine = () => setPurchaseLines((lines) => [...lines, { productId: "", quantity: "", unitCost: "" }]);
    const updatePurchaseLine = (index, field, value) => {
        setPurchaseLines((lines) => lines.map((line, idx) => (idx === index ? { ...line, [field]: value } : line)));
    };
    const removePurchaseLine = (index) => setPurchaseLines((lines) => lines.filter((_, idx) => idx !== index));

    const handlePurchaseSubmit = async (event) => {
        event.preventDefault();
        try {
            const payload = {
                orderNumber: purchaseForm.orderNumber || undefined,
                vendorName: purchaseForm.vendorName,
                expectedDate: purchaseForm.expectedDate || undefined,
                lines: purchaseLines
                    .filter((line) => line.productId && line.quantity)
                    .map((line) => ({
                        productId: Number(line.productId),
                        quantity: Number(line.quantity),
                        unitCost: Number(line.unitCost || 0),
                    })),
            };
            const response = await api.post("/api/supply-chain/purchase-orders", payload);
            notify(t("supplyChain.purchaseCreated", "Einkaufsbestellung erfasst."), "success");
            setPurchaseForm({ orderNumber: "", vendorName: "", expectedDate: "", warehouseId: "" });
            setPurchaseLines([{ productId: "", quantity: "", unitCost: "" }]);
            if (purchaseForm.warehouseId) {
                setReceiveForm({ orderId: response.data?.id ?? "", warehouseId: purchaseForm.warehouseId });
            }
        } catch (error) {
            console.error("Failed to create purchase order", error);
            notify(t("supplyChain.purchaseCreateFailed", "Einkaufsbestellung konnte nicht erstellt werden."), "error");
        }
    };

    const handleReceiveSubmit = async (event) => {
        event.preventDefault();
        try {
            const payload = {
                warehouseId: Number(receiveForm.warehouseId),
            };
            await api.post(`/api/supply-chain/purchase-orders/${receiveForm.orderId}/receive`, payload);
            notify(t("supplyChain.receiptBooked", "Wareneingang gebucht."), "success");
            setReceiveForm({ orderId: "", warehouseId: "" });
            setLoading(true);
            setRefreshFlag((value) => value + 1);
        } catch (error) {
            console.error("Failed to receive purchase order", error);
            notify(t("supplyChain.receiptFailed", "Wareneingang konnte nicht gebucht werden."), "error");
        }
    };

    const addSalesLine = () => setSalesLines((lines) => [...lines, { productId: "", quantity: "", unitPrice: "" }]);
    const updateSalesLine = (index, field, value) => {
        setSalesLines((lines) => lines.map((line, idx) => (idx === index ? { ...line, [field]: value } : line)));
    };
    const removeSalesLine = (index) => setSalesLines((lines) => lines.filter((_, idx) => idx !== index));

    const handleSalesSubmit = async (event) => {
        event.preventDefault();
        try {
            const payload = {
                orderNumber: salesForm.orderNumber || undefined,
                customerName: salesForm.customerName,
                dueDate: salesForm.dueDate || undefined,
                lines: salesLines
                    .filter((line) => line.productId && line.quantity)
                    .map((line) => ({
                        productId: Number(line.productId),
                        quantity: Number(line.quantity),
                        unitPrice: Number(line.unitPrice || 0),
                    })),
            };
            const response = await api.post("/api/supply-chain/sales-orders", payload);
            notify(t("supplyChain.salesCreated", "Verkaufsauftrag erfasst."), "success");
            setSalesForm({ orderNumber: "", customerName: "", dueDate: "", warehouseId: "" });
            setSalesLines([{ productId: "", quantity: "", unitPrice: "" }]);
            if (salesForm.warehouseId) {
                setFulfillForm({ orderId: response.data?.id ?? "", warehouseId: salesForm.warehouseId });
            }
        } catch (error) {
            console.error("Failed to create sales order", error);
            notify(t("supplyChain.salesCreateFailed", "Verkaufsauftrag konnte nicht erstellt werden."), "error");
        }
    };

    const handleFulfillSubmit = async (event) => {
        event.preventDefault();
        try {
            const payload = { warehouseId: Number(fulfillForm.warehouseId) };
            await api.post(`/api/supply-chain/sales-orders/${fulfillForm.orderId}/fulfill`, payload);
            notify(t("supplyChain.fulfillmentBooked", "Warenausgang gebucht."), "success");
            setFulfillForm({ orderId: "", warehouseId: "" });
            setLoading(true);
            setRefreshFlag((value) => value + 1);
        } catch (error) {
            console.error("Failed to fulfill sales order", error);
            notify(t("supplyChain.fulfillmentFailed", "Warenausgang konnte nicht gebucht werden."), "error");
        }
    };

    const handleProductionOrderSubmit = async (event) => {
        event.preventDefault();
        try {
            const payload = {
                orderNumber: productionOrderForm.orderNumber,
                productId: Number(productionOrderForm.productId),
                quantity: Number(productionOrderForm.quantity),
                status: productionOrderForm.status || undefined,
                startDate: productionOrderForm.startDate || undefined,
                completionDate: productionOrderForm.completionDate || undefined,
            };
            await api.post("/api/supply-chain/production-orders", payload);
            notify(t("supplyChain.productionOrderCreated", "Produktionsauftrag gespeichert."), "success");
            setProductionOrderForm({ orderNumber: "", productId: "", quantity: "", status: "PLANNED", startDate: "", completionDate: "" });
            setLoading(true);
            setRefreshFlag((value) => value + 1);
        } catch (error) {
            console.error("Failed to create production order", error);
            notify(t("supplyChain.productionOrderCreateFailed", "Produktionsauftrag konnte nicht gespeichert werden."), "error");
        }
    };

    const handleProductionStatusSubmit = async (event) => {
        event.preventDefault();
        try {
            const payload = {
                status: productionStatusForm.status,
                startDate: productionStatusForm.startDate || undefined,
                completionDate: productionStatusForm.completionDate || undefined,
            };
            await api.post(`/api/supply-chain/production-orders/${productionStatusForm.orderId}/status`, payload);
            notify(t("supplyChain.productionStatusUpdated", "Produktionsauftrag aktualisiert."), "success");
            setProductionStatusForm({ orderId: "", status: "IN_PROGRESS", startDate: "", completionDate: "" });
            setLoading(true);
            setRefreshFlag((value) => value + 1);
        } catch (error) {
            console.error("Failed to update production order", error);
            notify(t("supplyChain.productionStatusFailed", "Produktionsauftrag konnte nicht aktualisiert werden."), "error");
        }
    };

    const handleServiceRequestSubmit = async (event) => {
        event.preventDefault();
        try {
            const payload = {
                customerName: serviceRequestForm.customerName,
                subject: serviceRequestForm.subject,
                description: serviceRequestForm.description || undefined,
            };
            await api.post("/api/supply-chain/service-requests", payload);
            notify(t("supplyChain.serviceRequestLogged", "Serviceeinsatz erfasst."), "success");
            setServiceRequestForm({ customerName: "", subject: "", description: "" });
            setLoading(true);
            setRefreshFlag((value) => value + 1);
        } catch (error) {
            console.error("Failed to create service request", error);
            notify(t("supplyChain.serviceRequestFailed", "Serviceeinsatz konnte nicht erfasst werden."), "error");
        }
    };

    const handleServiceStatusSubmit = async (event) => {
        event.preventDefault();
        try {
            const payload = {
                status: serviceStatusForm.status,
                closedDate: serviceStatusForm.closedDate || undefined,
            };
            await api.post(`/api/supply-chain/service-requests/${serviceStatusForm.requestId}/status`, payload);
            notify(t("supplyChain.serviceStatusUpdated", "Serviceeinsatz aktualisiert."), "success");
            setServiceStatusForm({ requestId: "", status: "IN_PROGRESS", closedDate: "" });
            setLoading(true);
            setRefreshFlag((value) => value + 1);
        } catch (error) {
            console.error("Failed to update service request", error);
            notify(t("supplyChain.serviceStatusFailed", "Serviceeinsatz konnte nicht aktualisiert werden."), "error");
        }
    };

    const handleWorkflowReset = () => {
        setWorkflowSchema(defaultWorkflowTemplate);
        notify(t("supplyChain.workflowReset", "Workflow-Template zurückgesetzt."), "info");
    };

    const handleWorkflowCopy = async () => {
        try {
            if (navigator?.clipboard?.writeText) {
                await navigator.clipboard.writeText(workflowSchema);
                notify(t("supplyChain.workflowCopied", "Workflow-JSON kopiert."), "success");
            } else {
                throw new Error("Clipboard API unavailable");
            }
        } catch (error) {
            console.error("Failed to copy workflow JSON", error);
            notify(t("supplyChain.workflowCopyFailed", "Workflow konnte nicht kopiert werden."), "error");
        }
    };

    return (
        <div className="admin-page supply-chain-page">
            <Navbar />
            <main className="admin-content">
                <header className="admin-header">
                    <h1>{t("supplyChain.title", "Supply Chain")}</h1>
                    <p className="muted">{t("supplyChain.subtitle", "Materialstämme, Lagerbestände und Bewegungen")}</p>
                </header>

                <section className="card-grid">
                    <article className="card">
                        <h2>{t("supplyChain.products", "Produkte")}</h2>
                        <p className="metric">{products.length}</p>
                    </article>
                    <article className="card">
                        <h2>{t("supplyChain.warehouses", "Lager")}</h2>
                        <p className="metric">{warehouses.length}</p>
                    </article>
                    <article className="card">
                        <h2>{t("supplyChain.inventoryValue", "Bestandswert")}</h2>
                        <p className="metric">CHF {totalInventoryValue.toFixed(2)}</p>
                    </article>
                    <article className="card">
                        <h2>{t("supplyChain.activeProductionOrders", "Aktive Produktionsaufträge")}</h2>
                        <p className="metric">{activeProductionOrders}</p>
                    </article>
                    <article className="card">
                        <h2>{t("supplyChain.openServiceRequests", "Offene Serviceeinsätze")}</h2>
                        <p className="metric">{openServiceTickets}</p>
                    </article>
                </section>

                <section className="card">
                    <h2>{t("supplyChain.stockLevels", "Lagerbestände")}</h2>
                    {loading ? (
                        <p>{t("supplyChain.loading", "Lade...")}</p>
                    ) : (
                        <div className="table-wrapper">
                            <table>
                                <thead>
                                    <tr>
                                        <th>{t("supplyChain.product", "Produkt")}</th>
                                        <th>{t("supplyChain.warehouse", "Lager")}</th>
                                        <th>{t("supplyChain.quantity", "Menge")}</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {stock.map((entry) => {
                                        const product = products.find((p) => p.id === entry.product?.id || p.id === entry.productId);
                                        const warehouse = warehouses.find((w) => w.id === entry.warehouse?.id || w.id === entry.warehouseId);
                                        return (
                                            <tr key={entry.id}>
                                                <td>{product?.name ?? entry.product?.name}</td>
                                                <td>{warehouse?.name ?? entry.warehouse?.name}</td>
                                                <td>{Number(entry.quantity ?? 0).toFixed(2)}</td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>
                        </div>
                    )}
                </section>

                <section className="card">
                    <h2>{t("supplyChain.productionOrdersHeadline", "Produktionsaufträge")}</h2>
                    {loading ? (
                        <p>{t("supplyChain.loading", "Lade...")}</p>
                    ) : productionOrders.length === 0 ? (
                        <p>{t("supplyChain.noProductionOrders", "Noch keine Produktionsaufträge vorhanden.")}</p>
                    ) : (
                        <div className="table-wrapper">
                            <table>
                                <thead>
                                    <tr>
                                        <th>{t("supplyChain.orderNumber", "Bestellnummer")}</th>
                                        <th>{t("supplyChain.product", "Produkt")}</th>
                                        <th>{t("supplyChain.quantity", "Menge")}</th>
                                        <th>{t("supplyChain.status", "Status")}</th>
                                        <th>{t("supplyChain.startDate", "Startdatum")}</th>
                                        <th>{t("supplyChain.completionDate", "Fertigstellung")}</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {productionOrders.map((order) => (
                                        <tr key={order.id}>
                                            <td>{order.orderNumber ?? order.id}</td>
                                            <td>{order.productName ?? "-"}</td>
                                            <td>{Number(order.quantity ?? 0).toFixed(2)}</td>
                                            <td>{order.status}</td>
                                            <td>{order.startDate ?? "-"}</td>
                                            <td>{order.completionDate ?? "-"}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </section>

                <section className="card">
                    <h2>{t("supplyChain.serviceRequestsHeadline", "Serviceeinsätze")}</h2>
                    {loading ? (
                        <p>{t("supplyChain.loading", "Lade...")}</p>
                    ) : serviceRequests.length === 0 ? (
                        <p>{t("supplyChain.noServiceRequests", "Noch keine Serviceeinsätze erfasst.")}</p>
                    ) : (
                        <div className="table-wrapper">
                            <table>
                                <thead>
                                    <tr>
                                        <th>{t("supplyChain.id", "ID")}</th>
                                        <th>{t("supplyChain.subject", "Thema")}</th>
                                        <th>{t("supplyChain.customer", "Kunde")}</th>
                                        <th>{t("supplyChain.status", "Status")}</th>
                                        <th>{t("supplyChain.openedDate", "Eröffnet")}</th>
                                        <th>{t("supplyChain.closedDate", "Geschlossen")}</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {serviceRequests.map((request) => (
                                        <tr key={request.id}>
                                            <td>{request.id}</td>
                                            <td>{request.subject}</td>
                                            <td>{request.customerName}</td>
                                            <td>{request.status}</td>
                                            <td>{request.openedDate ?? "-"}</td>
                                            <td>{request.closedDate ?? "-"}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </section>

                <section className="card-grid">
                    <article className="card">
                        <h2>{t("supplyChain.newProduct", "Neues Produkt")}</h2>
                        <form className="form-grid" onSubmit={handleProductSubmit}>
                            <label>
                                SKU
                                <input type="text" value={productForm.sku} onChange={(e) => setProductForm({ ...productForm, sku: e.target.value })} />
                            </label>
                            <label>
                                {t("supplyChain.productName", "Name")}
                                <input type="text" value={productForm.name} onChange={(e) => setProductForm({ ...productForm, name: e.target.value })} required />
                            </label>
                            <label>
                                {t("supplyChain.description", "Beschreibung")}
                                <input type="text" value={productForm.description} onChange={(e) => setProductForm({ ...productForm, description: e.target.value })} />
                            </label>
                            <label>
                                {t("supplyChain.unitCost", "Einstandspreis")}
                                <input type="number" step="0.01" value={productForm.unitCost} onChange={(e) => setProductForm({ ...productForm, unitCost: e.target.value })} />
                            </label>
                            <label>
                                {t("supplyChain.unitPrice", "Verkaufspreis")}
                                <input type="number" step="0.01" value={productForm.unitPrice} onChange={(e) => setProductForm({ ...productForm, unitPrice: e.target.value })} />
                            </label>
                            <button type="submit" className="primary">{t("common.save", "Speichern")}</button>
                        </form>
                    </article>
                    <article className="card">
                        <h2>{t("supplyChain.newWarehouse", "Neues Lager")}</h2>
                        <form className="form-grid" onSubmit={handleWarehouseSubmit}>
                            <label>
                                {t("supplyChain.warehouseName", "Name")}
                                <input type="text" value={warehouseForm.name} onChange={(e) => setWarehouseForm({ ...warehouseForm, name: e.target.value })} required />
                            </label>
                            <label>
                                {t("supplyChain.location", "Standort")}
                                <input type="text" value={warehouseForm.location} onChange={(e) => setWarehouseForm({ ...warehouseForm, location: e.target.value })} />
                            </label>
                            <button type="submit" className="primary">{t("common.save", "Speichern")}</button>
                        </form>
                    </article>
                </section>

                <section className="card-grid">
                    <article className="card">
                        <h2>{t("supplyChain.inventoryAdjustment", "Bestandskorrektur")}</h2>
                        <form className="form-grid" onSubmit={handleAdjustmentSubmit}>
                            <label>
                                {t("supplyChain.product", "Produkt")}
                                <select value={adjustForm.productId} onChange={(e) => setAdjustForm({ ...adjustForm, productId: e.target.value })} required>
                                    <option value="">{t("supplyChain.chooseProduct", "Produkt wählen")}</option>
                                    {products.map((product) => (
                                        <option key={product.id} value={product.id}>{product.name}</option>
                                    ))}
                                </select>
                            </label>
                            <label>
                                {t("supplyChain.warehouse", "Lager")}
                                <select value={adjustForm.warehouseId} onChange={(e) => setAdjustForm({ ...adjustForm, warehouseId: e.target.value })} required>
                                    <option value="">{t("supplyChain.chooseWarehouse", "Lager wählen")}</option>
                                    {warehouses.map((warehouse) => (
                                        <option key={warehouse.id} value={warehouse.id}>{warehouse.name}</option>
                                    ))}
                                </select>
                            </label>
                            <label>
                                {t("supplyChain.quantity", "Menge")}
                                <input type="number" value={adjustForm.quantity} onChange={(e) => setAdjustForm({ ...adjustForm, quantity: e.target.value })} required />
                            </label>
                            <label>
                                {t("supplyChain.movementType", "Typ")}
                                <select value={adjustForm.type} onChange={(e) => setAdjustForm({ ...adjustForm, type: e.target.value })}>
                                    <option value="ADJUSTMENT">{t("supplyChain.adjustment", "Korrektur")}</option>
                                    <option value="WRITE_OFF">{t("supplyChain.writeOff", "Abschreibung")}</option>
                                    <option value="GAIN">{t("supplyChain.gain", "Bestandsmehrung")}</option>
                                </select>
                            </label>
                            <label>
                                {t("supplyChain.reference", "Referenz")}
                                <input type="text" value={adjustForm.reference} onChange={(e) => setAdjustForm({ ...adjustForm, reference: e.target.value })} />
                            </label>
                            <label>
                                {t("supplyChain.lotNumber", "Charge/Lot")}
                                <input type="text" value={adjustForm.lotNumber} onChange={(e) => setAdjustForm({ ...adjustForm, lotNumber: e.target.value })} />
                            </label>
                            <label>
                                {t("supplyChain.serialNumber", "Seriennummer")}
                                <input type="text" value={adjustForm.serialNumber} onChange={(e) => setAdjustForm({ ...adjustForm, serialNumber: e.target.value })} />
                            </label>
                            <label>
                                {t("supplyChain.expirationDate", "MHD")}
                                <input type="date" value={adjustForm.expirationDate} onChange={(e) => setAdjustForm({ ...adjustForm, expirationDate: e.target.value })} />
                            </label>
                            <p className="muted small-print">{t("supplyChain.traceabilityHint", "Diese Felder bleiben optional, ermöglichen aber FEFO/MEFO-gerechte Entnahmen in der Pick-Route.")}</p>
                            <button type="submit" className="primary">{t("supplyChain.postAdjustment", "Buchen")}</button>
                        </form>
                    </article>
                </section>

                <section className="card">
                    <h2>{t("supplyChain.purchaseOrders", "Einkauf")}</h2>
                    <div className="form-grid">
                        <form onSubmit={handlePurchaseSubmit} className="form-grid">
                            <label>
                                {t("supplyChain.orderNumber", "Bestellnummer")}
                                <input type="text" value={purchaseForm.orderNumber} onChange={(e) => setPurchaseForm({ ...purchaseForm, orderNumber: e.target.value })} />
                            </label>
                            <label>
                                {t("supplyChain.vendor", "Lieferant")}
                                <input type="text" value={purchaseForm.vendorName} onChange={(e) => setPurchaseForm({ ...purchaseForm, vendorName: e.target.value })} required />
                            </label>
                            <label>
                                {t("supplyChain.expectedDate", "Liefertermin")}
                                <input type="date" value={purchaseForm.expectedDate} onChange={(e) => setPurchaseForm({ ...purchaseForm, expectedDate: e.target.value })} />
                            </label>
                            <label>
                                {t("supplyChain.defaultWarehouse", "Standardlager für Eingang")}
                                <select value={purchaseForm.warehouseId} onChange={(e) => setPurchaseForm({ ...purchaseForm, warehouseId: e.target.value })}>
                                    <option value="">{t("supplyChain.chooseWarehouse", "Lager wählen")}</option>
                                    {warehouses.map((warehouse) => (
                                        <option key={warehouse.id} value={warehouse.id}>{warehouse.name}</option>
                                    ))}
                                </select>
                            </label>
                            <div className="po-lines">
                                {purchaseLines.map((line, index) => (
                                    <div key={index} className="po-line">
                                        <select value={line.productId} onChange={(e) => updatePurchaseLine(index, "productId", e.target.value)} required>
                                            <option value="">{t("supplyChain.product", "Produkt")}</option>
                                            {products.map((product) => (
                                                <option key={product.id} value={product.id}>{product.name}</option>
                                            ))}
                                        </select>
                                        <input type="number" step="0.01" placeholder={t("supplyChain.quantity", "Menge")} value={line.quantity} onChange={(e) => updatePurchaseLine(index, "quantity", e.target.value)} required />
                                        <input type="number" step="0.01" placeholder={t("supplyChain.unitCost", "Einstandspreis")} value={line.unitCost} onChange={(e) => updatePurchaseLine(index, "unitCost", e.target.value)} />
                                        {purchaseLines.length > 1 && (
                                            <button type="button" className="ghost" onClick={() => removePurchaseLine(index)}>{t("common.remove", "Entfernen")}</button>
                                        )}
                                    </div>
                                ))}
                                <button type="button" className="secondary" onClick={addPurchaseLine}>{t("supplyChain.addLine", "Zeile hinzufügen")}</button>
                            </div>
                            <button type="submit" className="primary">{t("supplyChain.createPurchase", "Bestellung anlegen")}</button>
                        </form>
                        <form onSubmit={handleReceiveSubmit} className="form-grid">
                            <label>
                                {t("supplyChain.purchaseOrderId", "Bestell-ID")}
                                <input type="number" value={receiveForm.orderId} onChange={(e) => setReceiveForm({ ...receiveForm, orderId: e.target.value })} required />
                            </label>
                            <label>
                                {t("supplyChain.receivingWarehouse", "Warenannahme Lager")}
                                <select value={receiveForm.warehouseId} onChange={(e) => setReceiveForm({ ...receiveForm, warehouseId: e.target.value })} required>
                                    <option value="">{t("supplyChain.chooseWarehouse", "Lager wählen")}</option>
                                    {warehouses.map((warehouse) => (
                                        <option key={warehouse.id} value={warehouse.id}>{warehouse.name}</option>
                                    ))}
                                </select>
                            </label>
                            <button type="submit" className="primary">{t("supplyChain.receiveGoods", "Wareneingang buchen")}</button>
                        </form>
                    </div>
                </section>

                <section className="card">
                    <h2>{t("supplyChain.salesOrders", "Verkauf")}</h2>
                    <div className="form-grid">
                        <form onSubmit={handleSalesSubmit} className="form-grid">
                            <label>
                                {t("supplyChain.orderNumber", "Bestellnummer")}
                                <input type="text" value={salesForm.orderNumber} onChange={(e) => setSalesForm({ ...salesForm, orderNumber: e.target.value })} />
                            </label>
                            <label>
                                {t("supplyChain.customer", "Kunde")}
                                <input type="text" value={salesForm.customerName} onChange={(e) => setSalesForm({ ...salesForm, customerName: e.target.value })} required />
                            </label>
                            <label>
                                {t("supplyChain.dueDate", "Fälligkeitsdatum")}
                                <input type="date" value={salesForm.dueDate} onChange={(e) => setSalesForm({ ...salesForm, dueDate: e.target.value })} />
                            </label>
                            <label>
                                {t("supplyChain.fulfillmentWarehouse", "Versandlager")}
                                <select value={salesForm.warehouseId} onChange={(e) => setSalesForm({ ...salesForm, warehouseId: e.target.value })}>
                                    <option value="">{t("supplyChain.chooseWarehouse", "Lager wählen")}</option>
                                    {warehouses.map((warehouse) => (
                                        <option key={warehouse.id} value={warehouse.id}>{warehouse.name}</option>
                                    ))}
                                </select>
                            </label>
                            <div className="po-lines">
                                {salesLines.map((line, index) => (
                                    <div key={index} className="po-line">
                                        <select value={line.productId} onChange={(e) => updateSalesLine(index, "productId", e.target.value)} required>
                                            <option value="">{t("supplyChain.product", "Produkt")}</option>
                                            {products.map((product) => (
                                                <option key={product.id} value={product.id}>{product.name}</option>
                                            ))}
                                        </select>
                                        <input type="number" step="0.01" placeholder={t("supplyChain.quantity", "Menge")} value={line.quantity} onChange={(e) => updateSalesLine(index, "quantity", e.target.value)} required />
                                        <input type="number" step="0.01" placeholder={t("supplyChain.unitPrice", "Verkaufspreis")} value={line.unitPrice} onChange={(e) => updateSalesLine(index, "unitPrice", e.target.value)} />
                                        {salesLines.length > 1 && (
                                            <button type="button" className="ghost" onClick={() => removeSalesLine(index)}>{t("common.remove", "Entfernen")}</button>
                                        )}
                                    </div>
                                ))}
                                <button type="button" className="secondary" onClick={addSalesLine}>{t("supplyChain.addLine", "Zeile hinzufügen")}</button>
                            </div>
                            <button type="submit" className="primary">{t("supplyChain.createSales", "Auftrag anlegen")}</button>
                        </form>
                        <form onSubmit={handleFulfillSubmit} className="form-grid">
                            <label>
                                {t("supplyChain.salesOrderId", "Auftrags-ID")}
                                <input type="number" value={fulfillForm.orderId} onChange={(e) => setFulfillForm({ ...fulfillForm, orderId: e.target.value })} required />
                            </label>
                            <label>
                                {t("supplyChain.shippingWarehouse", "Versandlager")}
                                <select value={fulfillForm.warehouseId} onChange={(e) => setFulfillForm({ ...fulfillForm, warehouseId: e.target.value })} required>
                                    <option value="">{t("supplyChain.chooseWarehouse", "Lager wählen")}</option>
                                    {warehouses.map((warehouse) => (
                                        <option key={warehouse.id} value={warehouse.id}>{warehouse.name}</option>
                                    ))}
                                </select>
                            </label>
                            <button type="submit" className="primary">{t("supplyChain.fulfillOrder", "Warenausgang buchen")}</button>
                        </form>
                    </div>
                </section>

                <section className="card-grid">
                    <article className="card">
                        <h2>{t("supplyChain.newProductionOrder", "Neuer Produktionsauftrag")}</h2>
                        <form className="form-grid" onSubmit={handleProductionOrderSubmit}>
                            <label>
                                {t("supplyChain.orderNumber", "Bestellnummer")}
                                <input type="text" value={productionOrderForm.orderNumber} onChange={(e) => setProductionOrderForm({ ...productionOrderForm, orderNumber: e.target.value })} required />
                            </label>
                            <label>
                                {t("supplyChain.product", "Produkt")}
                                <select value={productionOrderForm.productId} onChange={(e) => setProductionOrderForm({ ...productionOrderForm, productId: e.target.value })} required>
                                    <option value="">{t("supplyChain.chooseProduct", "Produkt wählen")}</option>
                                    {products.map((product) => (
                                        <option key={product.id} value={product.id}>{product.name}</option>
                                    ))}
                                </select>
                            </label>
                            <label>
                                {t("supplyChain.quantity", "Menge")}
                                <input type="number" step="0.01" value={productionOrderForm.quantity} onChange={(e) => setProductionOrderForm({ ...productionOrderForm, quantity: e.target.value })} required />
                            </label>
                            <label>
                                {t("supplyChain.status", "Status")}
                                <select value={productionOrderForm.status} onChange={(e) => setProductionOrderForm({ ...productionOrderForm, status: e.target.value })}>
                                    {productionStatusOptions.map((option) => (
                                        <option key={option.value} value={option.value}>{option.label}</option>
                                    ))}
                                </select>
                            </label>
                            <label>
                                {t("supplyChain.startDate", "Startdatum")}
                                <input type="date" value={productionOrderForm.startDate} onChange={(e) => setProductionOrderForm({ ...productionOrderForm, startDate: e.target.value })} />
                            </label>
                            <label>
                                {t("supplyChain.completionDate", "Fertigstellung")}
                                <input type="date" value={productionOrderForm.completionDate} onChange={(e) => setProductionOrderForm({ ...productionOrderForm, completionDate: e.target.value })} />
                            </label>
                            <button type="submit" className="primary">{t("supplyChain.saveProductionOrder", "Produktion speichern")}</button>
                        </form>
                    </article>
                    <article className="card">
                        <h2>{t("supplyChain.updateProductionOrder", "Produktionsauftrag aktualisieren")}</h2>
                        <form className="form-grid" onSubmit={handleProductionStatusSubmit}>
                            <label>
                                {t("supplyChain.productionOrderId", "Produktionsauftrag ID")}
                                <input type="number" value={productionStatusForm.orderId} onChange={(e) => setProductionStatusForm({ ...productionStatusForm, orderId: e.target.value })} required />
                            </label>
                            <label>
                                {t("supplyChain.status", "Status")}
                                <select value={productionStatusForm.status} onChange={(e) => setProductionStatusForm({ ...productionStatusForm, status: e.target.value })}>
                                    {productionStatusOptions.map((option) => (
                                        <option key={option.value} value={option.value}>{option.label}</option>
                                    ))}
                                </select>
                            </label>
                            <label>
                                {t("supplyChain.startDateOverride", "Startdatum (optional)")}
                                <input type="date" value={productionStatusForm.startDate} onChange={(e) => setProductionStatusForm({ ...productionStatusForm, startDate: e.target.value })} />
                            </label>
                            <label>
                                {t("supplyChain.completionDateOverride", "Fertigstellung (optional)")}
                                <input type="date" value={productionStatusForm.completionDate} onChange={(e) => setProductionStatusForm({ ...productionStatusForm, completionDate: e.target.value })} />
                            </label>
                            <button type="submit" className="primary">{t("supplyChain.update", "Aktualisieren")}</button>
                        </form>
                    </article>
                </section>

                <section className="card-grid">
                    <article className="card">
                        <h2>{t("supplyChain.newServiceRequest", "Neuer Serviceeinsatz")}</h2>
                        <form className="form-grid" onSubmit={handleServiceRequestSubmit}>
                            <label>
                                {t("supplyChain.customer", "Kunde")}
                                <input type="text" value={serviceRequestForm.customerName} onChange={(e) => setServiceRequestForm({ ...serviceRequestForm, customerName: e.target.value })} required />
                            </label>
                            <label>
                                {t("supplyChain.subject", "Thema")}
                                <input type="text" value={serviceRequestForm.subject} onChange={(e) => setServiceRequestForm({ ...serviceRequestForm, subject: e.target.value })} required />
                            </label>
                            <label>
                                {t("supplyChain.description", "Beschreibung")}
                                <textarea value={serviceRequestForm.description} onChange={(e) => setServiceRequestForm({ ...serviceRequestForm, description: e.target.value })} rows={3} />
                            </label>
                            <button type="submit" className="primary">{t("supplyChain.logServiceRequest", "Service erfassen")}</button>
                        </form>
                    </article>
                    <article className="card">
                        <h2>{t("supplyChain.updateServiceRequest", "Serviceeinsatz aktualisieren")}</h2>
                        <form className="form-grid" onSubmit={handleServiceStatusSubmit}>
                            <label>
                                {t("supplyChain.serviceRequestId", "Serviceeinsatz ID")}
                                <input type="number" value={serviceStatusForm.requestId} onChange={(e) => setServiceStatusForm({ ...serviceStatusForm, requestId: e.target.value })} required />
                            </label>
                            <label>
                                {t("supplyChain.status", "Status")}
                                <select value={serviceStatusForm.status} onChange={(e) => setServiceStatusForm({ ...serviceStatusForm, status: e.target.value })}>
                                    {serviceStatusOptions.map((option) => (
                                        <option key={option.value} value={option.value}>{option.label}</option>
                                    ))}
                                </select>
                            </label>
                            <label>
                                {t("supplyChain.closedDate", "Geschlossen")}
                                <input type="date" value={serviceStatusForm.closedDate} onChange={(e) => setServiceStatusForm({ ...serviceStatusForm, closedDate: e.target.value })} />
                            </label>
                            <button type="submit" className="primary">{t("supplyChain.update", "Aktualisieren")}</button>
                        </form>
                    </article>
                </section>

                <section className="card">
                    <h2>{t("supplyChain.mobileWorkflowDesigner", "Low-Code Mobile Workflow Designer")}</h2>
                    <p className="muted">{t("supplyChain.mobileWorkflowSubtitle", "Konfigurieren Sie mobile MDE-Prozesse ohne Deployment – einfach JSON anpassen und sofort ausrollen.")}</p>
                    <div className="workflow-designer">
                        <label>
                            {t("supplyChain.workflowJsonLabel", "Workflow-Definition (JSON)")}
                            <textarea value={workflowSchema} onChange={(event) => setWorkflowSchema(event.target.value)} spellCheck={false} />
                        </label>
                        <div className="workflow-preview">
                            <h3>{t("supplyChain.workflowPreview", "Vorschau")}</h3>
                            {workflowPreview.error ? (
                                <div className="workflow-preview-card">
                                    <strong>{t("supplyChain.workflowInvalid", "Ungültige Definition")}</strong>
                                    <p className="muted">{workflowPreview.error}</p>
                                </div>
                            ) : workflowPreview.data ? (
                                <>
                                    <div className="workflow-preview-card">
                                        <strong>{workflowPreview.data.label ?? workflowPreview.data.workflowId}</strong>
                                        <p className="muted">
                                            {(workflowPreview.data.steps?.length ?? 0)} {t("supplyChain.workflowStepsLabel", "Schritte")} · {t("supplyChain.workflowRolesLabel", "Rollen")}: {(workflowPreview.data.roles ?? []).length > 0
                                                ? (workflowPreview.data.roles ?? []).join(", ")
                                                : t("supplyChain.workflowRolesDefault", "Keine Rollen definiert")}
                                        </p>
                                    </div>
                                    {(workflowPreview.data.steps ?? []).map((step) => (
                                        <div key={step.id} className="workflow-preview-card">
                                            <strong>{step.prompt ?? step.id}</strong>
                                            <p className="muted">{t("supplyChain.workflowStepTypeLabel", "Typ")}: {step.type}</p>
                                            {step.requires && step.requires.length > 0 && (
                                                <p className="muted">{t("supplyChain.workflowStepDependsLabel", "Abhängigkeiten")}: {step.requires.join(", ")}</p>
                                            )}
                                        </div>
                                    ))}
                                </>
                            ) : (
                                <div className="workflow-preview-card">
                                    <p>{t("supplyChain.workflowEmpty", "Starten Sie mit dem Template oder fügen Sie Ihre eigene Struktur ein.")}</p>
                                </div>
                            )}
                        </div>
                    </div>
                    <div className="workflow-actions">
                        <button type="button" className="secondary" onClick={handleWorkflowReset}>{t("supplyChain.workflowResetCta", "Template wiederherstellen")}</button>
                        <button type="button" className="primary" onClick={handleWorkflowCopy}>{t("supplyChain.workflowCopyCta", "JSON kopieren")}</button>
                    </div>
                </section>
            </main>
        </div>
    );
};

export default SupplyChainDashboard;
