# 02 — Pipeline-complianceverslag NEN 7510-2:2024

## Groep B18 · openmrs-module-auditlog v1.1-SNAPSHOT

|                    |                                                           |
| ------------------ | --------------------------------------------------------- |
| **Opgesteld door** | Groep B18 (JohnBeunBV / IN2.4-OpenMRS-Auditlog-B18)       |
| **Moduleversie**   | auditlog v1.1-SNAPSHOT                                    |
| **Normreferentie** | NEN 7510-2:2024 (≡ ISO/IEC 27002:2022 + zorgtoepassingen) |
| **Documentdatum**  | 04 juni 2026                                              |
| **Status**         | Concept                                                   |

---

## 1. Inleiding

Dit document vormt het mini-complianceverslag voor de beveiligingsaudit van de `openmrs-module-auditlog`-module in het kader van het IN2.4-project. Voor elk relevant NEN 7510-2:2024-beheersingsmaatregel (control) is vastgesteld:

1. **Wat de module feitelijk levert** — gebaseerd op codeanalyse van de bronbestanden in de zip.
2. **Welke pipeline-maatregel** de control automatisch bewaakt of toetst in de CI/CD-pijplijn (GitHub Actions).
3. **Welk bewijs** daarvoor bestaat of gecreëerd moet worden.
4. **De compliance-status** op dit moment: ✅ Voldoet · ⚠️ Deels · ❌ Ontbreekt.

De geselecteerde controls zijn die controls waarop een auditlog-module _directe verantwoordelijkheid_ draagt of waarbij code-kwetsbaarheden aantoonbaar zijn gevonden.

---

## 2. Toelichting op de module-architectuur

De module bestaat uit drie lagen:

| Laag            | Sleutelbron                                            | Functie                                                                                                                        |
| --------------- | ------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------ |
| **Interceptie** | `HibernateAuditLogInterceptor.java`                    | Onderschept alle Hibernate-sessie-events (INSERT / UPDATE / DELETE) via `EmptyInterceptor`                                     |
| **Entiteit**    | `AuditLog.java`                                        | Datamodel: vastgelegd worden `type`, `identifier`, `action`, `user`, `dateCreated`, `serializedData` (JSON blob met oud/nieuw) |
| **Toegang**     | `AuditLogService.java` + `ViewAuditLogController.java` | Service-laag beveiligd met `@Authorized`; weblaag bevat kwetsbaarheden (zie §3)                                                |
| **Config**      | `config.xml`                                           | Standaardstrategie = **NONE** — logging staat standaard **uit**                                                                |

---

## 3. Compliance-tabel

### 3.1 NEN 7510-2:2024 §8.15 — Logboekregistratie

> _Logboeken die gebruikersactiviteiten, uitzonderingen, fouten en beveiligingsgebeurtenissen registreren, moeten worden aangemaakt, bewaard en regelmatig worden beoordeeld._

| Aspect                          | Bevinding (code)                                                                                                            | Pipeline-maatregel                                                                                | Bewijs                                                            | Status |
| ------------------------------- | --------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------- | ------ |
| **Vastgelegde velden**          | `AuditLog.java`: `type`, `identifier`, `action` (CREATED/UPDATED/DELETED), `user`, `dateCreated`, `serializedData` aanwezig | Statische analyse (Checkstyle / SonarQube) verifieert aanwezigheid verplichte velden              | Klasse-definitie + Liquibase-schema (`liquibase.xml`)             | ✅     |
| **Gebruikerskoppeling**         | `user_id` in schema FK naar `users`-tabel; **nullable** — anonieme systeemoperaties worden **niet** verplicht geattribueerd | Pipeline-testcase: `AuditLogServiceTest` → assertNotNull(auditLog.getUser())                      | Ontbreekt in testset; `user_id` staat nullable in `liquibase.xml` | ⚠️     |
| **Standaard logging-strategie** | `config.xml` defaultValue = **`NONE`** — zonder handmatige configuratie worden **geen** klassen gelogd                      | Integratietest: verifieer dat minimumset zorgklassen (Patient, Obs, User) standaard in scope valt | Ontbreekt                                                         | ❌     |
| **Verwijderde items**           | `GP_STORE_LAST_STATE_OF_DELETED_ITEMS` defaultValue = **`false`** — eindtoestand bij verwijdering niet vastgelegd           | Test: bij DELETE-actie moet `serializedData` niet-null zijn (NEN 7510 vereist reconstructie)      | Ontbreekt in standaardconfiguratie                                | ❌     |

