import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Navbar from "../../components/Navbar.jsx";
import api from "../../utils/api.js";
import { useNotification } from "../../context/NotificationContext.jsx";
import { useTranslation } from "../../context/LanguageContext.jsx";
import { Chart as ChartJS, BarElement, CategoryScale, LinearScale, Tooltip, Legend } from "chart.js";
import { Bar } from "react-chartjs-2";
import "../../styles/SupplyChainDashboardScoped.css";

ChartJS.register(BarElement, CategoryScale, LinearScale, Tooltip, Legend);

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
                    { id: "expirationDate", label: "MHD (Mindesthaltbarkeitsdatum, YYYY-MM-DD)", type: "date" },
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
    const isMountedRef = useRef(true);
    const productNameInputRef = useRef(null);
    const purchaseVendorInputRef = useRef(null);
    const productionOrderNumberInputRef = useRef(null);
    const serviceCustomerInputRef = useRef(null);
    const [products, setProducts] = useState([]);
    const [warehouses, setWarehouses] = useState([]);
    const [stock, setStock] = useState([]);
    const [productionOrders, setProductionOrders] = useState([]);
    const [serviceRequests, setServiceRequests] = useState([]);
    const [loading, setLoading] = useState(true);
    const [refreshFlag, setRefreshFlag] = useState(0);

    const [productForm, setProductForm] = useState({ sku: "", name: "", description: "", unitCost: "", unitPrice: "" });
    const [warehouseForm, setWarehouseForm] = useState({ code: "", name: "", location: "" });
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
    const [replenishmentPreview, setReplenishmentPreview] = useState({
        items: [],
        evaluatedProducts: 0,
        productsRequiringReplenishment: 0,
    });
    const [replenishmentLoading, setReplenishmentLoading] = useState(false);
    const [replenishmentError, setReplenishmentError] = useState(null);
    const [activeTab, setActiveTab] = useState("overview");
    const [showAdjustmentDetails, setShowAdjustmentDetails] = useState(false);

    useEffect(() => {
        isMountedRef.current = true;
        return () => {
            isMountedRef.current = false;
        };
    }, []);

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

    const handleQuickAction = useCallback((tab, focusRef) => {
        setActiveTab(tab);
        setTimeout(() => {
            focusRef?.current?.focus?.();
        }, 120);
    }, []);

    const handleOpenProcurement = useCallback(() => {
        handleQuickAction("procurement", purchaseVendorInputRef);
    }, [handleQuickAction, purchaseVendorInputRef]);

    const tabItems = useMemo(
        () => [
            { value: "overview", label: t("supplyChain.tab.overview", "Übersicht"), icon: "📊" },
            { value: "inventory", label: t("supplyChain.tab.inventory", "Lager"), icon: "📦" },
            { value: "procurement", label: t("supplyChain.tab.procurement", "Einkauf"), icon: "🛒" },
            { value: "production", label: t("supplyChain.tab.production", "Produktion"), icon: "🏭" },
            { value: "sales", label: t("supplyChain.tab.sales", "Verkauf"), icon: "🚚" },
            { value: "service", label: t("supplyChain.tab.service", "Service"), icon: "🛠️" },
            { value: "workflows", label: t("supplyChain.tab.workflows", "Workflows"), icon: "⚙️" },
        ],
        [t]
    );

    const quickActions = useMemo(
        () => [
            {
                id: "product",
                icon: "📦",
                label: t("supplyChain.quickAction.product", "+ Produkt"),
                tab: "inventory",
                ref: productNameInputRef,
            },
            {
                id: "purchase",
                icon: "🛒",
                label: t("supplyChain.quickAction.purchase", "+ Bestellung"),
                tab: "procurement",
                ref: purchaseVendorInputRef,
            },
            {
                id: "production",
                icon: "🏭",
                label: t("supplyChain.quickAction.production", "+ Produktion"),
                tab: "production",
                ref: productionOrderNumberInputRef,
            },
            {
                id: "service",
                icon: "🛠️",
                label: t("supplyChain.quickAction.service", "+ Serviceeinsatz"),
                tab: "service",
                ref: serviceCustomerInputRef,
            },
        ],
        [
            productNameInputRef,
            purchaseVendorInputRef,
            productionOrderNumberInputRef,
            serviceCustomerInputRef,
            t,
        ]
    );

    useEffect(() => {
        const map = {
            inventory: productNameInputRef,
            procurement: purchaseVendorInputRef,
            production: productionOrderNumberInputRef,
            service: serviceCustomerInputRef,
        };
        const targetRef = map[activeTab];
        if (targetRef?.current) {
            setTimeout(() => {
                targetRef.current?.focus?.();
            }, 150);
        }
    }, [activeTab, productNameInputRef, productionOrderNumberInputRef, purchaseVendorInputRef, serviceCustomerInputRef]);

    const lowStockProducts = useMemo(() => {
        if (!Array.isArray(stock) || stock.length === 0) {
            return [];
        }
        const merged = stock.map((entry) => {
            const product = products.find((p) => p.id === entry.product?.id || p.id === entry.productId);
            const warehouse = warehouses.find((w) => w.id === entry.warehouse?.id || w.id === entry.warehouseId);
            return {
                id: entry.id ?? `${entry.productId ?? "unknown"}-${entry.warehouseId ?? ""}`,
                name:
                    product?.name ??
                    entry.product?.name ??
                    t("supplyChain.unknownProduct", "Unbekanntes Produkt"),
                sku: product?.sku ?? entry.product?.sku ?? "-",
                quantity: Number(entry.quantity ?? 0),
                warehouse:
                    warehouse?.name ??
                    entry.warehouse?.name ??
                    t("supplyChain.unknownWarehouse", "Unbekanntes Lager"),
            };
        });
        return merged
            .sort((a, b) => a.quantity - b.quantity)
            .slice(0, 5);
    }, [products, stock, t, warehouses]);

    const productionHighlights = useMemo(() => {
        if (!Array.isArray(productionOrders) || productionOrders.length === 0) {
            return [];
        }
        return productionOrders
            .filter((order) => order.status !== "COMPLETED" && order.status !== "CANCELLED")
            .slice(0, 5)
            .map((order) => ({
                id: order.id,
                orderNumber: order.orderNumber ?? order.id,
                productName:
                    order.productName ??
                    order.product?.name ??
                    t("supplyChain.unknownProduct", "Unbekanntes Produkt"),
                quantity: Number(order.quantity ?? 0),
                status: order.status,
                startDate: order.startDate ?? "-",
                completionDate: order.completionDate ?? null,
            }));
    }, [productionOrders, t]);

    const upcomingDeliveries = useMemo(() => {
        const candidates = Array.isArray(replenishmentPreview?.items)
            ? replenishmentPreview.items
            : [];
        const mapped = candidates.map((item) => {
            const etaRaw =
                item.expectedDate ??
                item.expectedReceiptDate ??
                item.nextReceiptDate ??
                item.targetDate ??
                item.nextReplenishmentDate ??
                null;
            const etaTime = etaRaw ? Date.parse(etaRaw) : Number.NaN;
            return {
                id: `${item.productId ?? "product"}-${item.sku ?? "sku"}`,
                productName: item.productName ?? item.sku ?? t("supplyChain.unknownProduct", "Unbekanntes Produkt"),
                quantity: Number(item.recommendedQuantity ?? item.quantity ?? 0),
                eta: etaRaw,
                etaTime: Number.isFinite(etaTime) ? etaTime : null,
            };
        });
        const filtered = mapped.filter((entry) => entry.eta || entry.quantity > 0);
        filtered.sort((a, b) => {
            if (a.etaTime && b.etaTime) {
                return a.etaTime - b.etaTime;
            }
            if (a.etaTime) {
                return -1;
            }
            if (b.etaTime) {
                return 1;
            }
            return b.quantity - a.quantity;
        });
        return filtered.slice(0, 5);
    }, [replenishmentPreview?.items, t]);

    const replenishmentChartData = useMemo(() => {
        const dataset = Array.isArray(replenishmentPreview?.items) ? replenishmentPreview.items : [];
        const entries = dataset
            .filter((item) => {
                const value = Number(item?.recommendedQuantity ?? item?.quantity ?? 0);
                return Number.isFinite(value) && value > 0;
            })
            .slice(0, 6);

        if (entries.length === 0) {
            return null;
        }

        return {
            labels: entries.map((item) => item.productName ?? item.sku ?? t("supplyChain.unknownProduct", "Unbekanntes Produkt")),
            datasets: [
                {
                    label: t("supplyChain.replenishmentChartLabel", "Empfohlene Nachbestellung"),
                    data: entries.map((item) => Number(item.recommendedQuantity ?? item.quantity ?? 0)),
                    backgroundColor: "rgba(37, 99, 235, 0.75)",
                    borderRadius: 6,
                },
            ],
        };
    }, [replenishmentPreview?.items, t]);

    const replenishmentChartOptions = useMemo(
        () => ({
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    display: false,
                },
            },
            scales: {
                y: {
                    beginAtZero: true,
                    ticks: {
                        precision: 0,
                    },
                },
                x: {
                    ticks: {
                        maxRotation: 0,
                        minRotation: 0,
                    },
                },
            },
        }),
        []
    );

    const replenishmentProgress = useMemo(() => {
        if (replenishmentLoading) {
            return 35;
        }
        const total =
            Number.isFinite(replenishmentPreview?.evaluatedProducts) && replenishmentPreview.evaluatedProducts > 0
                ? replenishmentPreview.evaluatedProducts
                : Array.isArray(replenishmentPreview?.items)
                ? replenishmentPreview.items.length
                : 0;
        if (!total) {
            return 0;
        }
        const ratio = (replenishmentPreview?.productsRequiringReplenishment ?? 0) / total;
        return Math.max(0, Math.min(100, Math.round(ratio * 100)));
    }, [
        replenishmentLoading,
        replenishmentPreview?.evaluatedProducts,
        replenishmentPreview?.items,
        replenishmentPreview?.productsRequiringReplenishment,
    ]);

    const hasReplenishmentResults = (replenishmentPreview?.items ?? []).length > 0;

    const fetchReplenishmentPreview = useCallback(
        async (showSuccessToast = false) => {
            setReplenishmentLoading(true);
            setReplenishmentError(null);
            try {
                const response = await api.post("/api/supply-chain/procurement/replenishment-preview", {
                    planningHorizonDays: 28,
                    safetyDays: 7,
                    serviceLevelTarget: 0.95,
                });
                const data = response?.data ?? {};
                if (!isMountedRef.current) {
                    return;
                }
                const items = Array.isArray(data?.items) ? data.items : data?.items?.content ?? [];
                setReplenishmentPreview({
                    items,
                    evaluatedProducts: Number.isFinite(data?.evaluatedProducts) ? data.evaluatedProducts : 0,
                    productsRequiringReplenishment: Number.isFinite(data?.productsRequiringReplenishment)
                        ? data.productsRequiringReplenishment
                        : 0,
                });
                if (showSuccessToast) {
                    notify(t("supplyChain.replenishmentPreviewSuccess", "Replenishment-Analyse aktualisiert."), "success");
                }
            } catch (error) {
                console.error("Failed to load replenishment preview", error);
                if (!isMountedRef.current) {
                    return;
                }
                setReplenishmentError(error instanceof Error ? error : new Error(String(error)));
                setReplenishmentPreview((prev) => ({
                    items: Array.isArray(prev?.items) ? prev.items : [],
                    evaluatedProducts: Number.isFinite(prev?.evaluatedProducts) ? prev.evaluatedProducts : 0,
                    productsRequiringReplenishment: Number.isFinite(prev?.productsRequiringReplenishment)
                        ? prev.productsRequiringReplenishment
                        : 0,
                }));
                notify(
                    t(
                        "supplyChain.replenishmentPreviewFailed",
                        "KI (Künstliche Intelligenz)-Bedarfsanalyse konnte nicht geladen werden."
                    ),
                    "error"
                );
            } finally {
                if (!isMountedRef.current) {
                    return;
                }
                setReplenishmentLoading(false);
            }
        },
        [notify, t]
    );

    const handleReplenishmentRefresh = useCallback(() => {
        fetchReplenishmentPreview(true);
    }, [fetchReplenishmentPreview]);

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
                await fetchReplenishmentPreview(false);
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
    }, [fetchReplenishmentPreview, notify, t, refreshFlag]);

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
            const payload = {
                code: warehouseForm.code.trim(),
                name: warehouseForm.name.trim(),
                location: warehouseForm.location.trim() || undefined,
            };

            if (!payload.code || !payload.name) {
                notify(t("supplyChain.warehouseValidationError", "Code und Name sind erforderlich."), "error");
                return;
            }

            await api.post("/api/supply-chain/warehouses", payload);
            notify(t("supplyChain.warehouseCreated", "Lager angelegt."), "success");
            setWarehouseForm({ code: "", name: "", location: "" });
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
                notify(t("supplyChain.workflowCopied", "Workflow-JSON (JavaScript Object Notation) kopiert."), "success");
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
                <header className="admin-header hero-header">
                    <div className="header-text">
                        <h1>{t("supplyChain.title", "Supply Chain Command Center")}</h1>
                        <p className="muted">
                            {t("supplyChain.subtitle", "Behält Bestände, Aufträge und Services im Blick – fokussiert und ohne Scrollmarathon.")}
                        </p>
                    </div>
                    <div className="quick-actions">
                        {quickActions.map((action) => (
                            <button
                                key={action.id}
                                type="button"
                                className="quick-action"
                                onClick={() => handleQuickAction(action.tab, action.ref)}
                            >
                                <span className="quick-action-icon" aria-hidden="true">{action.icon}</span>
                                <span>{action.label}</span>
                            </button>
                        ))}
                    </div>
                </header>

                <section className="metric-grid card-grid">
                    {[
                        { id: "products", label: t("supplyChain.products", "Produkte"), value: products.length },
                        { id: "warehouses", label: t("supplyChain.warehouses", "Lager"), value: warehouses.length },
                        {
                            id: "inventoryValue",
                            label: t("supplyChain.inventoryValue", "Bestandswert"),
                            value: `CHF ${totalInventoryValue.toFixed(2)}`,
                        },
                        {
                            id: "activeProduction",
                            label: t("supplyChain.activeProductionOrders", "Aktive Produktionsaufträge"),
                            value: activeProductionOrders,
                        },
                        {
                            id: "serviceRequests",
                            label: t("supplyChain.openServiceRequests", "Offene Serviceeinsätze"),
                            value: openServiceTickets,
                        },
                    ].map((metric) => (
                        <article key={metric.id} className="card metric-card">
                            <span className="metric-label">{metric.label}</span>
                            <p className="metric">{metric.value}</p>
                        </article>
                    ))}
                </section>

                <section className="tabs-container">
                    <div className="tabs">
                        <div className="tabs-list">
                            {tabItems.map((tab) => (
                                <button
                                    key={tab.value}
                                    type="button"
                                    className={`tab-trigger${activeTab === tab.value ? " active" : ""}`}
                                    onClick={() => setActiveTab(tab.value)}
                                >
                                    <span aria-hidden="true">{tab.icon}</span>
                                    <span>{tab.label}</span>
                                </button>
                            ))}
                        </div>
                        <div className="tabs-content">
                            {activeTab === "overview" && (
                                <div className="tab-panel">
                                    <div className="panel-grid">
                                        <article className="card insight-card">
                                            <div className="panel-header">
                                                <div>
                                                    <h2>{t("supplyChain.replenishmentInsightsHeadline", "KI (Künstliche Intelligenz)-Bedarfsanalyse")}</h2>
                                                    <p className="muted">
                                                        {t(
                                                            "supplyChain.replenishmentInsightsSummary",
                                                            "{{count}} von {{total}} Produkten benötigen Nachbestellung.",
                                                            {
                                                                count: replenishmentPreview.productsRequiringReplenishment,
                                                                total: replenishmentPreview.evaluatedProducts,
                                                            }
                                                        )}
                                                    </p>
                                                </div>
                                                <div className="panel-actions">
                                                    <button
                                                        type="button"
                                                        className="secondary"
                                                        onClick={handleReplenishmentRefresh}
                                                        disabled={replenishmentLoading}
                                                    >
                                                        {replenishmentLoading
                                                            ? t("supplyChain.replenishmentInsightsRefreshing", "Analysiere...")
                                                            : t("supplyChain.replenishmentInsightsRefresh", "Analyse aktualisieren")}
                                                    </button>
                                                    <button
                                                        type="button"
                                                        className="primary"
                                                        onClick={handleOpenProcurement}
                                                        disabled={replenishmentLoading}
                                                    >
                                                        {t("supplyChain.openProcurementCta", "Nachbestellen")}
                                                    </button>
                                                </div>
                                            </div>
                                            <div className="insight-body">
                                                <div className="progress-block">
                                                    <div className="progress-track">
                                                        <div className="progress-value" style={{ width: `${replenishmentProgress}%` }} />
                                                    </div>
                                                    <dl className="insight-stats">
                                                        <div>
                                                            <dt>{t("supplyChain.replenishmentAnalysed", "Analysierte Produkte")}</dt>
                                                            <dd>{replenishmentPreview.evaluatedProducts ?? 0}</dd>
                                                        </div>
                                                        <div>
                                                            <dt>{t("supplyChain.replenishmentDue", "Nachbestellungen")}</dt>
                                                            <dd>{replenishmentPreview.productsRequiringReplenishment ?? 0}</dd>
                                                        </div>
                                                    </dl>
                                                </div>
                                                {replenishmentLoading ? (
                                                    <p>{t("supplyChain.replenishmentInsightsLoading", "Analysiere Bestellbedarf...")}</p>
                                                ) : replenishmentError ? (
                                                    <p className="error-message">
                                                        {t("supplyChain.replenishmentInsightsError", "Die KI (Künstliche Intelligenz)-Bedarfsanalyse konnte nicht geladen werden.")}
                                                    </p>
                                                ) : !hasReplenishmentResults ? (
                                                    <p>
                                                        {t("supplyChain.replenishmentInsightsEmpty", "Aktuell sind keine automatischen Nachbestellungen erforderlich.")}
                                                    </p>
                                                ) : (
                                                    <>
                                                        {replenishmentChartData && (
                                                            <div className="insight-chart">
                                                                <Bar data={replenishmentChartData} options={replenishmentChartOptions} />
                                                            </div>
                                                        )}
                                                        <ul className="insight-list">
                                                            {replenishmentPreview.items.slice(0, 4).map((item) => {
                                                                const riskClass = item.stockOutRisk
                                                                    ? "danger"
                                                                    : item.overstockRisk
                                                                    ? "warn"
                                                                    : "safe";
                                                                const riskLabel = item.stockOutRisk
                                                                    ? t("supplyChain.stockOutRisk", "Stockout-Risiko")
                                                                    : item.overstockRisk
                                                                    ? t("supplyChain.overstockRisk", "Überbestand-Risiko")
                                                                    : t("supplyChain.stableStock", "Bestand stabil");
                                                                const recommendedQuantity = Number(item?.recommendedQuantity ?? 0);
                                                                const formattedQuantity = Number.isFinite(recommendedQuantity)
                                                                    ? recommendedQuantity.toLocaleString()
                                                                    : "-";
                                                                return (
                                                                    <li key={`${item.productId ?? ""}-${item.sku ?? "no-sku"}`}>
                                                                        <div>
                                                                            <strong>{item.productName}</strong>
                                                                            <span className="muted">SKU (Stock Keeping Unit): {item.sku ?? "-"}</span>
                                                                        </div>
                                                                        <div className="insight-meta">
                                                                            <span className={`status-chip ${riskClass}`}>{riskLabel}</span>
                                                                            <span>{t("supplyChain.recommendedQuantity", "Empf. Menge")}: {formattedQuantity}</span>
                                                                        </div>
                                                                    </li>
                                                                );
                                                            })}
                                                        </ul>
                                                    </>
                                                )}
                                            </div>
                                        </article>
                                        <article className="card table-card">
                                            <div className="panel-header">
                                                <h2>{t("supplyChain.lowStockHeadline", "Top 5 mit niedrigstem Bestand")}</h2>
                                            </div>
                                            {lowStockProducts.length === 0 ? (
                                                <p className="muted">{t("supplyChain.lowStockEmpty", "Keine Bestände vorhanden.")}</p>
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
                                                            {lowStockProducts.map((item) => (
                                                                <tr key={item.id}>
                                                                    <td>{item.name}</td>
                                                                    <td>{item.warehouse}</td>
                                                                    <td>{item.quantity.toLocaleString()}</td>
                                                                </tr>
                                                            ))}
                                                        </tbody>
                                                    </table>
                                                </div>
                                            )}
                                        </article>
                                        <article className="card table-card">
                                            <div className="panel-header">
                                                <h2>{t("supplyChain.productionOverviewHeadline", "Aktive Produktionsaufträge")}</h2>
                                            </div>
                                            {productionHighlights.length === 0 ? (
                                                <p className="muted">{t("supplyChain.noProductionOrders", "Noch keine Produktionsaufträge vorhanden.")}</p>
                                            ) : (
                                                <div className="table-wrapper">
                                                    <table>
                                                        <thead>
                                                            <tr>
                                                                <th>{t("supplyChain.orderNumber", "Bestellnummer")}</th>
                                                                <th>{t("supplyChain.product", "Produkt")}</th>
                                                                <th>{t("supplyChain.quantity", "Menge")}</th>
                                                                <th>{t("supplyChain.status", "Status")}</th>
                                                            </tr>
                                                        </thead>
                                                        <tbody>
                                                            {productionHighlights.map((order) => (
                                                                <tr key={order.id}>
                                                                    <td>{order.orderNumber}</td>
                                                                    <td>{order.productName}</td>
                                                                    <td>{order.quantity.toLocaleString()}</td>
                                                                    <td>{order.status}</td>
                                                                </tr>
                                                            ))}
                                                        </tbody>
                                                    </table>
                                                </div>
                                            )}
                                        </article>
                                        <article className="card table-card">
                                            <div className="panel-header">
                                                <h2>{t("supplyChain.upcomingDeliveriesHeadline", "Nächste Liefertermine")}</h2>
                                            </div>
                                            {upcomingDeliveries.length === 0 ? (
                                                <p className="muted">{t("supplyChain.upcomingDeliveriesEmpty", "Noch keine geplanten Lieferungen.")}</p>
                                            ) : (
                                                <div className="table-wrapper">
                                                    <table>
                                                        <thead>
                                                            <tr>
                                                                <th>{t("supplyChain.product", "Produkt")}</th>
                                                                <th>{t("supplyChain.recommendedQuantity", "Empf. Menge")}</th>
                                                                <th>{t("supplyChain.expectedDate", "Liefertermin")}</th>
                                                            </tr>
                                                        </thead>
                                                        <tbody>
                                                            {upcomingDeliveries.map((delivery) => (
                                                                <tr key={delivery.id}>
                                                                    <td>{delivery.productName}</td>
                                                                    <td>{Number.isFinite(delivery.quantity) ? delivery.quantity.toLocaleString() : "-"}</td>
                                                                    <td>{delivery.eta ?? t("supplyChain.dateTbd", "Termin offen")}</td>
                                                                </tr>
                                                            ))}
                                                        </tbody>
                                                    </table>
                                                </div>
                                            )}
                                        </article>
                                    </div>
                                </div>
                            )}
                            {activeTab === "inventory" && (
                                <div className="tab-panel">
                                    <div className="panel-grid two-column">
                                        <article className="card form-card">
                                            <div className="panel-header">
                                                <h2>{t("supplyChain.newProduct", "Neues Produkt")}</h2>
                                            </div>
                                            <form className="form-grid" onSubmit={handleProductSubmit}>
                                                <label>
                                                    SKU (Stock Keeping Unit)
                                                    <input
                                                        type="text"
                                                        value={productForm.sku}
                                                        onChange={(e) => setProductForm({ ...productForm, sku: e.target.value })}
                                                    />
                                                </label>
                                                <label>
                                                    {t("supplyChain.productName", "Name")}
                                                    <input
                                                        ref={productNameInputRef}
                                                        type="text"
                                                        value={productForm.name}
                                                        onChange={(e) => setProductForm({ ...productForm, name: e.target.value })}
                                                        required
                                                    />
                                                </label>
                                                <label>
                                                    {t("supplyChain.description", "Beschreibung")}
                                                    <input
                                                        type="text"
                                                        value={productForm.description}
                                                        onChange={(e) => setProductForm({ ...productForm, description: e.target.value })}
                                                    />
                                                </label>
                                                <label>
                                                    {t("supplyChain.unitCost", "Einstandspreis")}
                                                    <input
                                                        type="number"
                                                        step="0.01"
                                                        value={productForm.unitCost}
                                                        onChange={(e) => setProductForm({ ...productForm, unitCost: e.target.value })}
                                                    />
                                                </label>
                                                <label>
                                                    {t("supplyChain.unitPrice", "Verkaufspreis")}
                                                    <input
                                                        type="number"
                                                        step="0.01"
                                                        value={productForm.unitPrice}
                                                        onChange={(e) => setProductForm({ ...productForm, unitPrice: e.target.value })}
                                                    />
                                                </label>
                                                <button type="submit" className="primary">{t("common.save", "Speichern")}</button>
                                            </form>
                                        </article>
                                        <article className="card form-card">
                                            <div className="panel-header">
                                                <h2>{t("supplyChain.newWarehouse", "Neues Lager")}</h2>
                                            </div>
                                            <form className="form-grid" onSubmit={handleWarehouseSubmit}>
                                                <label>
                                                    {t("supplyChain.warehouseCode", "Code")}
                                                    <input
                                                        type="text"
                                                        value={warehouseForm.code}
                                                        onChange={(e) => setWarehouseForm({ ...warehouseForm, code: e.target.value })}
                                                        required
                                                    />
                                                </label>
                                                <label>
                                                    {t("supplyChain.warehouseName", "Name")}
                                                    <input
                                                        type="text"
                                                        value={warehouseForm.name}
                                                        onChange={(e) => setWarehouseForm({ ...warehouseForm, name: e.target.value })}
                                                        required
                                                    />
                                                </label>
                                                <label>
                                                    {t("supplyChain.location", "Standort")}
                                                    <input
                                                        type="text"
                                                        value={warehouseForm.location}
                                                        onChange={(e) => setWarehouseForm({ ...warehouseForm, location: e.target.value })}
                                                    />
                                                </label>
                                                <button type="submit" className="primary">{t("common.save", "Speichern")}</button>
                                            </form>
                                        </article>
                                    </div>
                                    <article className="card form-card">
                                        <div className="panel-header">
                                            <h2>{t("supplyChain.inventoryAdjustment", "Bestandskorrektur")}</h2>
                                        </div>
                                        <form className="form-grid" onSubmit={handleAdjustmentSubmit}>
                                            <label>
                                                {t("supplyChain.product", "Produkt")}
                                                <select
                                                    value={adjustForm.productId}
                                                    onChange={(e) => setAdjustForm({ ...adjustForm, productId: e.target.value })}
                                                    required
                                                >
                                                    <option value="">{t("supplyChain.chooseProduct", "Produkt wählen")}</option>
                                                    {products.map((product) => (
                                                        <option key={product.id} value={product.id}>
                                                            {product.name}
                                                        </option>
                                                    ))}
                                                </select>
                                            </label>
                                            <label>
                                                {t("supplyChain.warehouse", "Lager")}
                                                <select
                                                    value={adjustForm.warehouseId}
                                                    onChange={(e) => setAdjustForm({ ...adjustForm, warehouseId: e.target.value })}
                                                    required
                                                >
                                                    <option value="">{t("supplyChain.chooseWarehouse", "Lager wählen")}</option>
                                                    {warehouses.map((warehouse) => (
                                                        <option key={warehouse.id} value={warehouse.id}>
                                                            {warehouse.name}
                                                        </option>
                                                    ))}
                                                </select>
                                            </label>
                                            <label>
                                                {t("supplyChain.quantity", "Menge")}
                                                <input
                                                    type="number"
                                                    value={adjustForm.quantity}
                                                    onChange={(e) => setAdjustForm({ ...adjustForm, quantity: e.target.value })}
                                                    required
                                                />
                                            </label>
                                            <label>
                                                {t("supplyChain.movementType", "Typ")}
                                                <select
                                                    value={adjustForm.type}
                                                    onChange={(e) => setAdjustForm({ ...adjustForm, type: e.target.value })}
                                                >
                                                    <option value="ADJUSTMENT">{t("supplyChain.adjustment", "Korrektur")}</option>
                                                    <option value="WRITE_OFF">{t("supplyChain.writeOff", "Abschreibung")}</option>
                                                    <option value="GAIN">{t("supplyChain.gain", "Bestandsmehrung")}</option>
                                                </select>
                                            </label>
                                            <div className="optional-toggle-row">
                                                <button
                                                    type="button"
                                                    className="link-button"
                                                    onClick={() => setShowAdjustmentDetails((value) => !value)}
                                                >
                                                    {showAdjustmentDetails
                                                        ? t("supplyChain.hideOptionalFields", "Optionale Angaben ausblenden")
                                                        : t("supplyChain.showOptionalFields", "Optionale Angaben anzeigen")}
                                                </button>
                                                <span className="muted small-print">
                                                    {t(
                                                        "supplyChain.traceabilityHint",
                                                        "Optionale Angaben aktivieren FEFO (First Expired, First Out)/MEFO (Minimum Expiration, First Out)-gerechte Entnahmen."
                                                    )}
                                                </span>
                                            </div>
                                            <div className={`optional-fields${showAdjustmentDetails ? " open" : ""}`}>
                                                <label>
                                                    {t("supplyChain.reference", "Referenz")}
                                                    <input
                                                        type="text"
                                                        value={adjustForm.reference}
                                                        onChange={(e) => setAdjustForm({ ...adjustForm, reference: e.target.value })}
                                                    />
                                                </label>
                                                <label>
                                                    {t("supplyChain.lotNumber", "Charge/Lot")}
                                                    <input
                                                        type="text"
                                                        value={adjustForm.lotNumber}
                                                        onChange={(e) => setAdjustForm({ ...adjustForm, lotNumber: e.target.value })}
                                                    />
                                                </label>
                                                <label>
                                                    {t("supplyChain.serialNumber", "Seriennummer")}
                                                    <input
                                                        type="text"
                                                        value={adjustForm.serialNumber}
                                                        onChange={(e) => setAdjustForm({ ...adjustForm, serialNumber: e.target.value })}
                                                    />
                                                </label>
                                                <label>
                                                    {t("supplyChain.expirationDate", "MHD (Mindesthaltbarkeitsdatum)")}
                                                    <input
                                                        type="date"
                                                        value={adjustForm.expirationDate}
                                                        onChange={(e) => setAdjustForm({ ...adjustForm, expirationDate: e.target.value })}
                                                    />
                                                </label>
                                            </div>
                                            <button type="submit" className="primary">{t("supplyChain.postAdjustment", "Buchen")}</button>
                                        </form>
                                    </article>
                                    <article className="card table-card">
                                        <div className="panel-header">
                                            <h2>{t("supplyChain.stockLevels", "Lagerbestände")}</h2>
                                        </div>
                                        {loading ? (
                                            <p>{t("supplyChain.loading", "Lade...")}</p>
                                        ) : stock.length === 0 ? (
                                            <p className="muted">{t("supplyChain.noStockEntries", "Noch keine Bestände erfasst.")}</p>
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
                                    </article>
                                </div>
                            )}
                            {activeTab === "procurement" && (
                                <div className="tab-panel">
                                    <div className="panel-grid two-column">
                                        <article className="card form-card">
                                            <div className="panel-header">
                                                <h2>{t("supplyChain.purchaseOrders", "Einkauf")}</h2>
                                            </div>
                                            <form onSubmit={handlePurchaseSubmit} className="form-grid">
                                                <label>
                                                    {t("supplyChain.orderNumber", "Bestellnummer")}
                                                    <input
                                                        type="text"
                                                        value={purchaseForm.orderNumber}
                                                        onChange={(e) => setPurchaseForm({ ...purchaseForm, orderNumber: e.target.value })}
                                                    />
                                                </label>
                                                <label>
                                                    {t("supplyChain.vendor", "Lieferant")}
                                                    <input
                                                        ref={purchaseVendorInputRef}
                                                        type="text"
                                                        value={purchaseForm.vendorName}
                                                        onChange={(e) => setPurchaseForm({ ...purchaseForm, vendorName: e.target.value })}
                                                        required
                                                    />
                                                </label>
                                                <label>
                                                    {t("supplyChain.expectedDate", "Liefertermin")}
                                                    <input
                                                        type="date"
                                                        value={purchaseForm.expectedDate}
                                                        onChange={(e) => setPurchaseForm({ ...purchaseForm, expectedDate: e.target.value })}
                                                    />
                                                </label>
                                                <label>
                                                    {t("supplyChain.targetWarehouse", "Wareneingang in Lager")}
                                                    <select
                                                        value={purchaseForm.warehouseId}
                                                        onChange={(e) => setPurchaseForm({ ...purchaseForm, warehouseId: e.target.value })}
                                                    >
                                                        <option value="">{t("supplyChain.chooseWarehouse", "Lager wählen")}</option>
                                                        {warehouses.map((warehouse) => (
                                                            <option key={warehouse.id} value={warehouse.id}>
                                                                {warehouse.name}
                                                            </option>
                                                        ))}
                                                    </select>
                                                </label>
                                                <div className="po-lines">
                                                    <h3>{t("supplyChain.orderLines", "Bestellpositionen")}</h3>
                                                    {purchaseLines.map((line, index) => (
                                                        <div key={index} className="po-line">
                                                            <select
                                                                value={line.productId}
                                                                onChange={(e) => updatePurchaseLine(index, "productId", e.target.value)}
                                                                required
                                                            >
                                                                <option value="">{t("supplyChain.product", "Produkt")}</option>
                                                                {products.map((product) => (
                                                                    <option key={product.id} value={product.id}>
                                                                        {product.name}
                                                                    </option>
                                                                ))}
                                                            </select>
                                                            <input
                                                                type="number"
                                                                step="0.01"
                                                                placeholder={t("supplyChain.quantity", "Menge")}
                                                                value={line.quantity}
                                                                onChange={(e) => updatePurchaseLine(index, "quantity", e.target.value)}
                                                                required
                                                            />
                                                            <input
                                                                type="number"
                                                                step="0.01"
                                                                placeholder={t("supplyChain.unitCost", "Einstandspreis")}
                                                                value={line.unitCost}
                                                                onChange={(e) => updatePurchaseLine(index, "unitCost", e.target.value)}
                                                            />
                                                            {purchaseLines.length > 1 && (
                                                                <button type="button" className="ghost" onClick={() => removePurchaseLine(index)}>
                                                                    {t("common.remove", "Entfernen")}
                                                                </button>
                                                            )}
                                                        </div>
                                                    ))}
                                                    <button type="button" className="secondary" onClick={addPurchaseLine}>
                                                        {t("supplyChain.addLine", "Zeile hinzufügen")}
                                                    </button>
                                                </div>
                                                <button type="submit" className="primary">{t("supplyChain.createPurchase", "Bestellung anlegen")}</button>
                                            </form>
                                        </article>
                                        <article className="card form-card">
                                            <div className="panel-header">
                                                <h2>{t("supplyChain.receiveGoods", "Wareneingang buchen")}</h2>
                                            </div>
                                            <form onSubmit={handleReceiveSubmit} className="form-grid">
                                                <label>
                                                    {t("supplyChain.purchaseOrderId", "Bestell-ID (Identifikationsnummer)")}
                                                    <input
                                                        type="number"
                                                        value={receiveForm.orderId}
                                                        onChange={(e) => setReceiveForm({ ...receiveForm, orderId: e.target.value })}
                                                        required
                                                    />
                                                </label>
                                                <label>
                                                    {t("supplyChain.warehouse", "Lager")}
                                                    <select
                                                        value={receiveForm.warehouseId}
                                                        onChange={(e) => setReceiveForm({ ...receiveForm, warehouseId: e.target.value })}
                                                        required
                                                    >
                                                        <option value="">{t("supplyChain.chooseWarehouse", "Lager wählen")}</option>
                                                        {warehouses.map((warehouse) => (
                                                            <option key={warehouse.id} value={warehouse.id}>
                                                                {warehouse.name}
                                                            </option>
                                                        ))}
                                                    </select>
                                                </label>
                                                <button type="submit" className="primary">{t("supplyChain.bookReceipt", "Wareneingang buchen")}</button>
                                            </form>
                                        </article>
                                    </div>
                                </div>
                            )}
                            {activeTab === "production" && (
                                <div className="tab-panel">
                                    <div className="panel-grid two-column">
                                        <article className="card form-card">
                                            <div className="panel-header">
                                                <h2>{t("supplyChain.newProductionOrder", "Neuer Produktionsauftrag")}</h2>
                                            </div>
                                            <form className="form-grid" onSubmit={handleProductionOrderSubmit}>
                                                <label>
                                                    {t("supplyChain.orderNumber", "Bestellnummer")}
                                                    <input
                                                        ref={productionOrderNumberInputRef}
                                                        type="text"
                                                        value={productionOrderForm.orderNumber}
                                                        onChange={(e) => setProductionOrderForm({ ...productionOrderForm, orderNumber: e.target.value })}
                                                        required
                                                    />
                                                </label>
                                                <label>
                                                    {t("supplyChain.product", "Produkt")}
                                                    <select
                                                        value={productionOrderForm.productId}
                                                        onChange={(e) => setProductionOrderForm({ ...productionOrderForm, productId: e.target.value })}
                                                        required
                                                    >
                                                        <option value="">{t("supplyChain.chooseProduct", "Produkt wählen")}</option>
                                                        {products.map((product) => (
                                                            <option key={product.id} value={product.id}>
                                                                {product.name}
                                                            </option>
                                                        ))}
                                                    </select>
                                                </label>
                                                <label>
                                                    {t("supplyChain.quantity", "Menge")}
                                                    <input
                                                        type="number"
                                                        step="0.01"
                                                        value={productionOrderForm.quantity}
                                                        onChange={(e) => setProductionOrderForm({ ...productionOrderForm, quantity: e.target.value })}
                                                        required
                                                    />
                                                </label>
                                                <label>
                                                    {t("supplyChain.status", "Status")}
                                                    <select
                                                        value={productionOrderForm.status}
                                                        onChange={(e) => setProductionOrderForm({ ...productionOrderForm, status: e.target.value })}
                                                    >
                                                        {productionStatusOptions.map((option) => (
                                                            <option key={option.value} value={option.value}>
                                                                {option.label}
                                                            </option>
                                                        ))}
                                                    </select>
                                                </label>
                                                <label>
                                                    {t("supplyChain.startDate", "Startdatum")}
                                                    <input
                                                        type="date"
                                                        value={productionOrderForm.startDate}
                                                        onChange={(e) => setProductionOrderForm({ ...productionOrderForm, startDate: e.target.value })}
                                                    />
                                                </label>
                                                <label>
                                                    {t("supplyChain.completionDate", "Fertigstellung")}
                                                    <input
                                                        type="date"
                                                        value={productionOrderForm.completionDate}
                                                        onChange={(e) => setProductionOrderForm({ ...productionOrderForm, completionDate: e.target.value })}
                                                    />
                                                </label>
                                                <button type="submit" className="primary">{t("supplyChain.saveProductionOrder", "Produktion speichern")}</button>
                                            </form>
                                        </article>
                                        <article className="card form-card">
                                            <div className="panel-header">
                                                <h2>{t("supplyChain.updateProductionOrder", "Produktionsauftrag aktualisieren")}</h2>
                                            </div>
                                            <form className="form-grid" onSubmit={handleProductionStatusSubmit}>
                                                <label>
                                                    {t("supplyChain.productionOrderId", "Produktionsauftrag ID (Identifikationsnummer)")}
                                                    <input
                                                        type="number"
                                                        value={productionStatusForm.orderId}
                                                        onChange={(e) => setProductionStatusForm({ ...productionStatusForm, orderId: e.target.value })}
                                                        required
                                                    />
                                                </label>
                                                <label>
                                                    {t("supplyChain.status", "Status")}
                                                    <select
                                                        value={productionStatusForm.status}
                                                        onChange={(e) => setProductionStatusForm({ ...productionStatusForm, status: e.target.value })}
                                                    >
                                                        {productionStatusOptions.map((option) => (
                                                            <option key={option.value} value={option.value}>
                                                                {option.label}
                                                            </option>
                                                        ))}
                                                    </select>
                                                </label>
                                                <label>
                                                    {t("supplyChain.startDateOverride", "Startdatum (optional)")}
                                                    <input
                                                        type="date"
                                                        value={productionStatusForm.startDate}
                                                        onChange={(e) => setProductionStatusForm({ ...productionStatusForm, startDate: e.target.value })}
                                                    />
                                                </label>
                                                <label>
                                                    {t("supplyChain.completionDateOverride", "Fertigstellung (optional)")}
                                                    <input
                                                        type="date"
                                                        value={productionStatusForm.completionDate}
                                                        onChange={(e) => setProductionStatusForm({ ...productionStatusForm, completionDate: e.target.value })}
                                                    />
                                                </label>
                                                <button type="submit" className="primary">{t("supplyChain.update", "Aktualisieren")}</button>
                                            </form>
                                        </article>
                                    </div>
                                    <article className="card table-card">
                                        <div className="panel-header">
                                            <h2>{t("supplyChain.productionOrdersHeadline", "Produktionsaufträge")}</h2>
                                        </div>
                                        {loading ? (
                                            <p>{t("supplyChain.loading", "Lade...")}</p>
                                        ) : productionOrders.length === 0 ? (
                                            <p className="muted">{t("supplyChain.noProductionOrders", "Noch keine Produktionsaufträge vorhanden.")}</p>
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
                                    </article>
                                </div>
                            )}
                            {activeTab === "sales" && (
                                <div className="tab-panel">
                                    <div className="panel-grid two-column">
                                        <article className="card form-card">
                                            <div className="panel-header">
                                                <h2>{t("supplyChain.newSalesOrder", "Neuer Verkaufsauftrag")}</h2>
                                            </div>
                                            <form onSubmit={handleSalesSubmit} className="form-grid">
                                                <label>
                                                    {t("supplyChain.orderNumber", "Bestellnummer")}
                                                    <input
                                                        type="text"
                                                        value={salesForm.orderNumber}
                                                        onChange={(e) => setSalesForm({ ...salesForm, orderNumber: e.target.value })}
                                                    />
                                                </label>
                                                <label>
                                                    {t("supplyChain.customer", "Kunde")}
                                                    <input
                                                        type="text"
                                                        value={salesForm.customerName}
                                                        onChange={(e) => setSalesForm({ ...salesForm, customerName: e.target.value })}
                                                        required
                                                    />
                                                </label>
                                                <label>
                                                    {t("supplyChain.expectedDate", "Liefertermin")}
                                                    <input
                                                        type="date"
                                                        value={salesForm.dueDate}
                                                        onChange={(e) => setSalesForm({ ...salesForm, dueDate: e.target.value })}
                                                    />
                                                </label>
                                                <label>
                                                    {t("supplyChain.targetWarehouse", "Warenausgang aus Lager")}
                                                    <select
                                                        value={salesForm.warehouseId}
                                                        onChange={(e) => setSalesForm({ ...salesForm, warehouseId: e.target.value })}
                                                    >
                                                        <option value="">{t("supplyChain.chooseWarehouse", "Lager wählen")}</option>
                                                        {warehouses.map((warehouse) => (
                                                            <option key={warehouse.id} value={warehouse.id}>
                                                                {warehouse.name}
                                                            </option>
                                                        ))}
                                                    </select>
                                                </label>
                                                <div className="po-lines">
                                                    <h3>{t("supplyChain.orderLines", "Auftragspositionen")}</h3>
                                                    {salesLines.map((line, index) => (
                                                        <div key={index} className="po-line">
                                                            <select
                                                                value={line.productId}
                                                                onChange={(e) => updateSalesLine(index, "productId", e.target.value)}
                                                                required
                                                            >
                                                                <option value="">{t("supplyChain.product", "Produkt")}</option>
                                                                {products.map((product) => (
                                                                    <option key={product.id} value={product.id}>
                                                                        {product.name}
                                                                    </option>
                                                                ))}
                                                            </select>
                                                            <input
                                                                type="number"
                                                                step="0.01"
                                                                placeholder={t("supplyChain.quantity", "Menge")}
                                                                value={line.quantity}
                                                                onChange={(e) => updateSalesLine(index, "quantity", e.target.value)}
                                                                required
                                                            />
                                                            <input
                                                                type="number"
                                                                step="0.01"
                                                                placeholder={t("supplyChain.unitPrice", "Verkaufspreis")}
                                                                value={line.unitPrice}
                                                                onChange={(e) => updateSalesLine(index, "unitPrice", e.target.value)}
                                                            />
                                                            {salesLines.length > 1 && (
                                                                <button type="button" className="ghost" onClick={() => removeSalesLine(index)}>
                                                                    {t("common.remove", "Entfernen")}
                                                                </button>
                                                            )}
                                                        </div>
                                                    ))}
                                                    <button type="button" className="secondary" onClick={addSalesLine}>
                                                        {t("supplyChain.addLine", "Zeile hinzufügen")}
                                                    </button>
                                                </div>
                                                <button type="submit" className="primary">{t("supplyChain.createSales", "Auftrag anlegen")}</button>
                                            </form>
                                        </article>
                                        <article className="card form-card">
                                            <div className="panel-header">
                                                <h2>{t("supplyChain.fulfillmentHeadline", "Warenausgang")}</h2>
                                            </div>
                                            <form onSubmit={handleFulfillSubmit} className="form-grid">
                                                <label>
                                                    {t("supplyChain.salesOrderId", "Auftrags-ID (Identifikationsnummer)")}
                                                    <input
                                                        type="number"
                                                        value={fulfillForm.orderId}
                                                        onChange={(e) => setFulfillForm({ ...fulfillForm, orderId: e.target.value })}
                                                        required
                                                    />
                                                </label>
                                                <label>
                                                    {t("supplyChain.shippingWarehouse", "Versandlager")}
                                                    <select
                                                        value={fulfillForm.warehouseId}
                                                        onChange={(e) => setFulfillForm({ ...fulfillForm, warehouseId: e.target.value })}
                                                        required
                                                    >
                                                        <option value="">{t("supplyChain.chooseWarehouse", "Lager wählen")}</option>
                                                        {warehouses.map((warehouse) => (
                                                            <option key={warehouse.id} value={warehouse.id}>
                                                                {warehouse.name}
                                                            </option>
                                                        ))}
                                                    </select>
                                                </label>
                                                <button type="submit" className="primary">{t("supplyChain.fulfillOrder", "Warenausgang buchen")}</button>
                                            </form>
                                        </article>
                                    </div>
                                </div>
                            )}
                            {activeTab === "service" && (
                                <div className="tab-panel">
                                    <div className="panel-grid two-column">
                                        <article className="card form-card">
                                            <div className="panel-header">
                                                <h2>{t("supplyChain.newServiceRequest", "Neuer Serviceeinsatz")}</h2>
                                            </div>
                                            <form className="form-grid" onSubmit={handleServiceRequestSubmit}>
                                                <label>
                                                    {t("supplyChain.customer", "Kunde")}
                                                    <input
                                                        ref={serviceCustomerInputRef}
                                                        type="text"
                                                        value={serviceRequestForm.customerName}
                                                        onChange={(e) => setServiceRequestForm({ ...serviceRequestForm, customerName: e.target.value })}
                                                        required
                                                    />
                                                </label>
                                                <label>
                                                    {t("supplyChain.subject", "Thema")}
                                                    <input
                                                        type="text"
                                                        value={serviceRequestForm.subject}
                                                        onChange={(e) => setServiceRequestForm({ ...serviceRequestForm, subject: e.target.value })}
                                                        required
                                                    />
                                                </label>
                                                <label>
                                                    {t("supplyChain.description", "Beschreibung")}
                                                    <textarea
                                                        value={serviceRequestForm.description}
                                                        onChange={(e) => setServiceRequestForm({ ...serviceRequestForm, description: e.target.value })}
                                                        rows={3}
                                                    />
                                                </label>
                                                <button type="submit" className="primary">{t("supplyChain.logServiceRequest", "Service erfassen")}</button>
                                            </form>
                                        </article>
                                        <article className="card form-card">
                                            <div className="panel-header">
                                                <h2>{t("supplyChain.updateServiceRequest", "Serviceeinsatz aktualisieren")}</h2>
                                            </div>
                                            <form className="form-grid" onSubmit={handleServiceStatusSubmit}>
                                                <label>
                                                    {t("supplyChain.serviceRequestId", "Serviceeinsatz ID (Identifikationsnummer)")}
                                                    <input
                                                        type="number"
                                                        value={serviceStatusForm.requestId}
                                                        onChange={(e) => setServiceStatusForm({ ...serviceStatusForm, requestId: e.target.value })}
                                                        required
                                                    />
                                                </label>
                                                <label>
                                                    {t("supplyChain.status", "Status")}
                                                    <select
                                                        value={serviceStatusForm.status}
                                                        onChange={(e) => setServiceStatusForm({ ...serviceStatusForm, status: e.target.value })}
                                                    >
                                                        {serviceStatusOptions.map((option) => (
                                                            <option key={option.value} value={option.value}>
                                                                {option.label}
                                                            </option>
                                                        ))}
                                                    </select>
                                                </label>
                                                <label>
                                                    {t("supplyChain.closedDate", "Geschlossen")}
                                                    <input
                                                        type="date"
                                                        value={serviceStatusForm.closedDate}
                                                        onChange={(e) => setServiceStatusForm({ ...serviceStatusForm, closedDate: e.target.value })}
                                                    />
                                                </label>
                                                <button type="submit" className="primary">{t("supplyChain.update", "Aktualisieren")}</button>
                                            </form>
                                        </article>
                                    </div>
                                    <article className="card table-card">
                                        <div className="panel-header">
                                            <h2>{t("supplyChain.serviceRequestsHeadline", "Serviceeinsätze")}</h2>
                                        </div>
                                        {loading ? (
                                            <p>{t("supplyChain.loading", "Lade...")}</p>
                                        ) : serviceRequests.length === 0 ? (
                                            <p className="muted">{t("supplyChain.noServiceRequests", "Noch keine Serviceeinsätze erfasst.")}</p>
                                        ) : (
                                            <div className="table-wrapper">
                                                <table>
                                                    <thead>
                                                        <tr>
                                                            <th>{t("supplyChain.id", "ID (Identifikationsnummer)")}</th>
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
                                    </article>
                                </div>
                            )}
                            {activeTab === "workflows" && (
                                <div className="tab-panel">
                                    <article className="card workflow-card">
                                        <div className="panel-header">
                                            <h2>{t("supplyChain.mobileWorkflowDesigner", "Low-Code Mobile Workflow Designer")}</h2>
                                            <p className="muted">
                                                {t(
                                                    "supplyChain.mobileWorkflowSubtitle",
                                                    "Konfigurieren Sie mobile MDE (Mobile Datenerfassung)-Prozesse ohne Deployment – einfach JSON (JavaScript Object Notation) anpassen und sofort ausrollen."
                                                )}
                                            </p>
                                        </div>
                                        <div className="workflow-designer">
                                            <label>
                                                {t("supplyChain.workflowJsonLabel", "Workflow-Definition (JSON, JavaScript Object Notation)")}
                                                <textarea
                                                    value={workflowSchema}
                                                    onChange={(event) => setWorkflowSchema(event.target.value)}
                                                    spellCheck={false}
                                                />
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
                                                                {(workflowPreview.data.steps?.length ?? 0)} {t("supplyChain.workflowStepsLabel", "Schritte")} · {t("supplyChain.workflowRolesLabel", "Rollen")}:
                                                                {(workflowPreview.data.roles ?? []).length > 0
                                                                    ? (workflowPreview.data.roles ?? []).join(", ")
                                                                    : t("supplyChain.workflowRolesDefault", "Keine Rollen definiert")}
                                                            </p>
                                                        </div>
                                                        {(workflowPreview.data.steps ?? []).map((step) => (
                                                            <div key={step.id} className="workflow-preview-card">
                                                                <strong>{step.prompt ?? step.id}</strong>
                                                                <p className="muted">{t("supplyChain.workflowStepTypeLabel", "Typ")}: {step.type}</p>
                                                                {step.requires && step.requires.length > 0 && (
                                                                    <p className="muted">
                                                                        {t("supplyChain.workflowStepDependsLabel", "Abhängigkeiten")}: {step.requires.join(", ")}
                                                                    </p>
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
                                            <button type="button" className="secondary" onClick={handleWorkflowReset}>
                                                {t("supplyChain.workflowResetCta", "Template wiederherstellen")}
                                            </button>
                                            <button type="button" className="primary" onClick={handleWorkflowCopy}>
                                                {t("supplyChain.workflowCopyCta", "JSON (JavaScript Object Notation) kopieren")}
                                            </button>
                                        </div>
                                    </article>
                                </div>
                            )}
                        </div>
                    </div>
                </section>
            </main>
        </div>
    );

};

export default SupplyChainDashboard;
