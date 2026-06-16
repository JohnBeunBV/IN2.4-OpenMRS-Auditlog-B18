# Security Backlog – openmrs-module-auditlog

**Project:** IN2.4 – OpenMRS Auditlog Module
**Groep:** B18 | **Sprint:** 2
**Gebaseerd op:** Gap-analyse (gap-analyse.md), CIA-analyse (cia-analyse.md), broncode-inspectie, threat modeling
**Norm:** NEN 7510-2:2024+A1:2026

---

## Overzichtstabel

| ID     | Bevinding                                            | CVSS Base | CWE     | Healthcare-impact                                 | Prioriteit  |
| ------ | ---------------------------------------------------- | :-------: | ------- | ------------------------------------------------- | :---------: |
| SEC-01 | Export endpoint zonder auth (missing access control) |    9.1    | CWE-862 | Volledige audittrail patiëntmutaties uitleesbaar  | 🔴 Critical |
| SEC-11 | SQL Injection via auditlog-zoekopdracht              |    8.8    | CWE-89  | Auditlog-tabel manipuleerbaar en uitleesbaar      | 🔴 Critical |
| SEC-02 | `showForm()` zonder toegangscontrole                 |    8.2    | CWE-862 | Auditlogs met medische mutaties inzichtelijk      |   🟠 High   |
| SEC-03 | Default auditingstrategie = NONE                     |    7.5    | CWE-778 | Geen logging van patiëntmutaties na fresh install |   🟠 High   |
| SEC-04 | READ-acties niet gelogd                              |    7.5    | CWE-778 | Inzage patiëntdata niet aantoonbaar (NEN/AVG)     |   🟠 High   |
| SEC-05 | Privilege 'View Audit Log' niet geregistreerd        |    5.3    | CWE-284 | Toegangsbeheer auditlog functioneert incorrect    |  🟡 Medium  |
| SEC-06 | AuditLog-entries volledig muteerbaar                 |    6.5    | CWE-284 | Forensisch bewijs kan worden gewist of aangepast  |  🟡 Medium  |
| SEC-07 | Geen IP-adres/sessie-ID in log-entries               |    5.3    | CWE-778 | Forensische herleidbaarheid ontbreekt             |  🟡 Medium  |
| SEC-08 | Daemon/unauthenticated operaties → user=null         |    4.3    | CWE-778 | Systeemacties op patiëntdata niet herleidbaar     |  🟡 Medium  |
| SEC-09 | DB-bypass omzeilt auditmechanisme                    |    6.3    | CWE-778 | Directe DB-wijzigingen in patiëntdata ongezien    |   🟢 Low    |
| SEC-10 | OpenMRS 1.8.3 EOL – verouderd platform               |     —     | —       | Onbekend tot SBOM-scan compleet                   |   🟢 Low    |

---

## Priority 1 – CRITICAL (onmiddellijk behandelen)

---

### SEC-01 · Beveilig exportAuditLogs()-endpoint met authenticatie en privilege-check

**Risico-ID:** R-01 | **Score:** 20/25 | **NEN 7510:** A.8.3, A.8.5
**CVSS Base Score:** 9.1 (AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:N/A:N)
**CWE:** CWE-862 (Missing Authorization)
**Bestand:** `ViewAuditLogController.java` r.51–64

**Beschrijving:**
Het endpoint `module/auditlog/exportAuditLogs` is volledig anoniem toegankelijk. Elke gebruiker
zonder login kan via een HTTP GET-request de volledige audittrail als CSV exporteren. Dit is een
gecombineerde missing access control en unauthenticated mass data export kwetsbaarheid. De
`userId`-parameter wordt geaccepteerd maar volledig genegeerd — de query retourneert altijd alle
logs ongeacht de meegegeven waarde.

Bijkomende bevinding: de exportmethode roept `al.getClassName()` aan (r.63), maar deze methode
bestaat niet in `AuditLog.java`. De klasse biedt `getType()` (geeft `Class<?>`) en
`getSimpleTypeName()` (geeft `String`). Dit is een compilatiefout die eveneens opgelost moet worden.

**Acceptatiecriteria:**

