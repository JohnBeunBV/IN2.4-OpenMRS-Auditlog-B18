# Bow Tie Analyse — SEC-01: Export Audit Logs zonder Authenticatie

## Tabel 1 — Oorzaken & Preventieve Barrières

| # | Oorzaak | Preventieve barrière |
|---|---------|----------------------|
| 1 | Geen authenticatiecontrole op `exportAuditLogs`-endpoint | `Context.isAuthenticated()` controleren vóór request-verwerking; bij false → HTTP 401 |
| 2 | Geen privilege-check (missing access control, CWE-862) | `Context.requirePrivilege(PRIV_GET_AUDITLOGS)` afdwingen; bij ontbrekend privilege → HTTP 403 |
| 3 | `userId`-parameter wordt geaccepteerd maar genegeerd | Validatie van `userId`: export beperken tot eigen gebruiker, tenzij `PRIV_MANAGE_AUDITLOG` aanwezig is |
| 4 | Endpoint direct via GET-request bereikbaar zonder sessie | Endpointbeveiliging consistent toepassen op alle audittrail-endpoints (`module/auditlog/*`) |
| 5 | `userId`-parameter wordt doorgegeven maar niet gebruikt in de query — `getAuditLogs()` wordt altijd zonder gebruikersfilter aangeroepen (IDOR, CWE-639) | `getAuditLogs()` aanroepen met de opgegeven `userId` als filter; alleen toestaan als de aanvrager zichzelf opvraagt of `PRIV_MANAGE_AUDITLOG` heeft |

---

## TOP EVENT

> **Onbevoegde gebruiker exporteert volledige audittrail van patiëntmutaties**

---

## Tabel 2 — Gevolgen & Mitigerende Barrières

| # | Gevolg | Mitigerende barrière | Type |
|---|--------|----------------------|------|
| 1 | Volledige audittrail patiëntmutaties (incl. PII) blootgesteld als CSV | Encryptie van audittraildata at rest | Informatie |
| 2 | Inzicht in wijzigingen op patiëntdossiers door externen | Detectie van ongebruikelijke export-volumes / anomaly monitoring | Informatie |
| 3 | Ongeautoriseerde mass data export onopgemerkt | Logging en alerting op toegang tot `module/auditlog/*` endpoints | Informatie |
| 4 | Datalek conform AVG/NEN 7510 (A.8.3, A.8.5) | Incident response- en datalekmeldprocedure activeren | Operationeel |
| 5 | Aanvaller kan door enumeratie van `userId`-waarden gericht auditdata van specifieke patiënten of medewerkers opvragen | Rate limiting en input-validatie op `userId`-parameter; blokkeer requests zonder geldige sessie-gebonden gebruikerscontext | Operationeel |