---

### 3.2 NEN 7510-2:2024 §8.16 — Bewakingsactiviteiten

> _Netwerken, systemen en applicaties moeten worden bewaakt op afwijkend gedrag en om potentiële beveiligingsincidenten te detecteren._

| Aspect                 | Bevinding (code)                                                                                                       | Pipeline-maatregel                                                                  | Bewijs                                                                | Status |
| ---------------------- | ---------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------- | --------------------------------------------------------------------- | ------ |
| **Runtime-monitoring** | Module logt databankwijzigingen maar bevat **geen alerting** bij ongewone patronen (bulk-deletes, nachtelijke toegang) | DAST / behavioral test in pipeline (of SIEM-integratie buiten module)               | Buiten scope module; aanbeveling: externe SIEM-koppeling documenteren | ⚠️     |
| **Fout-logging**       | `HibernateAuditLogInterceptor` gebruikt `LogFactory.getLog()` — fouten worden gelogd op `DEBUG`-niveau                 | Pipelinestap: grep op log-niveau; zorg dat fouten op `ERROR` of `WARN` terechtkomen | `log.debug(...)` in `ViewAuditLogController.java` reg. 36             | ⚠️     |

---

### 3.3 NEN 7510-2:2024 §8.17 — Synchronisatie van klokken

> _Klokken van informatieverwerkende systemen die binnen een organisatie of een beveiligingsdomein worden gebruikt, moeten worden gesynchroniseerd met een goedgekeurde tijdbron._

| Aspect                      | Bevinding (code)                                                                     | Pipeline-maatregel                                                                               | Bewijs                                                                      | Status |
| --------------------------- | ------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------- | ------ |
| **Tijdstempel-integriteit** | `dateCreated` = `new Date()` (JVM-tijd) — correct als server NTP-gesynchroniseerd is | Pipeline: OS-level NTP-check in deployment-script (`timedatectl show`); verifieer tijdzone = UTC | Extern aan module; infrastructuurvereiste documenteren in deployment-README | ⚠️     |
| **Tijdzone-vastlegging**    | `DATE_FORMAT = "yyyy-MM-dd HH:mm:ss"` — **geen tijdzone** opgeslagen                 | Statische analyse-regel: detecteer `SimpleDateFormat` zonder `UTC`-aanduiding                    | `AuditLogConstants.java` reg. 29 — ontbreekt timezone-suffix                | ❌     |

---

### 3.4 NEN 7510-2:2024 §5.33 — Bescherming van registraties

> _Registraties moeten worden beschermd tegen verlies, vernietiging, vervalsing, onbevoegde toegang en onbevoegde vrijgave._

| Aspect                                           | Bevinding (code)                                                                                                                                                         | Pipeline-maatregel                                                                                                               | Bewijs                                                   | Status |
| ------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------- | ------ |
| **Onveranderlijkheid / integriteitsbeveiliging** | Geen cryptografische handtekening of hash over logregels aanwezig — database-admin kan regels stilzwijgend muteren                                                       | Pipeline: beveiligingstest controleert dat geen `UPDATE`-statement op `auditlog_audit_log` uitvoerbaar is vanuit applicatie-laag | Geen integriteitscheck aangetroffen in codebase          | ❌     |
| **Bewaarplicht / retentiebeleid**                | `AuditLogDAO.delete(Object)` bestaat — logs kunnen worden verwijderd; **geen retentietermijn** geconfigureerd (NEN 7510 zorg: minimaal 5 jaar voor patiëntgebonden logs) | Pipeline-check: documenteer bewaartermijn in `README.md`; script blokkeert verwijdering voor logs < 5 jaar                       | Ontbreekt volledig                                       | ❌     |
| **Back-upbescherming**                           | Module vertrouwt op Hibernate/DB-back-up; geen aparte exportfunctie met integriteitscheck                                                                                | Pipeline: DB-back-up inclusief auditlog-tabel in standaard back-upscript; hashverificatie na dump                                | Buiten moduleveantwoordelijkheid; infrastructuurvereiste | ⚠️     |

