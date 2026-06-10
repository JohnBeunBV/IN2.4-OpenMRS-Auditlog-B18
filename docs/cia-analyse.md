# CIA-analyse: openmrs-module-auditlog v1.1-SNAPSHOT

**Project:** IN2.4 – OpenMRS Auditlog Module  
**Groep:** B18  
**Datum:** Juni 2026 (Geactualiseerd)  
**Norm:** NEN 7510-2:2024+A1:2026

---

## 1. Kroonjuwelen (Crown Jewels)

De module draait binnen OpenMRS, een elektronisch medisch dossier (EMD) dat in zorgomgevingen patiëntgegevens beheert. De auditlog-module is daarbinnen verantwoordelijk voor het bijhouden van wie welke wijziging heeft doorgevoerd. De kroonjuwelen zijn daarmee tweeledig: de patiëntgegevens zelf én de integriteit van de audittrail die toezicht op die gegevens mogelijk maakt.

### 1.1 Kroonjuweel 1 – Audittrail (de `auditlog`-tabel)

| Attribuut              | Detail                                                                                                                                                                                                                                                                              |
| ---------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Beschrijving**       | Auditlog van mutaties op OpenMRS-objecten. Voor iedere gebeurtenis wordt vastgelegd welk object is gewijzigd, welke actie is uitgevoerd (`CREATED`, `UPDATED`, `DELETED`), door welke gebruiker, op welk tijdstip en welke auditdata aan de wijziging is gekoppeld.                 |
| **Locatie in code**    | `AuditLog.java` (klasse-definitie), `AuditLog.hbm.xml` (Hibernate-mapping), `liquibase.xml` (databasetabel `auditlog_audit_log`)                                                                                                                                                    |
| **Verwerkte gegevens** | Gebruiker die de mutatie uitvoerde (`User user`), datum/tijd (`Date dateCreated`), objecttype (`Class<?> type`), object-identificatie (`Serializable identifier`), uitgevoerde actie (`Action action`), geserialiseerde auditdata (`Blob serializedData`) met wijzigingsinformatie. |
| **Relevantie**         | De audittrail ondersteunt controleerbaarheid en forensisch onderzoek van wijzigingen binnen het EPD. Manipulatie kan sporen van ongeautoriseerde activiteiten verbergen; ongeautoriseerde inzage kan inzicht geven in wijzigingen aan patiëntgerelateerde gegevens.                 |
| **Referenties**        | `AuditLog.java` r.29–70 (attributen en `Action`-enum), `AuditLog.java` r.54–58 (`serializedData`), `AuditLog.hbm.xml`, `liquibase.xml`                                                                                                                                              |

### 1.2 Kroonjuweel 2 – Patiëntgegevens in geserialiseerde vorm

| Attribuut              | Detail                                                                                                                                                                                                                                                                                                   |
| ---------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Beschrijving**       | Bij wijzigingen (`UPDATE`) worden gewijzigde veldwaarden geserialiseerd en als JSON opgeslagen in het veld `serializedData`. Bij verwijderingen (`DELETE`) kan optioneel de laatste toestand van het object worden opgeslagen, afhankelijk van de configuratie (`GP_STORE_LAST_STATE_OF_DELETED_ITEMS`). |
| **Locatie in code**    | `HibernateAuditLogInterceptor.java` (verzamelen van wijzigingen in `onFlushDirty()`, opslag in `instantiateAuditLog()`); `AuditLog.java` (`serializedData`-attribuut)                                                                                                                                    |
| **Verwerkte gegevens** | Oude en nieuwe veldwaarden van geaudite objecten. Afhankelijk van de auditconfiguratie kunnen dit domeinobjecten zijn zoals `Patient`, `Obs`, `Encounter`, `DrugOrder` en andere OpenMRS-records, inclusief mogelijk medische gegevens.                                                                  |
| **Relevantie**         | De auditlog kan historische objectgegevens bevatten, waaronder eerdere veldwaarden en mogelijk verwijderde records. Ongeautoriseerde toegang tot deze gegevens kan inzicht geven in patiëntgerelateerde informatie die niet meer in de primaire database aanwezig is.                                    |
| **Referenties**        | `HibernateAuditLogInterceptor.java` (`onFlushDirty()`, serialisatie van oude/nieuwe waarden; `instantiateAuditLog()`, opslag in `serializedData`); `AuditLog.java` (`Blob serializedData`); `AuditLogConstants.GP_STORE_LAST_STATE_OF_DELETED_ITEMS`                                                     |

