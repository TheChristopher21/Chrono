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

    useEffect(() => {
        const load = async () => {
            setLoading(true);
            try {
                const [productRes, warehouseRes, stockRes] = await Promise.all([
                    api.get("/api/supply-chain/products"),
                    api.get("/api/supply-chain/warehouses"),
                    api.get("/api/supply-chain/stock")
                ]);
                setProducts(productRes.data ?? []);
                setWarehouses(warehouseRes.data ?? []);
                setStock(stockRes.data ?? []);
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
            </main>
        </div>
    );
};

export default SupplyChainDashboard;
