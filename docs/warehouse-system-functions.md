# Funktionen des Lagersystems

## Kernfunktionen der Supply-Chain-API
- **Produktverwaltung**: Produkte anlegen, auflisten und inklusive Preis-/Segmentlogik speichern (`/api/supply-chain/products`).
- **Lagerverwaltung**: Lagerstandorte anlegen und abrufen (`/api/supply-chain/warehouses`).
- **Bestandsübersicht**: Lagerbestände seitenweise abrufen (`/api/supply-chain/stock`).
- **Bestandsbuchungen**: Wareneingang, -ausgang, Korrekturen und Inventurbewegungen buchen; Bewegungen erzeugen automatische Bestandsfortschreibung (`/api/supply-chain/stock/adjust`). Traceability-Felder für Charge, Seriennummer und Mindesthaltbarkeit erlauben FEFO/MEFO-geführte Pick-Routen.
- **Einkaufsprozesse**: Einkaufsbestellungen anlegen, Waren an Lagern vereinnahmen und Lieferantenrechnungen automatisiert erzeugen (`/api/supply-chain/purchase-orders`, `/receive`).
- **Verkaufsprozesse**: Kundenaufträge anlegen und aus Lagern kommissionieren mit Bestandsabzug (`/api/supply-chain/sales-orders`, `/fulfill`).
- **Fulfillment-Optimierung**: Mehrere Sales Orders zu Pick-Waves clustern und mit Live-Routenplanung kommissionieren (`/api/supply-chain/sales-orders/pick-waves`).
- **Auto-Replenishment**: KI-basierte Bedarfsermittlung kombiniert Forecasts und Lieferanten-Scoring zu automatischen Einkaufsentwürfen (`/api/supply-chain/procurement/auto-replenish`).
- **Produktions- und Serviceintegration**: Produktionsaufträge planen, Statuswechsel mit Terminlogik durchführen (`/api/supply-chain/production-orders`, `/status`) sowie Serviceeinsätze erfassen und abschließen (`/api/supply-chain/service-requests`, `/status`).
- **Finanzintegration**: Automatische Übergabe von Eingangsrechnungen an die Kreditorenbuchhaltung.

## Warehouse-Intelligence-API
- **Stammdaten und Bestände**: Erweiterte Produkt-, Standort- und Inventarübersichten (`/api/chrono2/products`, `/locations`, `/inventory`).
- **Smart Slotting**: Lagerplätze nach Kapazität, Zone, Wegezeit und Umschlag empfehlen (`/api/chrono2/slotting`).
- **Bestandsprognosen**: KI-gestützte Forecasts inkl. Stockout-/Überbestandsrisiken (`/api/chrono2/inventory/{productId}/forecast`).
- **IoT-Integration**: Sensorwerte erfassen und je Standort abrufen (`/api/chrono2/iot`).
- **Blockchain-Logistik**: Bewegungen revisionssicher mit Hash protokollieren (`/api/chrono2/blockchain/movement`).
- **Smart Sourcing & Accounting**: Lieferantenbewertung nach Präferenzen und Rechnungsabgleich mit Toleranzen (`/api/chrono2/procurement/*`).
- **Mobiler Wareneingang**: Navigation zum optimalen Einlagerplatz (`/api/chrono2/procurement/mobile-inbound`).
- **Pick-Routenplanung**: Wegeoptimierte Kommissionierung mit Zeit- und Distanzkalkulation (`/api/chrono2/outbound/pick-route`).
- **3D-Bin-Packing**: Optimale Versandkartons aus dem Katalog empfehlen, inklusive Auslastungs- und Gewichtsprüfung (`/api/chrono2/outbound/3d-box-recommendation`).
- **Retourenmanagement**: Rücksendungen erfassen und Workflows nachverfolgen (`/api/chrono2/outbound/returns`).
- **Versandetiketten**: Frachtdaten und Trackingnummern generieren (`/api/chrono2/outbound/carrier-label`).
- **NLP-Analysen**: Sprachabfragen zu KPIs, Forecasts, Temperaturen etc. beantworten (`/api/chrono2/analytics/nlp`).
- **KPI-Dashboard**: Kennzahlen und Trends verdichten (`/api/chrono2/analytics/kpis`).

## Frontend-Dashboard
- **Übersicht**: Kennzahlen zu Produkten, Lagern, Bestandswerten und aktuelle Bestandslisten.
- **Stammdatenformulare**: Produkte und Lager per UI anlegen.
- **Bestandsaktionen**: Lagerbewegungen buchen, Einkaufs- und Verkaufsaufträge erfassen sowie Wareneingänge/-ausgänge abwickeln. Die Bestandsmaske unterstützt optionale Lot-/Serien- und MHD-Angaben für traceable Items.
- **Low-Code Mobile Workflows**: Ein JSON-Designer im Dashboard erzeugt MDE-Workflows (z. B. Inbound → QC → Foto → Buchung), die ohne Redeploy angepasst werden können.