### 1.3 Kroonjuweel 3 – Gebruikers- en sessiecontext

| Attribuut              | Detail                                                                                                                                                                                                                                               |
| ---------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Beschrijving**       | Elke auditlog-entry wordt gekoppeld aan de op dat moment geauthenticeerde OpenMRS-gebruiker via `Context.getAuthenticatedUser()`. Deze koppeling ondersteunt accountabiliteit, herleidbaarheid en forensisch onderzoek naar uitgevoerde wijzigingen. |
| **Locatie in code**    | `HibernateAuditLogInterceptor.java` (`instantiateAuditLog()`, aanroep van `Context.getAuthenticatedUser()`); `AuditLog.java` (`User user`-attribuut en mapping)                                                                                      |
| **Verwerkte gegevens** | Referentie naar de geauthenticeerde OpenMRS-gebruiker (`User user`), gekoppeld aan de auditlog-entry.                                                                                                                                                |
| **Relevantie**         | De gebruikerskoppeling vormt de basis voor het herleiden van wijzigingen naar individuele accounts. Indien auditlog-records achteraf gewijzigd kunnen worden, neemt de betrouwbaarheid en bewijskracht van de audittrail af.                         |
| **Referenties**        | `HibernateAuditLogInterceptor.java` (`new AuditLog(... Context.getAuthenticatedUser() ...)`); `AuditLog.java` (`private User user`, `setUser()`, `setAction()`, `setDateCreated()`)                                                                  |

### 1.4 Kroonjuweel 4 – Privilege- en toegangsconfiguratie

| Attribuut              | Detail                                                                                                                                                                                                                                                                                        |
| ---------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Beschrijving**       | De configuratie van auditstrategieën en privileges bepaalt welke objecttypen worden geaudit en welke rechten beschikbaar zijn voor toegang tot auditlogfunctionaliteit.                                                                                                                       |
| **Locatie in code**    | `config.xml` (global properties voor `auditingStrategy` en privilege-definities); `AuditLogWebConstants.java` (`PRIV_VIEW_AUDITLOG`)                                                                                                                                                          |
| **Verwerkte gegevens** | Configuratiegegevens voor auditing (`ALL`, `ALL_EXCEPT`, `NONE`, `NONE_EXCEPT`) en privilege-definities zoals `Get Audit Logs`, `Manage Audit Log`, `Get Items`, `Get Audit Strategy` en `Check For Audited Items`.                                                                           |
| **Relevantie**         | De standaardwaarde `NONE` betekent dat direct na installatie geen objecten worden geaudit totdat een auditstrategie wordt geconfigureerd. Daarnaast kan een inconsistentie tussen gedefinieerde en gebruikte privileges leiden tot onverwacht autorisatiegedrag rond auditlogfunctionaliteit. |
| **Referenties**        | `config.xml` (global property `auditingStrategy`, defaultwaarde `NONE`; privilege-definities); `AuditLogWebConstants.java` (`PRIV_VIEW_AUDITLOG`); gap-analyse bevinding 3.                                                                                                                   |

---

## 2. CIA-beoordeling per kroonjuweel

De CIA-beoordeling (Confidentialiteit, Integriteit, Beschikbaarheid) is herzien zodat deze aansluit op de geactualiseerde definitie van de kroonjuwelen (1.1 t/m 1.4). De score blijft op schaal 1–3.

| Kroonjuweel                       | Confidentialiteit | Integriteit | Beschikbaarheid | Toelichting                                                                                       |
| --------------------------------- | :---------------: | :---------: | :-------------: | ------------------------------------------------------------------------------------------------- |
| Audittrail (auditlog-tabel)       |       **3**       |    **3**    |      **2**      | Volledige reconstructie van systeemwijzigingen; manipulatie ondermijnt bewijskracht en compliance |
| Patiëntgegevens in serializedData |       **3**       |    **2**    |      **1**      | Kan historische en gevoelige medische data bevatten; AVG/NEN 7510-impact bij uitlek               |
| Gebruikers- en sessiecontext      |       **2**       |    **3**    |      **1**      | Accountabiliteit en forensische herleidbaarheid hangen af van correcte user-context               |
| Privilege- en auditconfiguratie   |       **2**       |    **3**    |      **2**      | Foutieve configuratie kan auditing volledig uitschakelen of rechten verkeerd toekennen            |