- [ ] `Context.isAuthenticated()` wordt gecontroleerd vóór het verwerken van het request; bij false → HTTP 401
- [ ] `Context.requirePrivilege(AuditLogConstants.PRIV_GET_AUDITLOGS)` wordt gecontroleerd; bij ontbrekend privilege → HTTP 403
- [ ] De `userId`-parameter wordt gevalideerd; export toont alleen logs van/voor de requestende gebruiker tenzij `PRIV_MANAGE_AUDITLOG` aanwezig is
- [ ] `al.getClassName()` vervangen door `al.getSimpleTypeName()` (of `al.getType().getName()`), zodat de methode compileert
- [ ] Unit test aanwezig die anonieme toegang verifieert → verwachte 401
- [ ] Unit test aanwezig die ongeprivilegieerde toegang verifieert → verwachte 403

**Effort:** M (halve dag)

---

### SEC-11 · SQL Injection in auditlog-zoekopdracht

**Risico-ID:** R-11 | **Score:** 21/25 | **NEN 7510:** A.8.28, A.8.25
**CVSS Base Score:** 8.8 (AV:N/AC:L/PR:L/UI:N/S:U/C:H/I:H/A:N)
**CWE:** CWE-89 (Improper Neutralization of Special Elements used in an SQL Command)
**Bestand:** Audit service layer (`searchAuditLogsByUser()`)

**Beschrijving:**
De methode `searchAuditLogsByUser()` bevat een bevestigde SQL injection kwetsbaarheid. De parameter
`userDisplayName` wordt zonder validatie of parameter binding rechtstreeks in een SQL-string
geconcateneerd en uitgevoerd via `createSQLQuery()`. De query opereert op de `audit_log`-tabel.

Een aanvaller kan via een gemanipuleerde invoer de WHERE-clause omzeilen en zo alle auditlog-entries
uitlezen, ongeacht de bedoelde filtering op gebruiker. Daarnaast is de `audit_log`-tabel via deze
vector manipuleerbaar: entries kunnen worden gewijzigd of verwijderd, waarmee forensisch
bewijsmateriaal verloren gaat. De impact is primair beperkt tot de auditlog-tabel — niet de bredere
patiëntendatabase — maar in een zorgcontext is aantasting van de audittrail direct in strijd met
NEN 7510-2 A.8.15 en de AVG-verplichting tot aantoonbare logging (art. 5 lid 2).

**Acceptatiecriteria:**

- [ ] `createSQLQuery()` met string-concatenatie vervangen door Hibernate parameter binding (`setParameter()`)
- [ ] Inputvalidatie aanwezig op de `userDisplayName`-parameter
- [ ] SAST-tool (Snyk Code) bevestigt geen SQL Injection-bevindingen meer in de module
- [ ] Integratietest aanwezig die een SQL injection-payload (`' OR '1'='1`) afvangt zonder foutmelding of dataleek

**Effort:** S–M (< 1 dag, geïsoleerde fix in één methode)

---

## Priority 2 – HIGH (behandelen binnen 1 maand)

---

### SEC-02 · Voeg toegangscontrole toe aan showForm()-endpoint

**Risico-ID:** R-02 | **Score:** 16/25 | **NEN 7510:** A.8.3
**CVSS Base Score:** 8.2 (AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:N/A:N)
**CWE:** CWE-862 (Missing Authorization)
**Bestand:** `ViewAuditLogController.java` r.41–49

**Beschrijving:**
De methode `showForm()` verwerkt alle requests naar `module/auditlog/viewAuditLog.htm` zonder enige
authenticatie- of autorisatiecontrole. Elke HTTP-aanroep levert alle auditlogs op, inclusief
medische informatie over patiëntmutaties.

**Acceptatiecriteria:**

- [ ] `Context.requirePrivilege(AuditLogConstants.PRIV_GET_AUDITLOGS)` toegevoegd als eerste statement in `showForm()`
- [ ] Niet-ingelogde gebruikers worden omgeleid naar de loginpagina (OpenMRS-standaardgedrag na requirePrivilege)
- [ ] Integratie- of unit-test bevestigt dat niet-geauthenticeerde aanroepen worden geblokkeerd

**Effort:** S (< 4 uur)

---

### SEC-03 · Verander standaard auditingstrategie van NONE naar NONE_EXCEPT