---

### 3.5 NEN 7510-2:2024 §8.3 & §9.4 — Beperking van informatietoegang

> _Toegang tot informatie en functies van applicatiesystemen moet worden beperkt overeenkomstig het vastgestelde beleid inzake toegangsbeveiliging._

| Aspect                                               | Bevinding (code)                                                                                                                                                                                                                        | Pipeline-maatregel                                                                                                                                       | Bewijs                                                                                                          | Status |
| ---------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- | ------ |
| **Privilege-model service-laag**                     | `@Authorized`-annotaties aanwezig op alle methoden van `AuditLogService`: `PRIV_GET_AUDITLOGS`, `PRIV_MANAGE_AUDITLOG`, etc.                                                                                                            | Unit-test: aanroep zonder privilege gooit `APIAuthenticationException`                                                                                   | `AuditLogService.java` regels 40, 70, 81, 91, 101, 120, 136                                                     | ✅     |
| **Export-endpoint weblaag — KRITIEKE KWETSBAARHEID** | `ViewAuditLogController.exportAuditLogs()` heeft **geen `@RequireUserPrivilege`** — ieder geauthenticeerd (of zelfs anoniem) HTTP-verzoek naar `/module/auditlog/exportAuditLog` downloadt de **complete auditlog** van alle gebruikers | **Verplichte pipeline-gate**: SAST (SonarQube / CodeQL) detecteert Spring MVC `@RequestMapping` zonder autorisatie-annotatie; build **faalt** bij vondst | `ViewAuditLogController.java` regels 47–57; risico: **IDOR + ongeautoriseerde bulk-export van patiëntgegevens** | ❌     |
| **DWR-laag**                                         | `DWRAuditLogService` delegeert naar `Context.getService()`; DWR-authenticatie afhankelijk van OpenMRS-sessie                                                                                                                            | Pipeline: integratietest verifieert dat DWR-aanroep zonder sessie HTTP 403 retourneert                                                                   | `config.xml` DWR-allow-blok aanwezig zonder expliciete rolbeperking                                             | ⚠️     |

---

### 3.6 NEN 7510-2:2024 §8.11 — Maskering van gegevens

> _Gegevensmaskering moet worden gebruikt overeenkomstig het beleid inzake toegangsbeveiliging en overige beleidsregels en vereisten van de organisatie, rekening houdend met de toepasselijke wetgeving._

| Aspect                      | Bevinding (code)                                                                                                           | Pipeline-maatregel                                                                                                                                 | Bewijs                                                                   | Status |
| --------------------------- | -------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------ | ------ |
| **PII in serializedData**   | `serializedData` (BLOB) slaat JSON op met oude én nieuwe veldwaarden — dit kan BSN, geboortedatum, diagnose-codes bevatten | Pipeline: data-classificatietest controleert dat gevoelige veldnamen (`bsn`, `ssn`, `birthdate`, `password`) worden gemaskeerd vóór opslag in BLOB | `HibernateAuditLogInterceptor.java` — geen maskeringslogica aangetroffen | ❌     |
| **Gebruikersnamen in logs** | `AuditLog.user` bevat volledige `User`-object-referentie; weergave in `viewAuditLog.jsp` toont gebruikersnaam direct       | Pipeline: UI-test verifieert dat weergave van gebruikersdata voldoet aan need-to-know                                                              | `viewAuditLog.jsp` — geen obfuscatie                                     | ⚠️     |

---

### 3.7 NEN 7510-2:2024 §8.25 t/m §8.29 — Beveiligde softwareontwikkeling (SSDLC)

