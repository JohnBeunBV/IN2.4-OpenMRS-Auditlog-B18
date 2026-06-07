# Security Backlog – openmrs-module-auditlog

**Project:** IN2.4 – OpenMRS Auditlog Module  
**Groep:** B18 | **Sprint:** 2  
**Gebaseerd op:** Gap-analyse (gap-analyse.md), CIA-analyse (cia-analyse.md), broncode-inspectie  
**Norm:** NEN 7510-2:2024+A1:2026

---

## Priority 1 – CRITICAL (onmiddellijk behandelen, score ≥ 18)

---

### SEC-01 · Beveilig exportAuditLogs()-endpoint met authenticatie en privilege-check

**Risico-ID:** R-01 | **Score:** 20/25 | **NEN 7510:** A.8.3, A.8.5  
**Bestand:** `ViewAuditLogController.java` r.51–64

**Beschrijving:**  
Het endpoint `module/auditlog/exportAuditLogs` is volledig anoniem toegankelijk. Elke gebruiker
zonder login kan via een HTTP GET-request de volledige audittrail als CSV exporteren. Dit is een
gecombineerde **missing access control + unauthenticated mass data export** kwetsbaarheid. De
`userId`-parameter wordt geaccepteerd maar volledig genegeerd — de query retourneert altijd alle
logs ongeacht de meegegeven waarde, wat het geen klassieke IDOR maakt maar een ontbrekende
toegangscontrole.

Bijkomende bevinding: de exportmethode roept `al.getClassName()` aan (r.63), maar deze methode
bestaat niet in `AuditLog.java`. De klasse biedt `getType()` (geeft `Class<?>`) en
`getSimpleTypeName()` (geeft `String`). Dit is een compilatiefout in de huidige implementatie die
eveneens opgelost moet worden.

**Acceptatiecriteria:**

- [ ] `Context.isAuthenticated()` wordt gecontroleerd vóór het verwerken van het request; bij false → HTTP 401
- [ ] `Context.requirePrivilege(AuditLogConstants.PRIV_GET_AUDITLOGS)` wordt gecontroleerd; bij ontbrekend privilege → HTTP 403
- [ ] De `userId`-parameter wordt gevalideerd; export toont alleen logs van/voor de requestende gebruiker tenzij `PRIV_MANAGE_AUDITLOG` aanwezig is
- [ ] `al.getClassName()` vervangen door `al.getSimpleTypeName()` (of `al.getType().getName()`), zodat de methode compileert
- [ ] Unit test aanwezig die anonieme toegang verifieert → verwachte 401
- [ ] Unit test aanwezig die ongeprivilegieerde toegang verifieert → verwachte 403

**Schatting:** 3 story points

---

## Priority 2 – HIGH (behandelen binnen 1 maand, score 12–17)

---

### SEC-02 · Voeg toegangscontrole toe aan showForm()-endpoint

**Risico-ID:** R-02 | **Score:** 16/25 | **NEN 7510:** A.8.3  
**Bestand:** `ViewAuditLogController.java` r.41–49

**Beschrijving:**  
De methode `showForm()` verwerkt alle requests naar `module/auditlog/viewAuditLog.htm` zonder enige authenticatie- of autorisatiecontrole. Elke HTTP-aanroep levert alle auditlogs op, inclusief medische informatie over patiëntmutaties.

**Acceptatiecriteria:**

- [ ] `Context.requirePrivilege(AuditLogConstants.PRIV_GET_AUDITLOGS)` toegevoegd als eerste statement in `showForm()`
- [ ] Niet-ingelogde gebruikers worden omgeleid naar loginpagina (OpenMRS-standaardgedrag na requirePrivilege)
- [ ] Integratie- of unit-test bevestigt dat niet-geauthenticeerde aanroepen worden geblokkeerd

**Schatting:** 2 story points

---

### SEC-03 · Verander standaard auditingstrategie van NONE naar NONE_EXCEPT

**Risico-ID:** R-04 | **Score:** 16/25 | **NEN 7510:** A.8.15  
**Bestand:** `config.xml` r.47–54

**Beschrijving:**  
De standaardwaarde van `auditlog.auditingStrategy` is `NONE`. Na installatie van de module zonder extra configuratie wordt er dus helemaal niets gelogd. Dit is direct in strijd met NEN 7510-2 A.8.15, dat vereist dat relevante beveiligingsgebeurtenissen worden vastgelegd.

**Acceptatiecriteria:**

- [ ] `defaultValue` in `config.xml` gewijzigd naar `NONE_EXCEPT` of `ALL`
- [ ] Installatiedocumentatie (README) bijgewerkt met instructie voor minimaal vereiste configuratie
- [ ] Eventueel: `AuditLogActivator.java` logt een WARNING als strategie na startup nog `NONE` is