---

## 3. Risicocriteria

### 3.1 Risicobereidheid en grenswaarden

Binnen een NEN 7510-2:2024 + AVG gereguleerde zorgcontext geldt een **conservatieve risicohouding**, vooral rond patiëntdata en auditintegriteit.

Risico wordt berekend als: **Risico = Kans × Impact**, waarbij **Kans = Blootstelling × Waarschijnlijkheid**.

- **Blootstelling:** is de kwetsbaarheid aanwezig en bereikbaar vanuit de aanvalspositie?
- **Waarschijnlijkheid:** hoe groot is de kans dat een kwaadwillende de kwetsbaarheid daadwerkelijk benut?
- **Impact:** ernst van de gevolgen voor Confidentialiteit, Integriteit en/of Beschikbaarheid, inclusief financiële schade, schade aan patiëntveiligheid, reputatieschade en juridische gevolgen (AVG-boetes).

| Grenswaarde     | Risicoscore | Actie                                         | Termijn        |
| --------------- | ----------- | --------------------------------------------- | -------------- |
| Acceptatiegrens | ≤ 5         | Accepteren en registreren                     | Normale review |
| Tolerantiegrens | 6–11        | Accepteren met mitigerende maatregel          | ≤ 3 maanden    |
| Behandelgrens   | 12–17       | Mitigatie verplicht                           | ≤ 1 maand      |
| Kritieke grens  | ≥ 18        | Direct ingrijpen / mogelijk systeemrestrictie | ≤ 72 uur       |

**Aanvullende regels:**

1. Confidentialiteit van patiëntdata mag nooit Impact ≥ 4 hebben zonder directe mitigatie.
2. Integriteit van audittrail heeft hoogste prioriteit (compliance-functie).
3. Elke vorm van ongeauthenticeerde toegang tot audit- of patiëntdata wordt automatisch als kritisch beschouwd.

---

### 3.2 Initiële risicobeoordeling (op basis van gap-analyse, threat model en code-scan)

| ID   | Bevinding                                                           | Kans | Impact | Score | Classificatie |
| ---- | ------------------------------------------------------------------- | :--: | :----: | :---: | ------------- |
| R-01 | Export endpoint zonder autorisatiecontrole (missing access control) |  4   |   5    |  20   | 🔴 Kritiek    |
| R-11 | SQL-injectie in `searchAuditLogsByUser()` (CWE-89)                  |  4   |   5    |  21   | 🔴 Kritiek    |
| R-02 | `showForm()` zonder toegangscontrole                                |  4   |   4    |  16   | 🟠 Mitigeren  |
| R-04 | Default auditingStrategy = NONE                                     |  4   |   4    |  16   | 🟠 Mitigeren  |
| R-05 | READ-acties niet gelogd in auditmechanisme                          |  4   |   4    |  16   | 🟠 Mitigeren  |
| R-06 | AuditLog object mutabel via setters                                 |  2   |   5    |  10   | 🟡 Toezicht   |
| R-10 | Verouderde OpenMRS-versie (potentiële CVE-exposure)                 |  3   |   3    |   9   | 🟡 Toezicht   |
| R-03 | Ontbrekende/incorrecte privilege-registratie                        |  3   |   3    |   9   | 🟡 Toezicht   |
| R-08 | Geen IP-adres of sessie-ID in auditrecords                          |  3   |   3    |   9   | 🟡 Toezicht   |
| R-09 | DB-level toegang omzeilt auditmechanisme                            |  2   |   4    |   8   | 🟡 Toezicht   |
| R-07 | System/daemon acties loggen zonder user-context                     |  2   |   3    |   6   | 🟡 Toezicht   |