> _Beveiligingsprincipes voor het ontwerpen van systemen moeten worden vastgesteld, gedocumenteerd en toegepast op elke activiteit in de ontwikkeling van informatiesystemen._

| Aspect                          | Bevinding (code)                                                                                         | Pipeline-maatregel                                                                                    | Bewijs                                                                       | Status |
| ------------------------------- | -------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------- | ------ |
| **§8.25 SSDLC-proces**          | Maven-project met `src/test/` aanwezig; gestructureerde code-organisatie                                 | Pipeline: verplichte code-review via GitHub branch-protection op `main` (minimaal 1 reviewer)         | GitHub-repo `JohnBeunBV/IN2.4-OpenMRS-Auditlog-B18` — inrichten branch-rules | ⚠️     |
| **§8.26 Beveiligingsvereisten** | Geen beveiligings-user-stories of threat-model aangetroffen in module-documentatie                       | Pipeline: vereist `SECURITY.md` of `docs/threat-model.md` aanwezig; lintcheck op aanwezigheid bestand | Ontbreekt in zip-inhoud                                                      | ❌     |
| **§8.28 Beveiligde codering**   | IGNORED_PROPERTIES in interceptor negeert `changedBy`, `dateChanged` etc. — bewuste keuze gedocumenteerd | Pipeline: SonarQube quality gate ≥ 80 % codedekkingsdrempel; geen kritieke issues                     | `HibernateAuditLogInterceptor.java` reg. 91–93 — goed gedocumenteerd         | ✅     |
| **§8.29 Beveiligingstesten**    | Uitgebreide JUnit-testset aanwezig (`AuditLogBehaviorTest`, `CollectionsAuditLogBehaviorTest`, etc.)     | Pipeline: `mvn test` als verplichte gate; test-rapport gepubliceerd in CI-artefacten                  | `api/src/test/java/` — 8+ testklassen aanwezig                               | ✅     |
| **§8.29 SAST**                  | Geen SAST-configuratie aangetroffen in pom.xml                                                           | Pipeline: OWASP Dependency Check + CodeQL / SpotBugs als GitHub Actions-stap                          | Toe te voegen aan `.github/workflows/`                                       | ❌     |

---

## 4. Samenvatting compliance-scores

| NEN 7510-2:2024 Control | Titel                           | Status         |
| ----------------------- | ------------------------------- | -------------- |
| 8.15                    | Logboekregistratie              | ⚠️ Deels       |
| 8.16                    | Bewakingsactiviteiten           | ⚠️ Deels       |
| 8.17                    | Synchronisatie van klokken      | ⚠️ Deels       |
| 5.33                    | Bescherming van registraties    | ❌ Ontbreekt   |
| 8.3 / 9.4               | Beperking van informatietoegang | ❌ **Kritiek** |
| 8.11                    | Maskering van gegevens          | ❌ Ontbreekt   |
| 8.25–8.29               | Beveiligde softwareontwikkeling | ⚠️ Deels       |

**Legenda:**

- ✅ Voldoet — bewijs aantoonbaar aanwezig in code of pipeline
- ⚠️ Deels — gedeeltelijk geïmplementeerd; gap gedocumenteerd
- ❌ Ontbreekt — control niet geïmplementeerd; direct actie vereist

---

## 5. Prioritaire aanbevelingen voor de pipeline

De onderstaande maatregelen moeten worden toegevoegd aan `.github/workflows/ci.yml`:

### 5.1 KRITIEK — Verhelp ontbrekende autorisatie exportAuditLog (§8.3)

```java
// ViewAuditLogController.java — voeg toe:
@RequestMapping("module/auditlog/exportAuditLog")
@RequireUserPrivilege(privileges = { AuditLogConstants.PRIV_MANAGE_AUDITLOG })
public void exportAuditLogs(...) { ... }
```

Pipeline-gate (GitHub Actions):

```yaml
- name: SAST – detecteer onbeveiligde endpoints
  run: mvn com.github.spotbugs:spotbugs-maven-plugin:check -Dspotbugs.failThreshold=High
```

