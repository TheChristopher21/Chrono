# PMS mixed staff benchmark — 2026-09-12

The isolated API at `http://127.0.0.1:18085` completed a 45-second k6 0.57.0 run with 12 virtual users: three reception, three housekeeping, three finance and three management users. All configured latency and error thresholds passed; k6 exited with code 0.

| Measure | Result |
| --- | ---: |
| Hotel rooms | 1,007, including 1,000 newly seeded synthetic rooms |
| HTTP requests, including four access preflight requests | 1,347 |
| Workload operations checked | 1,343 |
| Completed iterations | 1,056 |
| HTTP / workload / authorization errors | 0 / 0 / 0 |
| Actual write requests / controlled conflicts | 24 / 0 |
| Overall read p95 / p99 | 28.66 / 39.74 ms |
| Overall write p95 / p99 | 50.77 / 71.08 ms |
| Reception read p95 | 36.78 ms |
| Housekeeping read p95 | 10.85 ms |
| Finance read p95 | 12.07 ms |
| Management read p95 | 14.13 ms |
| HTTP throughput | 29.58 requests/second |

Each role used a separate `ROLE_USER` identity with `master=false`. Reception had `FRONT_DESK` and `GUESTS` manage grants; housekeeping had `HOUSEKEEPING` manage; finance had `FINANCE` manage; management had `REPORTS` view. Eight explicit negative preflight checks returned the expected HTTP 403: master access administration and an unrelated hotel area for each identity.

Reception cycled a 30-day room-plan page of 50 rooms, the operations snapshot and guest search. Housekeeping read work orders and changed one dedicated inspection task between OPEN and IN_PROGRESS with optimistic versions. Finance read paginated invoices and open receivables. Management read the portfolio report. Writes created nine synthetic guest profiles and applied 15 housekeeping updates; they did not invoke messaging, outbox or payment-provider endpoints.

The 5% write setting is a probability on eligible reception and housekeeping iterations. It produced 24 writes out of 1,343 workload calls (1.79%), not a 5% share of all HTTP requests. Finance performs two reads per iteration.

Before the workload, the visible fixture comprised eight guest profiles, nine reservations/folios, one invoice and two housekeeping work orders, including the new dedicated task. Afterwards there were 17 visible guest profiles, nine reservations/folios and the same two housekeeping tasks; the dedicated task reached version 15. Four local test users and 1,000 synthetic rooms remain in the isolated QA database.

This is a short local concurrency smoke benchmark, not a certification of production capacity or a long-running saturation test. Most of the 1,007 rooms were unoccupied; reservation, invoice and receivable history was small. The run did not exercise concurrent booking allocation, payment capture/refund, multiple hotel tenants, WAN latency, failover or sustained high write contention. Permission checks verify the assigned grants and endpoint outcomes; they do not independently validate every field's redaction in each allowed response. The frozen QA server predates the final legacy housekeeping fix; this workload used the new work-order API. The parent task was performing visual QA concurrently on the same isolated server.

Raw sanitized evidence: `pms-staff-load-summary.json`, `pms-staff-load-metadata.json`, and `../../tmp/pms-staff-load.log`. No bearer tokens or JWTs were found in these artifacts. The native k6 summary's threshold boolean represents whether the threshold failed; `false` therefore means no threshold failure.