> **Toelichting R-11:** R-11 scoort hoger dan R-01 vanwege de combinatie van vertrouwelijkheid én integriteitsimpact. De SQL-injectie stelt een aanvaller niet alleen in staat de audittrail ongeautoriseerd uit te lezen (confidentialiteit, gelijk aan R-01), maar ook om log-entries te wijzigen of te wissen (integriteit). Daarmee is de audittrail als forensisch bewijsmiddel volledig onbetrouwbaar te maken, wat in een zorgcontext direct in strijd is met NEN 7510-2 A.8.15 en de AVG-verantwoordingsplicht (art. 5 lid 2). Kans = 4 op basis van: kwetsbaarheid aantoonbaar aanwezig in de codebase (blootstelling hoog) en SQL-injectie behoort tot OWASP Top 10 met breed beschikbare exploittechnieken (waarschijnlijkheid hoog).

---

### 3.3 Bow-tie analyse: SQL-injectie (R-11)

De bow-tie methode brengt zowel de oorzaken (dreigingen links) als de gevolgen (rechts) van het top event in kaart, samen met de barrières die kans en impact reduceren.

**Top event:** SQL-injectie succesvol uitgevoerd via `searchAuditLogsByUser()`

#### Oorzaken & preventieve barrières (kansreductie)

| #   | Oorzaak (dreiging)                                                    | Preventieve barrière                                                               |
| --- | --------------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| 1   | Gebruikersinvoer zonder validatie direct in SQL-string geconcateneerd | Input-validatie: saniteer alle invoer vóór verwerking                              |
| 2   | Gebruik van `createSQLQuery()` met dynamische string-opbouw           | Vervang door Hibernate parameter binding (`setParameter()`) of HQL                 |
| 3   | Geen ORM-laag als beschermende abstractie voor deze query             | Gebruik een query builder die SQL automatisch escapet                              |
| 4   | Te brede databaserechten voor de applicatiegebruiker                  | Verbind met de database via een least-privilege account (geen DROP/DELETE-rechten) |

#### Gevolgen & mitigerende barrières (impactreductie)

| #   | Gevolg                                          | Mitigerende barrière                                                    | CIA-dimensie           |
| --- | ----------------------------------------------- | ----------------------------------------------------------------------- | ---------------------- |
| 1   | Volledige audittrail ongeautoriseerd uitgelezen | Versleuteling van `serializedData` at rest (TDE/CLE)                    | Confidentialiteit      |
| 2   | Auditlog-entries gewijzigd of gewist            | Audit- en loginlogging op databaseniveau inschakelen (MySQL binary log) | Integriteit            |
| 3   | Forensisch bewijs vernietigd na incident        | Versleutelde backup & restore procedure met off-site kopie              | Integriteit            |
| 4   | AVG-meldplicht getriggerd (art. 33)             | Incident response plan activeren; DPA-melding binnen 72 uur             | Juridisch/Operationeel |
| 5   | Reputatieschade zorginstelling                  | Transparante communicatie richting patiënten en toezichthouder          | Operationeel           |

---

## 4. Referenties

| Bestand                             | Relevantie                                                  |
| ----------------------------------- | ----------------------------------------------------------- |
| `AuditLog.java`                     | Definitie auditlog-entiteit en mutabele structuur           |
| `AuditLog.hbm.xml`                  | ORM mapping van audittrail (DB persistentie)                |
| `HibernateAuditLogInterceptor.java` | Generatie auditrecords + user-context + serialisatie        |
| `ViewAuditLogController.java`       | Export- en view endpoints (R-01, R-02)                      |
| `AuditLogServiceImpl.java`          | SQL-injectie kwetsbaarheid `searchAuditLogsByUser()` (R-11) |
| `config.xml`                        | Auditstrategie + privilegeconfiguratie (R-03, R-04)         |
| `AuditLogWebConstants.java`         | Privilege-definities                                        |
| `liquibase.xml`                     | Database schema auditlog tabel                              |
| Gap-analyse document                | Bron voor alle R-01 t/m R-10, R-11 bevindingen              |
| Threat model (B18)                  | Identificatie R-11; SQL-injectie als aanvalsvector          |
| NEN 7510-2:2024+A1:2026             | Normatieve basis (A.8 logging & toegangscontrole)           |
| AVG (art. 33)                       | Meldplicht datalekken                                       |
