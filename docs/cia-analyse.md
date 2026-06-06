# CIA-analyse: openmrs-module-auditlog v1.1-SNAPSHOT
**Project:** IN2.4 – OpenMRS Auditlog Module  
**Groep:** B18  
**Datum:** Juni 2026  
**Norm:** NEN 7510-2:2024+A1:2026

---

## 1. Kroonjuwelen (Crown Jewels)

De module draait binnen OpenMRS, een elektronisch medisch dossier (EMD) dat in zorgomgevingen patiëntgegevens beheert. De auditlog-module is daarbinnen verantwoordelijk voor het bijhouden van *wie* welke wijziging heeft doorgevoerd. De kroonjuwelen zijn daarmee tweeledig: de patiëntgegevens zelf én de integriteit van de audittrail die toezicht op die gegevens mogelijk maakt.

### 1.1 Kroonjuweel 1 – Audittrail (de `auditlog`-tabel)

| Attribuut | Detail |
|-----------|--------|
| **Beschrijving** | Alle log-entries van mutaties (CREATED, UPDATED, DELETED) op OpenMRS-entiteiten, inclusief user-koppeling, timestamp, entiteitstype en geserialiseerde voor/na-waarden. |
| **Locatie in code** | `AuditLog.java` (klasse-definitie), `AuditLog.hbm.xml` (Hibernate-mapping), `liquibase.xml` (DB-schema) |
| **Verwerkte gegevens** | Gebruiker die mutatie uitvoerde (`User user`), datum/tijd (`Date dateCreated`), object-identifier (`Serializable identifier`), actie (`Action action`), geserialiseerde data (`Blob serializedData`) |
| **Relevantie** | Als de audittrail wordt gemanipuleerd of uitgelekt, kunnen sporen van misbruik worden verborgen of worden alle patiëntmutaties inzichtelijk voor onbevoegden. |
| **Referenties** | `AuditLog.java` r.38–62; `AuditLog.hbm.xml`; `liquibase.xml` |

### 1.2 Kroonjuweel 2 – Patiëntgegevens in geserialiseerde vorm

| Attribuut | Detail |
|-----------|--------|
| **Beschrijving** | Bij elke UPDATE of DELETE worden de vorige veldwaarden van het patiëntobject geserialiseerd als JSON in het `serializedData` Blob-veld. Dit kan medische gegevens bevatten (diagnoses, medicatie, labwaarden). |
| **Locatie in code** | `HibernateAuditLogInterceptor.java` r.134–248 (serialisatie bij onFlushDirty/onDelete); `AuditLog.java` r.55 |
| **Verwerkte gegevens** | Afhankelijk van de geconfigureerde auditingstrategie: alle of geselecteerde domeinobjecten inclusief `Patient`, `Obs`, `Encounter`, `DrugOrder` etc. |
| **Relevantie** | Een dump van de auditlog bevat feitelijk een historisch archief van patiëntgegevens, inclusief verwijderde records. |
| **Referenties** | `HibernateAuditLogInterceptor.java` r.395–471; `AuditLogConstants.GP_STORE_LAST_STATE_OF_DELETED_ITEMS` |

### 1.3 Kroonjuweel 3 – Gebruikers- en sessiecontext

| Attribuut | Detail |
|-----------|--------|
| **Beschrijving** | De koppeling tussen acties en de geauthenticeerde gebruiker (`Context.getAuthenticatedUser()`) vormt de basis voor accountabiliteit en forensisch onderzoek. |
| **Locatie in code** | `HibernateAuditLogInterceptor.java` r.535; `DWRAuditLogService.java` r.64–128 |
| **Verwerkte gegevens** | OpenMRS `User`-object (username, userId, rollen/privileges) |
| **Relevantie** | Als de user-koppeling ontbreekt of kan worden gemanipuleerd (bijv. via de mutable setters in `AuditLog.java`), vervalt de bewijskracht van de audittrail volledig. |
| **Referenties** | `AuditLog.java` setUser()/setAction()/setDateCreated(); `HibernateAuditLogInterceptor.java` r.381 (TODO-comment) |

### 1.4 Kroonjuweel 4 – Privilege- en toegangsconfiguratie