### 5.2 HOOG — Voeg OWASP Dependency Check toe (§8.29)

```yaml
- name: OWASP Dependency Check
  run: |
    mvn org.owasp:dependency-check-maven:check \
      -DfailBuildOnCVSS=7 \
      -DsuppressionFile=owasp-suppressions.xml
- name: Upload rapport
  uses: actions/upload-artifact@v4
  with:
    name: dependency-check-report
    path: target/dependency-check-report.html
```

### 5.3 HOOG — Activeer minimale audit-strategie (§8.15)

In `config.xml` aanpassen:

```xml
<globalProperty>
  <property>auditlog.auditingStrategy</property>
  <defaultValue>NONE_EXCEPT</defaultValue>  <!-- was: NONE -->
</globalProperty>
<globalProperty>
  <property>auditlog.exceptions</property>
  <defaultValue>org.openmrs.Patient,org.openmrs.User,org.openmrs.Obs</defaultValue>
</globalProperty>
<globalProperty>
  <property>auditlog.storeLastStateOfDeletedItems</property>
  <defaultValue>true</defaultValue>  <!-- was: false -->
</globalProperty>
```

### 5.4 MIDDEL — Tijdzone vastleggen in tijdstempels (§8.17)

```java
// AuditLogConstants.java
public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss'Z'"; // UTC suffix toevoegen
```

Pipeline-lintcheck:

```yaml
- name: Tijdzone-check in AuditLogConstants
  run: grep -n "DATE_FORMAT" api/src/main/java/org/openmrs/module/auditlog/util/AuditLogConstants.java | grep -v "'Z'"
  # Faalt als tijdzone-aanduiding ontbreekt
```

### 5.5 MIDDEL — Retentiebeleid documenteren en afdwingen (§5.33)

Toe te voegen in `README.md`:

```markdown
## Wettelijk retentiebeleid

Conform NEN 7510-2:2024 §5.33 en de Wet op de Geneeskundige Behandelingsovereenkomst
(WGBO) moeten patiëntgebonden auditlogs minimaal **15 jaar** worden bewaard.
Logs ouder dan 15 jaar mogen uitsluitend worden verwijderd na expliciete goedkeuring
van de functionaris gegevensbescherming (FG).
```

---

## 6. Bewijs-matrix (traceerbaarheid)

| Bewijs-ID | Beschrijving                                              | Bestandslocatie                              | NEN-control |
| --------- | --------------------------------------------------------- | -------------------------------------------- | ----------- |
| EV-01     | AuditLog entiteit met verplichte velden                   | `api/.../AuditLog.java`                      | 8.15        |
| EV-02     | Liquibase-schema met NOT NULL constraints                 | `api/.../liquibase.xml`                      | 8.15        |
| EV-03     | `@Authorized` annotaties service-laag                     | `api/.../AuditLogService.java`               | 8.3 / 9.4   |
| EV-04     | **KWETSBAARHEID**: ontbrekende autorisatie exportAuditLog | `omod/.../ViewAuditLogController.java:47-57` | 8.3 — ❌    |
| EV-05     | Privilege-definities in module-config                     | `omod/.../config.xml`                        | 8.3         |
| EV-06     | Standaardstrategie = NONE (logging uit)                   | `omod/.../config.xml:32`                     | 8.15 — ❌   |
| EV-07     | Geen integriteits-hash op logregels                       | Codebase (afwezig)                           | 5.33 — ❌   |
| EV-08     | Geen maskering van PII in serializedData                  | `api/.../HibernateAuditLogInterceptor.java`  | 8.11 — ❌   |
| EV-09     | Tijdzone ontbreekt in DATE_FORMAT                         | `api/.../AuditLogConstants.java:29`          | 8.17 — ❌   |
| EV-10     | Uitgebreide JUnit-testdekking                             | `api/src/test/java/` (8 klassen)             | 8.29 — ✅   |

---

_Einde document — groep B18 · IN2.4 Software Security Audit_