**Schatting:** 1 story point

---

### SEC-04 · Voeg READ-actie toe aan Action-enum en implementeer read-logging

**Risico-ID:** R-05 | **Score:** 16/25 | **NEN 7510:** A.8.15  
**Bestand:** `AuditLog.java` r.64–66; `HibernateAuditLogInterceptor.java`

**Beschrijving:**  
De `Action`-enum heeft alleen `CREATED`, `UPDATED` en `DELETED`. Raadpleging van patiëntgegevens (READ) wordt nooit gelogd. NEN 7510-2 A.8.15 vereist expliciet dat ook toegang tot gevoelige gegevens wordt bijgehouden.

**Acceptatiecriteria:**

- [ ] `READ` toegevoegd aan de `Action`-enum in `AuditLog.java`
- [ ] Hibernate `onLoad()`-methode in `HibernateAuditLogInterceptor` geïmplementeerd voor geauditeerde entiteiten
- [ ] Configuratie-optie beschikbaar om READ-logging per entiteitstype in/uit te schakelen (performance-overweging)
- [ ] Liquibase-migratie bijgewerkt als het DB-schema wijzigt
- [ ] Unit test bevestigt dat READ-acties worden gelogd

**Schatting:** 5 story points

---

## Priority 3 – MEDIUM (behandelen binnen 3 maanden, score 6–11)

---

### SEC-05 · Registreer ontbrekende privilege 'View Audit Log' in config.xml

**Risico-ID:** R-03 | **Score:** 9/25 | **NEN 7510:** A.8.3  
**Bestand:** `config.xml`; `AuditLogWebConstants.java` r.21

**Beschrijving:**  
De privilege-string `"View Audit Log"` (in `AuditLogWebConstants.PRIV_VIEW_AUDITLOG`) wordt gebruikt in `DWRAuditLogService` maar is nooit geregistreerd als `<privilege>` in `config.xml`. Hierdoor kan deze privilege nooit aan een rol worden toegekend via de OpenMRS-beheerdersinterface.

**Acceptatiecriteria:**

- [ ] `<privilege><name>View Audit Log</name>...</privilege>` toegevoegd aan `config.xml`
- [ ] Alle privileges die in de codebase worden gebruikt zijn aantoonbaar geregistreerd in `config.xml`
- [ ] Handmatige verificatie: privilege verschijnt in OpenMRS Admin > Manage Privileges

**Schatting:** 1 story point

---

### SEC-06 · Implementeer write-once bescherming voor AuditLog-entries

**Risico-ID:** R-06 | **Score:** 10/25 | **NEN 7510:** A.8.15  
**Bestand:** `AuditLog.java` (setters); `AuditLog.hbm.xml`

**Beschrijving:**  
Bestaande auditlog-entries zijn volledig muteerbaar: `setAction()`, `setUser()`, `setDateCreated()` etc. hebben geen bescherming. Een gebruiker met voldoende rechten kan log-entries aanpassen of verwijderen, waarmee forensisch bewijsmateriaal wordt vernietigd.

**Acceptatiecriteria:**

- [ ] Optie A: Setters verwijderd of `@Deprecated` gemarkeerd voor immutable velden na persistentie
- [ ] Optie B: Database-niveau constraint `INSERT ONLY` via Hibernate `@Immutable` annotatie op de `AuditLog`-klasse
- [ ] Optie C: Hashketen geïmplementeerd (elke entry bevat hash van vorige entry)
- [ ] Minimaal optie A of B geïmplementeerd en getest
- [ ] Test bevestigt dat wijziging van een bestaande log-entry een exception gooit of niet persisteert

**Schatting:** 3 story points

---

### SEC-07 · Voeg IP-adres en sessie-ID toe aan AuditLog-entity

**Risico-ID:** R-08 | **Score:** 9/25 | **NEN 7510:** A.8.15  
**Bestand:** `AuditLog.java`; `HibernateAuditLogInterceptor.java`; `liquibase.xml`

**Beschrijving:**  
Log-entries bevatten geen netwerkcontext. Het is onmogelijk te achterhalen vanaf welk apparaat of IP-adres een wijziging is doorgevoerd. NEN 7510-2 A.8.15 vereist dat logrecords voldoende context bevatten voor forensisch onderzoek.

**Acceptatiecriteria:**

- [ ] Velden `ipAddress` (String) en `sessionId` (String) toegevoegd aan `AuditLog.java`
- [ ] Liquibase-migratie aangemaakt voor nieuwe kolommen
- [ ] `HibernateAuditLogInterceptor` vult deze velden via `WebConstants.OPENMRS_CLIENT_IP` of HttpServletRequest
- [ ] AuditLog.hbm.xml bijgewerkt met nieuwe veldmapping

