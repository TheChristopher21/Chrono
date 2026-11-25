# OASIS UBL 2.1 schemas

The 15 XSD files under `xsd/` are copied, without content changes, from the official OASIS UBL 2.1 release:
https://docs.oasis-open.org/ubl/os-UBL-2.1/xsd/

Specification: https://docs.oasis-open.org/ubl/os-UBL-2.1/UBL-2.1.html

Copyright OASIS Open 2013. Original notices and distribution terms are retained in every schema. These files include the Invoice/CreditNote schemas and their transitive schema dependencies. Runtime validation resolves these bundled files locally; it does not download schemas or follow arbitrary external DTDs.

Chrono validates UBL syntax plus its explicitly implemented invoice checks. It does not label this generic export as Peppol BIS, XRechnung, or another national customization. Those profiles require additional business-rule, code-list, routing, and recipient checks; see https://docs.peppol.eu/poacc/billing/3.0/syntax/ubl-invoice/.