| Attribuut | Detail |
|-----------|--------|
| **Beschrijving** | De configuratie van privileges en de `auditingStrategy` bepalen wie toegang heeft tot de auditlog en welke objecten worden gemonitord. |
| **Locatie in code** | `config.xml` r.47–54 (defaultValue NONE), r.88–111 (privileges) |
| **Verwerkte gegevens** | Privilege-definities: `Get Audit Logs`, `Manage Audit Log`, `Get Items` etc. |
| **Relevantie** | De standaardinstelling `NONE` betekent dat na installatie niets wordt gelogd. Onjuiste privilege-configuratie maakt de beveiligingslaag illusoir (zie bevinding 3: `PRIV_VIEW_AUDITLOG` niet geregistreerd in `config.xml`). |
| **Referenties** | `config.xml` r.47–54; `AuditLogWebConstants.java` r.21; gap-analyse bevinding 3 |

---

## 2. CIA-beoordeling per kroonjuweel

De CIA-beoordeling (Confidentialiteit, Integriteit, Beschikbaarheid) wordt gescoord op schaal 1–3:

| Score | Betekenis |
|-------|-----------|
| 3 – Hoog | Ernstige, directe impact op zorgcontinuïteit, patiëntveiligheid of wettelijke compliance |
| 2 – Middel | Significante impact, herstelbaar met inspanning |
| 1 – Laag | Beperkte impact, geen directe gevaren |

| Kroonjuweel | Confidentialiteit | Integriteit | Beschikbaarheid | Toelichting |
|-------------|:-----------------:|:-----------:|:---------------:|-------------|
| Audittrail | **3** | **3** | **2** | Uitlek legt alle historische mutaties bloot; manipulatie vernietigt bewijskracht |
| Patiëntgegevens in serializedData | **3** | **2** | **1** | Bevat medische gegevens; AVG- en NEN 7510-schending bij uitlek |
| Gebruikers-/sessiecontext | **2** | **3** | **1** | Manipulatie ondermijnt accountabiliteit en forensische waarde |
| Privilege-/auditconfiguratie | **2** | **3** | **2** | Onjuiste config → logging uitgeschakeld → alle andere risico's worden onzichtbaar |

---

## 3. Risicocriteria

### 3.1 Scoreschaal

De risicoscore wordt berekend als: **Risicoscore = Kans × Impact**

#### Kansschaal (Likelihood)

| Score | Label | Beschrijving |
|-------|-------|--------------|
| 1 | Onwaarschijnlijk | Vereist fysieke toegang of geavanceerde aanval; geen bekende exploit |
| 2 | Mogelijk | Vereist authenticatie of specifieke kennis van het systeem |
| 3 | Waarschijnlijk | Eenvoudig uitvoerbaar door elke geauthenticeerde gebruiker |
| 4 | Zeer waarschijnlijk | Geen authenticatie vereist; anoniem exploiteerbaar via netwerk |
| 5 | Zeker | Actief misbruik aangetoond of standaardscan vindt de kwetsbaarheid automatisch |

#### Impactschaal (Impact)

| Score | Label | Beschrijving |
|-------|-------|--------------|
| 1 | Verwaarloosbaar | Cosmetisch probleem, geen data-impact |
| 2 | Laag | Beperkte data-exposure, snel herstelbaar |
| 3 | Middel | Data van één gebruiker/patiënt blootgesteld; audit-trail beperkt aangetast |
| 4 | Hoog | Bulk-exposure van patiëntgegevens; audittrail gemanipuleerd; NEN 7510-schending |
| 5 | Kritiek | Volledige audittrail vernietigd of gelekt; patiëntveiligheid direct in gevaar; meldplicht datalekken |

#### Risicoscorematrix

| | **Impact 1** | **Impact 2** | **Impact 3** | **Impact 4** | **Impact 5** |
|---|:---:|:---:|:---:|:---:|:---:|
| **Kans 5** | 5 | 10 | 15 | 20 | **25** |
| **Kans 4** | 4 | 8 | 12 | **16** | **20** |
| **Kans 3** | 3 | 6 | 9 | **12** | **15** |
| **Kans 2** | 2 | 4 | 6 | 8 | **10** |
| **Kans 1** | 1 | 2 | 3 | 4 | 5 |

Kleurlegende: **Groen** (1–5) = Acceptabel · **Geel** (6–11) = Toezicht vereist · **Oranje** (12–17) = Behandelen · **Rood** (18–25) = Onmiddellijk handelen

### 3.2 Risicobereidheid en grenswaarden

De organisatie (zorginstelling die OpenMRS met auditlog-module inzet) opereert in een gereguleerde zorgomgeving onder NEN 7510-2:2024 en de AVG. Dit leidt tot een **lage risicobereidheid** voor risico's die patiëntgegevens of auditintegriteit raken.

