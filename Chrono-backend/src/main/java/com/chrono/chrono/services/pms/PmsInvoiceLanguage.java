package com.chrono.chrono.services.pms;
import java.util.*;
/** Labels belong to the immutable document language; entered names/descriptions are never translated. */
final class PmsInvoiceLanguage {
 private static final Map<String,List<String>> LABELS=Map.of(
  "DE",List.of("Rechnung","Gutschrift","Korrektur zu Rechnung","Dokumentdatum","Fällig","Register","Referenz / PO","Kostenstelle","Rechnungs-E-Mail","Leistungszeitraum","bis","Leistung","Datum","Menge","Steuer %","Netto","Brutto","MWST / Tax","Total","Swiss QR-Zahlteil","Betrag"),
  "EN",List.of("Invoice","Credit note","Correction of invoice","Issue date","Due date","Registration","Reference / PO","Cost centre","Billing email","Service period","to","Description","Date","Quantity","Tax %","Net","Gross","VAT / Tax","Total","Swiss QR payment section","Amount"),
  "FR",List.of("Facture","Avoir","Correction de la facture","Date du document","Échéance","Registre","Référence / BC","Centre de coûts","E-mail de facturation","Période de prestation","au","Prestation","Date","Quantité","Taxe %","Net","Brut","TVA / Taxe","Total","Section de paiement QR suisse","Montant"),
  "IT",List.of("Fattura","Nota di credito","Rettifica della fattura","Data documento","Scadenza","Registro","Riferimento / Ordine","Centro di costo","E-mail di fatturazione","Periodo di servizio","a","Prestazione","Data","Quantità","Imposta %","Netto","Lordo","IVA / Imposta","Totale","Sezione di pagamento QR svizzera","Importo"),
  "ES",List.of("Factura","Nota de crédito","Corrección de la factura","Fecha del documento","Vencimiento","Registro","Referencia / Pedido","Centro de costes","Correo de facturación","Periodo del servicio","a","Servicio","Fecha","Cantidad","Impuesto %","Neto","Bruto","IVA / Impuesto","Total","Sección de pago QR suiza","Importe"));
 static String text(String language,int key){return LABELS.getOrDefault(language==null?"DE":language.toUpperCase(Locale.ROOT),LABELS.get("DE")).get(key);}
 private PmsInvoiceLanguage(){}
}