**Schatting:** 3 story points

---

### SEC-08 · Behandel unauthenticated/daemon-operaties expliciet in interceptor

**Risico-ID:** R-07 | **Score:** 6/25 | **NEN 7510:** A.8.5  
**Bestand:** `HibernateAuditLogInterceptor.java` r.381–471 (TODO-comment)

**Beschrijving:**  
Operaties uitgevoerd door niet-ingelogde gebruikers of daemon-processen worden stilzwijgend verwerkt met `user=null`. De TODO-comment in de code bevestigt dat dit een bekend onopgelost probleem is. Logs zonder eigenaar hebben geen forensische waarde.

**Acceptatiecriteria:**

- [ ] Bij `Context.getAuthenticatedUser() == null`: keuze implementeren tussen (a) blokkeren van log-entry, (b) markeren als `DAEMON` via speciale systeemgebruiker, of (c) aparte log-categorie
- [ ] TODO-comment vervangen door daadwerkelijke implementatie
- [ ] Keuze gedocumenteerd in code-comment met onderbouwing

**Schatting:** 2 story points

---

## Priority 4 – LOW (accepteren; opnemen in risicoregister)

---

### SEC-09 · Onderzoek en documenteer risico van directe DB-bypass

**Risico-ID:** R-09 | **Score:** 8/25 | **NEN 7510:** A.8.15  
**Bestand:** `HibernateAuditLogInterceptor.java` (javadoc)

**Beschrijving:**  
Directe databasewijzigingen (SQL-scripts, DB-admin tools, ETL) omzeilen de Hibernate-interceptor volledig en worden niet gelogd. Dit is een architecturele beperking die erkend is in de javadoc. Volledig opheffen is niet realistisch zonder database-triggers.

**Acceptatiecriteria:**

- [ ] Risicoregistratie: bevinding gedocumenteerd als geaccepteerd restrisico met onderbouwing
- [ ] Compenserende maatregel: DB-audit op databaseniveau (bijv. MySQL binary log monitoring) aanbevolen in installatieguide
- [ ] Optioneel: database-trigger proof-of-concept geëvalueerd

**Schatting:** 1 story point

---

### SEC-10 · Upgrade of risicoregistratie OpenMRS v1.8.3 (verouderd platform)

**Risico-ID:** R-10 | **Score:** 9/25 | **NEN 7510:** A.8.8  
**Bestand:** `pom.xml` (`openMRSVersion=1.8.3`)

**Beschrijving:**  
Het project draait op OpenMRS 1.8.3, een versie die niet langer actief wordt onderhouden. Bekende CVE's in het core-platform of dependencies (bijv. Spring, Hibernate) kunnen van toepassing zijn en worden niet meer gepatcht door de upstream.

**Acceptatiecriteria:**

- [ ] SBOM-scan uitgevoerd op dependencies
- [ ] CVE's voor OpenMRS 1.8.3 en bijbehorende dependencies gedocumenteerd in risicoregister
- [ ] Go/no-go besluit voor upgrade naar ondersteunde OpenMRS-versie gedocumenteerd met kostenraming

**Schatting:** 2 story points

---

## Backlog-samenvatting

| ID     | Titel (kort)                                      | Prioriteit  | Score |    SP     |
| ------ | ------------------------------------------------- | ----------- | :---: | :-------: |
| SEC-01 | Beveilig exportAuditLogs() endpoint               | 🔴 Critical |  20   |     3     |
| SEC-02 | Toegangscontrole showForm()                       | 🟠 High     |  16   |     2     |
| SEC-03 | Wijzig default auditingstrategie NONE→NONE_EXCEPT | 🟠 High     |  16   |     1     |
| SEC-04 | Implementeer READ-logging                         | 🟠 High     |  16   |     5     |
| SEC-05 | Registreer View Audit Log privilege               | 🟡 Medium   |   9   |     1     |
| SEC-06 | Write-once bescherming AuditLog-entries           | 🟡 Medium   |  10   |     3     |
| SEC-07 | IP-adres en sessie-ID in log-entries              | 🟡 Medium   |   9   |     3     |
| SEC-08 | Behandel daemon/unauthenticated operaties         | 🟡 Medium   |   6   |     2     |
| SEC-09 | Documenteer DB-bypass restrisico                  | 🟢 Low      |   8   |     1     |
| SEC-10 | Upgrade/risicoregistratie OpenMRS v1.8.3          | 🟢 Low      |   9   |     2     |
|        | **Totaal**                                        |             |       | **23 SP** |