| Grenswaarde | Risicoscore | Actie | Tijdshorizon |
|-------------|-------------|-------|--------------|
| **Acceptatiegrens** | ≤ 5 | Accepteren; documenteren in risicoregister | Volgende reviewcyclus |
| **Tolerantiegrens** | 6–11 | Accepteren mits beheersmaatregel gedocumenteerd | Binnen 3 maanden mitigeren |
| **Behandelgrens** | 12–17 | Niet accepteren; mitigatie verplicht | Binnen 1 maand |
| **Kritieke grens** | ≥ 18 | Onmiddellijk behandelen; evt. system shutdown | Direct / binnen 72 uur |

**Aanvullende risicobereidheidsregels (op basis van NEN 7510 en AVG):**

1. **Confidentialiteit patiëntgegevens:** Geen enkel risico met Impact ≥ 4 op confidentialiteit wordt geaccepteerd, ongeacht de kans. Reden: AVG meldplicht datalekken en NEN 7510-2 A.8.3.
2. **Integriteit audittrail:** Risico's die de integriteit van de audittrail aantasten worden nooit geaccepteerd boven score 5. Reden: de audittrail is zelf het compliance-instrument.
3. **Unauthenticated access:** Elk risico waarbij anonieme netwerktoegang tot patiëntdata mogelijk is, wordt ongeacht de score als **kritiek** geclassificeerd (zie bevinding 5: exportAuditLogs IDOR).

### 3.3 Initiële risicobeoordeling op basis van gap-analyse

De onderstaande risico's zijn afgeleid uit de bevindingen in de gap-analyse. Ze dienen als input voor de risicomatrix die door de groepsgenoot wordt opgesteld.

| ID | Bevinding (bron) | Kans | Impact | Score | Classificatie |
|----|-----------------|:----:|:------:|:-----:|---------------|
| R-01 | Export-endpoint anoniem toegankelijk (IDOR) — `ViewAuditLogController.java` r.51–64 | 4 | 5 | **20** | 🔴 Kritiek |
| R-02 | `showForm()` zonder toegangscontrole — `ViewAuditLogController.java` r.41–49 | 4 | 4 | **16** | 🔴 Kritiek |
| R-03 | `PRIV_VIEW_AUDITLOG` niet geregistreerd in `config.xml` | 3 | 3 | **9** | 🟡 Toezicht |
| R-04 | Standaard auditingstrategie = NONE (niets gelogd) — `config.xml` r.47–54 | 4 | 4 | **16** | 🔴 Kritiek |
| R-05 | READ-acties niet gelogd — `AuditLog.java` Action enum | 4 | 4 | **16** | 🔴 Kritiek |
| R-06 | AuditLog volledig muteerbaar (setters zonder bescherming) — `AuditLog.java` | 2 | 5 | **10** | 🟡 Toezicht |
| R-07 | Daemon/unauthenticated operaties produceren `user=null` logs | 2 | 3 | **6** | 🟡 Toezicht |
| R-08 | Geen IP-adres of sessie-ID in log-entries | 3 | 3 | **9** | 🟡 Toezicht |
| R-09 | Directe DB-toegang omzeilt logging volledig (javadoc erkend) | 2 | 4 | **8** | 🟡 Toezicht |
| R-10 | OpenMRS v1.8.3 — verouderd platform, mogelijke bekende CVE's | 3 | 3 | **9** | 🟡 Toezicht |

---

## 4. Referenties

| Bestand | Relevantie |
|---------|-----------|
| `AuditLog.java` | Definitie kroonjuweel 1; mutable setters (R-06) |
| `ViewAuditLogController.java` r.41–64 | R-01 (IDOR export), R-02 (showForm zonder auth) |
| `HibernateAuditLogInterceptor.java` r.381–471, r.535 | Kroonjuweel 2 en 3; R-07 (daemon/null user) |
| `config.xml` r.47–54, r.88–111 | R-03 (ontbrekende privilege), R-04 (NONE default) |
| `AuditLog.java` r.64–66 (Action enum) | R-05 (geen READ) |
| `AuditLogWebConstants.java` r.21 | R-03 (PRIV_VIEW_AUDITLOG) |
| `liquibase.xml` | DB-schema definitie |
| Gap-analyse (gap-analyse.md) | Alle bevindingen 1–15 |
| NEN 7510-2:2024+A1:2026 | A.8.3, A.8.5, A.8.15 |
| AVG art. 33 | Meldplicht datalekken |
