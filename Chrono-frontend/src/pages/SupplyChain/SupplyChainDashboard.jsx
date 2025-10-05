import React, { useEffect, useMemo, useState } from "react";
import Navbar from "../../components/Navbar.jsx";
import api from "../../utils/api.js";
import { useNotification } from "../../context/NotificationContext.jsx";
import { useTranslation } from "../../context/LanguageContext.jsx";
import "../../styles/SupplyChainDashboardScoped.css";


const SupplyChainDashboard = () => {
    const { notify } = useNotification();
    const { t } = useTranslation();
    const [products, setProducts] = useState([]);
    const [warehouses, setWarehouses] = useState([]);
    const [stock, setStock] = useState([]);
    const [loading, setLoading] = useState(true);
    const [productForm, setProductForm] = useState({ sku: "", name: "", description: "", unitCost: "", unitPrice: "" });
    const [warehouseForm, setWarehouseForm] = useState({ name: "", location: "" });
    const [adjustForm, setAdjustForm] = useState({ productId: "", warehouseId: "", quantity: "", type: "ADJUSTMENT", reference: "" });
    const [purchaseForm, setPurchaseForm] = useState({ orderNumber: "", vendorName: "", expectedDate: "", warehouseId: "" });
    const [purchaseLines, setPurchaseLines] = useState([{ productId: "", quantity: "", unitCost: "" }]);
    const [receiveForm, setReceiveForm] = useState({ orderId: "", warehouseId: "" });
    const [salesForm, setSalesForm] = useState({ orderNumber: "", customerName: "", dueDate: "", warehouseId: "" });
    const [salesLines, setSalesLines] = useState([{ productId: "", quantity: "", unitPrice: "" }]);
    const [fulfillForm, setFulfillForm] = useState({ orderId: "", warehouseId: "" });

    useEffect(() => {
        const load = async () => {
            setLoading(true);
            try {
                const [productRes, warehouseRes, stockRes] = await Promise.all([
                    api.get("/api/supply-chain/products"),
                    api.get("/api/supply-chain/warehouses"),
                    api.get("/api/supply-chain/stock")
                ]);

                const productData = productRes?.data;
                const stockData = stockRes?.data;

                setProducts(Array.isArray(productData) ? productData : productData?.content ?? []);
                setWarehouses(warehouseRes?.data ?? []);
                setStock(Array.isArray(stockData) ? stockData : stockData?.content ?? []);
            } catch (error) {
                console.error("Failed to load supply chain data", error);
                notify(t("supplyChain.loadError", "Supply-Chain-Daten konnten nicht geladen werden."), "error");
            } finally {
                setLoading(false);
            }
        };
        load();
    }, [notify, t]);

    const totalInventoryValue = useMemo(() => {
        return stock.reduce((sum, entry) => {
            const product = products.find((p) => p.id === entry.product?.id || p.id === entry.productId);
            const cost = product?.unitCost ?? 0;
            return sum + (entry.quantity ?? 0) * cost;
        }, 0);
    }, [stock, products]);

    const handleProductSubmit = async (event) => {
        event.preventDefault();
        try {
            const payload = {
                sku: productForm.sku || undefined,
                name: productForm.name,
                description: productForm.description,
                unitCost: productForm.unitCost ? Number(productForm.unitCost) : undefined,
                unitPrice: productForm.unitPrice ? Number(productForm.unitPrice) : undefined
            };
            await api.post("/api/supply-chain/products", payload);
            notify(t("supplyChain.productCreated", "Produkt angelegt."), "success");
            setProductForm({ sku: "", name: "", description: "", unitCost: "", unitPrice: "" });
            setLoading(true);
            setTimeout(() => setLoading(false), 0);
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
            setTimeout(() => setLoading(false), 0);
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
                reference: adjustForm.reference || undefined
            };
            await api.post("/api/supply-chain/stock/adjust", payload);
            notify(t("supplyChain.adjustmentSaved", "Bestandskorrektur gebucht."), "success");
            setAdjustForm({ productId: "", warehouseId: "", quantity: "", type: "ADJUSTMENT", reference: "" });
            setLoading(true);
            setTimeout(() => setLoading(false), 0);
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
                        unitCost: Number(line.unitCost || 0)
                    }))
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
                warehouseId: Number(receiveForm.warehouseId)
            };
            await api.post(`/api/supply-chain/purchase-orders/${receiveForm.orderId}/receive`, payload);
            notify(t("supplyChain.receiptBooked", "Wareneingang gebucht."), "success");
            setReceiveForm({ orderId: "", warehouseId: "" });
            setLoading(true);
            setTimeout(() => setLoading(false), 0);
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
                        unitPrice: Number(line.unitPrice || 0)
                    }))
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
            setTimeout(() => setLoading(false), 0);
        } catch (error) {
            console.error("Failed to fulfill sales order", error);
            notify(t("supplyChain.fulfillmentFailed", "Warenausgang konnte nicht gebucht werden."), "error");
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
            </main>
        </div>
    );
};

export default SupplyChainDashboard;