**Risico-ID:** R-04 | **Score:** 16/25 | **NEN 7510:** A.8.15
**CVSS Base Score:** 7.5 (AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:N/A:N)
**CWE:** CWE-778 (Insufficient Logging)
**Bestand:** `config.xml` r.47–54

**Beschrijving:**
De standaardwaarde van `auditlog.auditingStrategy` is `NONE`. Na installatie van de module zonder
extra configuratie wordt er niets gelogd. Dit is direct in strijd met NEN 7510-2 A.8.15, dat
vereist dat relevante beveiligingsgebeurtenissen worden vastgelegd.

**Acceptatiecriteria:**

- [ ] `defaultValue` in `config.xml` gewijzigd naar `NONE_EXCEPT` of `ALL`
- [ ] Installatiedocumentatie (README) bijgewerkt met instructie voor minimaal vereiste configuratie
- [ ] `AuditLogActivator.java` logt een WARNING als de strategie na startup nog `NONE` is

**Effort:** S (< 4 uur)

---

### SEC-04 · Voeg READ-actie toe aan Action-enum en implementeer read-logging

**Risico-ID:** R-05 | **Score:** 16/25 | **NEN 7510:** A.8.15
**CVSS Base Score:** 7.5 (AV:N/AC:L/PR:L/UI:N/S:U/C:H/I:N/A:N)
**CWE:** CWE-778 (Insufficient Logging)
**Bestand:** `AuditLog.java` r.64–66; `HibernateAuditLogInterceptor.java`

**Beschrijving:**
De `Action`-enum heeft alleen `CREATED`, `UPDATED` en `DELETED`. Raadpleging van patiëntgegevens
(READ) wordt nooit gelogd. NEN 7510-2 A.8.15 vereist expliciet dat ook toegang tot gevoelige
gegevens wordt bijgehouden.

**Acceptatiecriteria:**

- [ ] `READ` toegevoegd aan de `Action`-enum in `AuditLog.java`
- [ ] Hibernate `onLoad()`-methode in `HibernateAuditLogInterceptor` geïmplementeerd voor geauditeerde entiteiten
- [ ] Configuratie-optie beschikbaar om READ-logging per entiteitstype in/uit te schakelen (performance-overweging)
- [ ] Liquibase-migratie bijgewerkt als het DB-schema wijzigt
- [ ] Unit test bevestigt dat READ-acties worden gelogd

**Effort:** L (1 week)

---

## Priority 3 – MEDIUM (behandelen binnen 3 maanden)

---

### SEC-05 · Registreer ontbrekende privilege 'View Audit Log' in config.xml

**Risico-ID:** R-03 | **Score:** 9/25 | **NEN 7510:** A.8.3
**CVSS Base Score:** 5.3 (AV:N/AC:L/PR:N/UI:N/S:U/C:L/I:N/A:N)
**CWE:** CWE-284 (Improper Access Control)
**Bestand:** `config.xml`; `AuditLogWebConstants.java` r.21

**Beschrijving:**
De privilege-string `"View Audit Log"` (in `AuditLogWebConstants.PRIV_VIEW_AUDITLOG`) wordt gebruikt
in `DWRAuditLogService` maar is nooit geregistreerd als `<privilege>` in `config.xml`. Hierdoor kan
deze privilege nooit aan een rol worden toegekend via de OpenMRS-beheerdersinterface.

**Acceptatiecriteria:**

- [ ] `<privilege><name>View Audit Log</name>...</privilege>` toegevoegd aan `config.xml`
- [ ] Alle privileges die in de codebase worden gebruikt zijn aantoonbaar geregistreerd in `config.xml`
- [ ] Handmatige verificatie: privilege verschijnt in OpenMRS Admin > Manage Privileges

**Effort:** S (< 4 uur)

---

### SEC-06 · Implementeer write-once bescherming voor AuditLog-entries

**Risico-ID:** R-06 | **Score:** 10/25 | **NEN 7510:** A.8.15
**CVSS Base Score:** 6.5 (AV:N/AC:L/PR:L/UI:N/S:U/C:N/I:H/A:N)
**CWE:** CWE-284 (Improper Access Control)
**Bestand:** `AuditLog.java` (setters); `AuditLog.hbm.xml`

