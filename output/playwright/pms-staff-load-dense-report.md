# PMS dense mixed staff baseline — 2026-09-12

The frozen isolated API at `http://127.0.0.1:18085` completed the 45-second, 12-VU dense workload without HTTP, operation or authorization errors. Write latency exceeded both configured thresholds: p95 2,771.57 ms versus a 1,500 ms limit, and p99 3,146.31 ms versus a 3,000 ms limit. k6 exited with code 99 for these latency failures. The earlier sparse-fixture report and artifacts remain unchanged.

| Measure | Dense baseline |
| --- | ---: |
| Hotel rooms | 1,007 |
| New distinct synthetic guests / confirmed stays | 500 / 500 |
| Stay dates | 2026-09-13 through checkout 2026-09-16 |
| Added room nights / persisted ROOM folio lines | 1,500 / 1,500 |
| Existing plus synthetic stays/folios visible during load | 509 / 509 |
| HTTP requests / checked workload calls | 1,304 / 1,300 |
| Completed iterations | 1,013 |
| HTTP / workload / authorization errors | 0 / 0 / 0 |
| Actual writes / controlled conflicts | 23 / 0 |
| Read p95 / p99 | 101.83 / 130.10 ms |
| Write p95 / p99 | 2,771.57 / 3,146.31 ms |
| Reception read p95 | 132.30 ms |
| Housekeeping read p95 | 10.46 ms |
| Finance read p95 | 9.66 ms |
| Management read p95 | 12.57 ms |
| HTTP throughput | 28.63 requests/second |

All new stays used dedicated synthetic rooms 10000–10499, one newly created guest each, and the supported idempotent front-desk booking API. The script checked CONFIRMED status and three ROOM folio lines for every creation. Seeding took about 522 seconds. No original QA reservation or folio was changed.

Independent API verification traversed all 21 room-plan pages at 50 rooms/page for a 30-day interval. It found all 1,007 unique rooms and all 500 added stays. A separate persisted operations read verified every new folio, exactly three distinct room-service dates, and the complete 1,500-line ROOM ledger. The verified fixture's total gross was CHF 238,500. The frozen demo rate had no tax snapshot, so all 1,500 new ROOM lines had `taxRate=null`; this baseline does **not** establish complete net-revenue reporting. That legacy fixture defect is recorded rather than retroactively edited.

Invoice-history and open-receivable endpoints both returned valid bounded responses: one invoice and zero open receivables. Thus this run meaningfully increases reservation, room-plan and folio volume but does not test large invoice or debtor histories. Internal outbox count was 524 at verification; reservation creation normally persists these events. Delivery and email were disabled on the isolated server; no dispatch, retry or email endpoint was invoked.

The four workload identities were separate non-master ROLE_USER users, with the same reception/HK/finance/report grants as the sparse test. Eight negative access probes again returned the expected HTTP 403. The 23 writes comprised six new synthetic guest profiles and 17 updates to a dedicated HK inspection task, with no conflicts. The 5% setting applies per eligible reception/HK iteration, yielding 1.77% writes among all workload calls. Guest-list visibility is capped at 200 and is not a total guest count.

CPU-heavy test runs were paused for the 45-second measurement. This remains a short single-machine smoke benchmark, without TLS/WAN delay, multiple hotel tenants, failover, simultaneous room allocation, card-provider calls or a representative large financial history. It is not an SLA or production-capacity guarantee. The observed slow mutation responses motivated separate query-batching work; any subsequent optimized run must be reported as a separate dataset, with its own database/fixture differences.

Evidence: `pms-staff-load-dense-summary.json`, `pms-staff-load-dense-metadata.json`, `pms-staff-dense-seed.json`, `pms-staff-dense-verification.json`, and `../../tmp/pms-staff-load-dense.log`. Native k6 threshold booleans indicate a failed threshold: `true` marks each breached write limit.