**Beschrijving:**
Bestaande auditlog-entries zijn volledig muteerbaar: `setAction()`, `setUser()`, `setDateCreated()`
etc. hebben geen bescherming. Een gebruiker met voldoende rechten kan log-entries aanpassen of
verwijderen, waarmee forensisch bewijsmateriaal wordt vernietigd.

**Acceptatiecriteria:**

- [ ] Optie A: Setters verwijderd of `@Deprecated` gemarkeerd voor immutable velden na persistentie
- [ ] Optie B: Database-niveau constraint via Hibernate `@Immutable` annotatie op de `AuditLog`-klasse
- [ ] Optie C: Hashketen geïmplementeerd (elke entry bevat hash van vorige entry)
- [ ] Minimaal optie A of B geïmplementeerd en getest
- [ ] Test bevestigt dat wijziging van een bestaande log-entry een exception gooit of niet persisteert

**Effort:** M (halve dag voor optie A/B; XL voor optie C)

---

### SEC-07 · Voeg IP-adres en sessie-ID toe aan AuditLog-entity

**Risico-ID:** R-08 | **Score:** 9/25 | **NEN 7510:** A.8.15
**CVSS Base Score:** 5.3 (AV:N/AC:L/PR:L/UI:N/S:U/C:N/I:N/A:N)
**CWE:** CWE-778 (Insufficient Logging)
**Bestand:** `AuditLog.java`; `HibernateAuditLogInterceptor.java`; `liquibase.xml`

**Beschrijving:**
Log-entries bevatten geen netwerkcontext. Het is onmogelijk te achterhalen vanaf welk apparaat of
IP-adres een wijziging is doorgevoerd. NEN 7510-2 A.8.15 vereist dat logrecords voldoende context
bevatten voor forensisch onderzoek.

**Acceptatiecriteria:**

- [ ] Velden `ipAddress` (String) en `sessionId` (String) toegevoegd aan `AuditLog.java`
- [ ] Liquibase-migratie aangemaakt voor nieuwe kolommen
- [ ] `HibernateAuditLogInterceptor` vult deze velden via `WebConstants.OPENMRS_CLIENT_IP` of HttpServletRequest
- [ ] `AuditLog.hbm.xml` bijgewerkt met nieuwe veldmapping

**Effort:** M (halve dag)

---

### SEC-08 · Behandel unauthenticated/daemon-operaties expliciet in interceptor

**Risico-ID:** R-07 | **Score:** 6/25 | **NEN 7510:** A.8.5
**CVSS Base Score:** 4.3 (AV:N/AC:L/PR:L/UI:N/S:U/C:N/I:L/A:N)
**CWE:** CWE-778 (Insufficient Logging)
**Bestand:** `HibernateAuditLogInterceptor.java` r.381–471

**Beschrijving:**
Operaties uitgevoerd door niet-ingelogde gebruikers of daemon-processen worden stilzwijgend
verwerkt met `user=null`. Een TODO-comment in de code bevestigt dat dit een bekend onopgelost
probleem is. Log-entries zonder eigenaar hebben geen forensische waarde.

**Acceptatiecriteria:**

- [ ] Bij `Context.getAuthenticatedUser() == null`: keuze implementeren tussen (a) blokkeren van de log-entry, (b) markeren als `DAEMON` via een speciale systeemgebruiker, of (c) een aparte log-categorie
- [ ] TODO-comment vervangen door daadwerkelijke implementatie
- [ ] Gemaakte keuze gedocumenteerd in code-comment met onderbouwing

**Effort:** S (< 4 uur)

---

## Priority 4 – LOW (accepteren; opnemen in risicoregister)

---

### SEC-09 · Onderzoek en documenteer risico van directe DB-bypass

**Risico-ID:** R-09 | **Score:** 8/25 | **NEN 7510:** A.8.15
**CVSS Base Score:** 6.3 (AV:N/AC:H/PR:H/UI:N/S:U/C:H/I:H/A:N)
**CWE:** CWE-778 (Insufficient Logging)
**Bestand:** `HibernateAuditLogInterceptor.java` (javadoc)

**Beschrijving:**
Directe databasewijzigingen via SQL-scripts, DB-admin tools of ETL omzeilen de Hibernate-interceptor
volledig en worden niet gelogd. Dit is een architecturele beperking die erkend is in de javadoc.
Volledig opheffen is niet realistisch zonder database-triggers.

**Acceptatiecriteria:**

- [ ] Bevinding gedocumenteerd als geaccepteerd restrisico met onderbouwing in het risicoregister
- [ ] Compenserende maatregel aanbevolen in de installatiegids: DB-audit op databaseniveau (bijv. MySQL binary log monitoring)
- [ ] Optioneel: database-trigger proof-of-concept geëvalueerd

**Effort:** S (< 4 uur documentatie)

---

### SEC-10 · Upgrade of risicoregistratie OpenMRS v1.8.3 (verouderd platform)

**Risico-ID:** R-10 | **Score:** 9/25 | **NEN 7510:** A.8.8
**CVSS Base Score:** — _(afhankelijk van SBOM-scan resultaten; nog niet afgerond)_
**CWE:** — _(nog niet bepaald; afhankelijk van scan-output)_
**Bestand:** `pom.xml` (`openMRSVersion=1.8.3`)

**Beschrijving:**
Het project draait op OpenMRS 1.8.3, een versie die niet langer actief wordt onderhouden. Bekende
CVE's in het core-platform of dependencies (bijv. Spring, Hibernate) kunnen van toepassing zijn
en worden niet meer gepatcht door de upstream.

> ⚠️ **Status:** SBOM-scan via Trivy is gedeeltelijk uitgevoerd maar nog niet volledig geanalyseerd.
> CVSS-scores en CVE-referenties worden ingevuld na afronding van de scan.

**Acceptatiecriteria:**

- [ ] SBOM-scan afgerond en gedocumenteerd
- [ ] CVE's voor OpenMRS 1.8.3 en bijbehorende dependencies gedocumenteerd in het risicoregister
- [ ] Go/no-go besluit voor upgrade naar ondersteunde OpenMRS-versie gedocumenteerd met kostenraming

**Effort:** M (halve dag analyse na scan-output)

---

## Backlog-samenvatting

| ID     | Titel (kort)                               | CVSS | CWE     | Healthcare-impact                         | Prioriteit  | Effort |    SP     |
| ------ | ------------------------------------------ | :--: | ------- | ----------------------------------------- | :---------: | :----: | :-------: |
| SEC-01 | Beveilig exportAuditLogs() endpoint        | 9.1  | CWE-862 | Audittrail uitleesbaar                    | 🔴 Critical |   M    |     3     |
| SEC-11 | SQL Injection auditlog-zoekopdracht        | 8.8  | CWE-89  | Auditlog-tabel manipuleerbaar/uitleesbaar | 🔴 Critical |  S–M   |     4     |
| SEC-02 | Toegangscontrole showForm()                | 8.2  | CWE-862 | Medische mutaties inzichtelijk            |   🟠 High   |   S    |     2     |
| SEC-03 | Default auditingstrategie NONE→NONE_EXCEPT | 7.5  | CWE-778 | Geen logging na fresh install             |   🟠 High   |   S    |     1     |
| SEC-04 | Implementeer READ-logging                  | 7.5  | CWE-778 | Inzage patiëntdata onzichtbaar            |   🟠 High   |   L    |     5     |
| SEC-05 | Registreer View Audit Log privilege        | 5.3  | CWE-284 | Toegangsbeheer onjuist                    |  🟡 Medium  |   S    |     1     |
| SEC-06 | Write-once bescherming AuditLog-entries    | 6.5  | CWE-284 | Forensisch bewijs manipuleerbaar          |  🟡 Medium  |  M–XL  |     3     |
| SEC-07 | IP-adres en sessie-ID in log-entries       | 5.3  | CWE-778 | Forensische herleidbaarheid ontbreekt     |  🟡 Medium  |   M    |     3     |
| SEC-08 | Behandel daemon/unauthenticated operaties  | 4.3  | CWE-778 | Systeemacties niet herleidbaar            |  🟡 Medium  |   S    |     2     |
| SEC-09 | Documenteer DB-bypass restrisico           | 6.3  | CWE-778 | Directe DB-toegang ongezien               |   🟢 Low    |   S    |     1     |
| SEC-10 | Upgrade/risicoregistratie OpenMRS v1.8.3   |  —   | —       | Onbekend (scan lopend)                    |   🟢 Low    |   M    |     2     |
|        | **Totaal**                                 |      |         |                                           |             |        | **27 SP** |

---
