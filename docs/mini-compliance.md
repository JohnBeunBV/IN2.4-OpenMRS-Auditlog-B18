# 02 — Pipeline-complianceverslag NEN 7510-2:2024

## Groep B18 · openmrs-module-auditlog v1.1-SNAPSHOT

|                    |                                                           |
| ------------------ | --------------------------------------------------------- |
| **Opgesteld door** | Groep B18 — JohnBeunBV / IN2.4-OpenMRS-Auditlog-B18       |
| **Module**         | openmrs-module-auditlog v1.1-SNAPSHOT                     |
| **Repository**     | https://github.com/JohnBeunBV/IN2.4-OpenMRS-Auditlog-B18  |
| **Normreferentie** | NEN 7510-2:2024 (≡ ISO/IEC 27002:2022 + zorgtoepassingen) |
| **Datum**          | 04 juni 2026                                              |
| **Status**         | Concept                                                   |

---

## 1. Inleiding

Dit document beschrijft hoe de beveiligingsmaatregelen op de GitHub-repository van groep B18
aantoonbaar voldoen aan de NEN 7510-2:2024-controls. De structuur per rij is:

> **NEN 7510-2 control → concrete GitHub/pipeline-maatregel → bewijs → compliance-status**

De maatregelen zijn onderverdeeld in drie categorieën die overeenkomen met de ingerichte
GitHub-beveiliging:

| Categorie                     | Maatregelen                                                                                                                     |
| ----------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| **A — GitHub Security-tab**   | Security advisories, Dependabot, Code scanning, Secret scanning, Code quality, Security policy, Private vulnerability reporting |
| **B — Repository-governance** | Signing keys (commit-ondertekening), 2FA (tweefactorauthenticatie), Teams-rollen (RBAC)                                         |
| **C — Resterende gaps**       | Controls die nog niet worden afgedekt door bovenstaande maatregelen                                                             |

**Legenda status:**
| Symbool | Betekenis |
|---------|-----------|
| ✅ | Maatregel actief en aantoonbaar |
| ⚠️ | Deels ingericht — aanvullende actie nodig |
| ❌ | Ontbreekt — direct actie vereist |

---

## 2. Categorie A — GitHub Security-tab

### 2.1 Dependabot alerts — **Ingeschakeld** ✅

**Wat het doet:** GitHub scant automatisch `pom.xml` op bekende CVE's in afhankelijkheden
en meldt dit als alert.

| NEN 7510-2:2024 | Titel                                | Koppeling                                                                                                                                     | Status |
| --------------- | ------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------- | ------ |
| **§8.8**        | Beheer van technische kwetsbaarheden | Dependabot detecteert kwetsbare libraries (bijv. verouderde Hibernate, Spring) in de Maven-dependency-tree automatisch vóór productie-release | ✅     |
| **§8.29**       | Beveiligingstesten bij ontwikkeling  | Dependabot-alerts fungeren als een continue SAST-component gericht op third-party componenten                                                 | ✅     |

**Bewijs:** [Dependabot alerts](https://github.com/JohnBeunBV/IN2.4-OpenMRS-Auditlog-B18/security/dependabot)
— alerts zichtbaar in Security-tab van de repository.

**Resterende gap:** Dependabot doet _geen_ automatische pull-requests voor fixes (`dependabot.yml`
ontbreekt). Aanbeveling: `dependabot.yml` toevoegen zodat updates ook als PR worden aangemaakt.

---

### 2.2 Code scanning alerts — **Ingeschakeld** ✅

**Wat het doet:** GitHub CodeQL analyseert de Java-broncode op bekende kwetsbaarheidspatronen
(CWE-klassen) bij elke push naar de hoofdbranch.

| NEN 7510-2:2024 | Titel                                              | Koppeling                                                                                                                                                      | Status |
| --------------- | -------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------ |
| **§8.28**       | Beveiligde codering                                | CodeQL detecteert onveilige codepatronen, waaronder de gevonden kwetsbaarheid: ontbrekende autorisatie op `exportAuditLogs()` in `ViewAuditLogController.java` | ✅     |
| **§8.29**       | Beveiligingstesten bij ontwikkeling en aanvaarding | Code scanning levert aantoonbaar SAST-bewijs als onderdeel van het SSDLC-proces                                                                                | ✅     |
| **§8.26**       | Beveiligingsvereisten voor applicaties             | Automatische bewaking dat beveiligingsvereisten (geen onbeschermde endpoints) niet worden geschonden bij nieuwe code                                           | ✅     |

**Bewijs:** [Code scanning alerts](https://github.com/JohnBeunBV/IN2.4-OpenMRS-Auditlog-B18/security/code-scanning)
— scan-resultaten per commit traceerbaar.

---

### 2.3 Secret scanning alerts — **Ingeschakeld** ✅

**Wat het doet:** GitHub scant alle commits op hardcoded tokens, wachtwoorden, API-sleutels
en certificaten. Bij detectie wordt onmiddellijk een alert gegenereerd.

| NEN 7510-2:2024 | Titel                              | Koppeling                                                                                                        | Status |
| --------------- | ---------------------------------- | ---------------------------------------------------------------------------------------------------------------- | ------ |
| **§8.12**       | Preventie van gegevenslekken (DLP) | Hardcoded credentials in broncode zijn een primaire oorzaak van datalekken; secret scanning blokkeert dit kanaal | ✅     |
| **§8.24**       | Gebruik van cryptografie           | Sleutelbeheer vereist dat private keys nooit in versiebeheer terechtkomen; secret scanning bewaakt dit actief    | ✅     |
| **§5.14**       | Informatie-overdracht              | Voorkomt dat gevoelige configuratie (DB-wachtwoorden, API-tokens) onbedoeld via repository wordt gedeeld         | ✅     |

**Bewijs:** [Secret scanning alerts](https://github.com/JohnBeunBV/IN2.4-OpenMRS-Auditlog-B18/security/secret-scanning)

---

### 2.4 Security advisories — **Ingeschakeld** ✅

**Wat het doet:** Het team kan officiële CVE-advisories aanmaken voor kwetsbaarheden die
worden gevonden in de module. Advisories zijn privé zichtbaar totdat ze worden gepubliceerd
(responsible disclosure).

| NEN 7510-2:2024 | Titel                                                          | Koppeling                                                                                                                     | Status |
| --------------- | -------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------- | ------ |
| **§5.7**        | Informatie over dreigingen                                     | Security advisories zijn het formele kanaal voor het vastleggen en delen van dreigingsinformatie specifiek voor deze module   | ✅     |
| **§8.8**        | Beheer van technische kwetsbaarheden                           | Advisories documenteren kwetsbaarheden gestructureerd (CVSS-score, CWE, patch-versie) conform vulnerability-management-proces | ✅     |
| **§5.24**       | Planning en voorbereiding van informatiebeveiligingsincidenten | Gepubliceerde advisory = formeel incident-record met tijdlijn en mitigatie                                                    | ✅     |

**Bewijs:** [Security advisories](https://github.com/JohnBeunBV/IN2.4-OpenMRS-Auditlog-B18/security/advisories)

---

### 2.5 Code quality findings — **Ingeschakeld** ✅

**Wat het doet:** GitHub detecteert automatisch codewaliteitsproblemen (dode code,
complexiteitsdrempels, antipatronen) als onderdeel van de CI-pipeline.

| NEN 7510-2:2024 | Titel                                | Koppeling                                                                                                             | Status |
| --------------- | ------------------------------------ | --------------------------------------------------------------------------------------------------------------------- | ------ |
| **§8.28**       | Beveiligde codering                  | Codekwaliteit correleert direct met veiligheid: complexe, onleesbare code is moeilijker te reviewen op kwetsbaarheden | ✅     |
| **§8.25**       | Beveiligde ontwikkelingslevenscyclus | Code-quality-gates als verplichte drempel in de SSDLC maken beveiligde ontwikkeling structureel afdwingbaar           | ✅     |

---

### 2.6 Security policy — **Uitgeschakeld** ❌

**Wat het zou doen:** Een `SECURITY.md`-bestand in de repository dat beschrijft hoe externe
gebruikers een beveiligingslek kunnen melden (responsible disclosure-beleid).

| NEN 7510-2:2024 | Titel                                               | Koppeling                                                                                                       | Status |
| --------------- | --------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- | ------ |
| **§5.2**        | Informatiebeveiligingsbeleid                        | Een security policy is het aantoonbare beleidsdocument dat NEN 7510 vereist voor het melden van kwetsbaarheden  | ❌     |
| **§6.4**        | Rapportage van informatiebeveiligingsgebeurtenissen | Zonder policy weten externe melders niet naar wie, hoe en via welk kanaal zij kwetsbaarheden moeten rapporteren | ❌     |

**Actie:** Maak `SECURITY.md` aan in de root van de repository met minimaal:

- Ondersteunde versies
- Meldkanaal (e-mailadres of GitHub-formulier)
- Verwachte reactietijd
- Responsible disclosure-termijn (bijv. 90 dagen)

---

### 2.7 Private vulnerability reporting — **Uitgeschakeld** ❌

**Wat het zou doen:** Externe onderzoekers kunnen een kwetsbaarheid privé melden via GitHub
zonder dat dit publiek zichtbaar wordt — directe koppeling met security advisories.

| NEN 7510-2:2024 | Titel                                               | Koppeling                                                                                                                          | Status |
| --------------- | --------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- | ------ |
| **§6.4**        | Rapportage van informatiebeveiligingsgebeurtenissen | NEN 7510 vereist een formeel, laagdrempelig meldkanaal voor beveiligingsgebeurtenissen; private reporting is precies dat kanaal    | ❌     |
| **§5.6**        | Contact met speciale belangengroepen                | Beveiligingsonderzoekers zijn een externe belangengroep waarmee contact onderhouden moet worden; private reporting faciliteert dit | ❌     |

**Actie:** Activeer _Private vulnerability reporting_ in de Security-tab
(Settings → Security → Private vulnerability reporting → Enable).
Kost geen extra configuratie en sluit direct aan op de reeds ingeschakelde Security advisories.

---

## 3. Categorie B — Repository-governance

### 3.1 Signing keys voor commits — **Ingeschakeld** ✅

**Wat het doet:** Elke commit is cryptografisch ondertekend met de GPG- of SSH-sleutel van
de ontwikkelaar. GitHub markeert ondertekende commits als **Verified**. Niet-ondertekende
commits worden geblokkeerd door de branch-protection-regel.

| NEN 7510-2:2024 | Titel                                | Koppeling                                                                                                                                                                          | Status |
| --------------- | ------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------ |
| **§8.24**       | Gebruik van cryptografie             | Commit-signing past asymmetrische cryptografie toe (GPG/SSH) om de identiteit van de indiener onweerlegbaar vast te leggen                                                         | ✅     |
| **§5.33**       | Bescherming van registraties         | Ondertekende commits maken de versiegeschiedenis **tamper-evident**: een gewijzigd commit-object verliest zijn Verified-status, waardoor retroactieve manipulatie detecteerbaar is | ✅     |
| **§8.15**       | Logboekregistratie — non-repudiation | De koppeling commit-hash ↔ cryptografische handtekening ↔ ontwikkelaarsidentiteit levert onweerlegbaarheid (non-repudiation) voor codewijzigingen                                  | ✅     |

**Bewijs:** In GitHub UI — commits in `main` tonen het **Verified**-label. Controleerbaar via:

```
git log --show-signature
```

---

### 3.2 Tweefactorauthenticatie (2FA) — **Ingeschakeld** ✅

**Wat het doet:** Alle leden van de GitHub-organisatie/repository zijn verplicht om
tweefactorauthenticatie te gebruiken voor toegang. Accounts zonder 2FA worden automatisch
uitgesloten van de organisatie.

| NEN 7510-2:2024 | Titel                    | Koppeling                                                                                                                                          | Status |
| --------------- | ------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------- | ------ |
| **§5.17**       | Authenticatie-informatie | NEN 7510 vereist dat toegang tot systemen die (patiënt)gegevens verwerken of beheren wordt beveiligd met sterke authenticatie; 2FA voldoet hieraan | ✅     |
| **§8.5**        | Beveiligde authenticatie | MFA als tweede factor beschermt ook bij gestolen wachtwoord (credential stuffing / phishing)                                                       | ✅     |
| **§5.15**       | Toegangsbeveiliging      | 2FA is een fundamentele laag in het toegangsbeveiligingsbeleid; zonder 2FA heeft een gestolen GitHub-account direct schrijftoegang tot de broncode | ✅     |

**Bewijs:** Organisatie-instellingen → Authentication security → _Require two-factor
authentication for everyone in the organization_ → **Enabled**.

---

### 3.3 Teams-rollen — **Ingeschakeld** ⚠️

**Wat het doet:** Toegang tot de repository is ingedeeld via GitHub Teams met aparte rollen
(Read / Triage / Write / Maintain / Admin). Niet elk teamlid heeft toegang tot elk project.

**Kanttekening bij de implementatie:** De huidige inrichting werkt op basis van
_request-and-approve_ — een gebruiker dient een verzoek in om lid te worden van een team,
en de owner accepteert of weigert dat. Dit is **geen volledig afgedwongen RBAC**: toegang
wordt reactief verleend op verzoek, niet proactief toegewezen op basis van een vastgestelde
rol of functie. Hierdoor bestaat het risico dat een verzoek wordt geaccepteerd zonder formele
toetsing aan het need-to-know-principe.

| NEN 7510-2:2024 | Titel                           | Koppeling                                                                                                                                                                                                                               | Status |
| --------------- | ------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------ |
| **§5.15**       | Toegangsbeveiliging             | Teams begrenzen toegang per project — wie niet in het B18-team zit heeft geen schrijftoegang. De begrenzing is technisch correct, maar de _toelatingsprocedure_ is informeel (ad-hoc acceptatie door owner)                             | ⚠️     |
| **§5.18**       | Toegangsrechten                 | NEN 7510 vereist dat toegangsrechten worden toegekend op basis van een **formeel vastgesteld principe** (least privilege / need-to-know). Request-based toegang zonder gedocumenteerd toetsingscriterium voldoet hier niet volledig aan | ⚠️     |
| **§8.3**        | Beperking van informatietoegang | Studenten/medewerkers buiten het team kunnen de repository niet muteren — de technische scheiding werkt                                                                                                                                 | ✅     |

**Bewijs:** GitHub → Settings → Collaborators and teams — teamstructuur zichtbaar.

**Actie om naar ✅ te komen:** Documenteer een korte toegangsprocedure (bijv. in `CONTRIBUTING.md`
of de projectwiki): wie mag lid worden, wie beoordeelt het verzoek, op basis van welk criterium.
Daarmee is het request-proces formeel verankerd en voldoet het aan §5.18.

---

## 4. Categorie C — Resterende gaps (niet gedekt door GitHub-instellingen)

De onderstaande controls vallen buiten wat de GitHub-beveiligingsinstellingen kunnen afdekken
en vereisen aanpassingen in de broncode of documentatie.

| NEN 7510-2:2024 | Titel                           | Gap                                                                                                                | Vereiste actie                                                                                             | Status |
| --------------- | ------------------------------- | ------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------- | ------ |
| **§8.15**       | Logboekregistratie              | Standaardstrategie = `NONE` in `config.xml:32` — logging staat standaard _uit_                                     | Wijzig default naar `NONE_EXCEPT` met minimumset zorgklassen (Patient, User, Obs)                          | ❌     |
| **§8.15**       | Logboekregistratie              | `storeLastStateOfDeletedItems` = `false` — eindtoestand bij verwijdering niet vastgelegd                           | Wijzig default naar `true` in `config.xml`                                                                 | ❌     |
| **§5.33**       | Bescherming van registraties    | Geen cryptografische integriteitsbeveiliging op logregels — DB-admin kan stilzwijgend mutaties uitvoeren           | Voeg HMAC-veld toe aan `auditlog_audit_log`-tabel of implementeer append-only DB-rechten                   | ❌     |
| **§5.33**       | Bescherming van registraties    | Geen retentiebeleid — logs kunnen via `AuditLogDAO.delete()` worden verwijderd zonder tijdsbeperking               | Documenteer retentietermijn (WGBO: 15 jaar) in `README.md`; voeg verwijderblokkade toe voor logs < 15 jaar | ❌     |
| **§8.11**       | Maskering van gegevens          | `serializedData`-blob kan ongemaskerd PII bevatten (BSN, diagnose, geboortedatum)                                  | Voeg maskeringsfilter toe in `HibernateAuditLogInterceptor` voor geclassificeerde veldnamen                | ❌     |
| **§8.3**        | Beperking van informatietoegang | `exportAuditLogs()` endpoint in `ViewAuditLogController.java:47` mist `@RequireUserPrivilege` — IDOR-kwetsbaarheid | Voeg `@RequireUserPrivilege(privileges = {PRIV_MANAGE_AUDITLOG})` toe                                      | ❌     |
| **§8.17**       | Synchronisatie van klokken      | `DATE_FORMAT` slaat tijdstempels op zonder tijdzone-aanduiding (`AuditLogConstants.java:29`)                       | Wijzig naar `yyyy-MM-dd'T'HH:mm:ss'Z'` (ISO 8601 UTC)                                                      | ⚠️     |

---

## 5. Totaaloverzicht compliance-scores

| NEN 7510-2:2024 | Titel                                  | GitHub-maatregel                                    | Status                 |
| --------------- | -------------------------------------- | --------------------------------------------------- | ---------------------- |
| §5.2            | Informatiebeveiligingsbeleid           | Security policy                                     | ❌ Uitgeschakeld       |
| §5.6            | Contact met speciale belangengroepen   | Private vulnerability reporting                     | ❌ Uitgeschakeld       |
| §5.7            | Informatie over dreigingen             | Security advisories                                 | ✅                     |
| §5.14           | Informatie-overdracht                  | Secret scanning                                     | ✅                     |
| §5.15           | Toegangsbeveiliging                    | 2FA + Teams-rollen                                  | ⚠️ Deels               |
| §5.17           | Authenticatie-informatie               | 2FA                                                 | ✅                     |
| §5.18           | Toegangsrechten                        | Teams-rollen — request-based, niet formeel getoetst | ⚠️ Deels               |
| §5.24           | Voorbereiding incidenten               | Security advisories                                 | ✅                     |
| §5.33           | Bescherming van registraties           | Signing keys + _code-aanpassing vereist_            | ⚠️ Deels               |
| §6.4            | Rapportage beveiligingsgebeurtenissen  | Security policy + Private reporting                 | ❌ Beide uitgeschakeld |
| §8.3            | Beperking van informatietoegang        | Teams-rollen + _code-aanpassing vereist_            | ⚠️ Deels               |
| §8.5            | Beveiligde authenticatie               | 2FA                                                 | ✅                     |
| §8.8            | Beheer van technische kwetsbaarheden   | Dependabot + Security advisories                    | ✅                     |
| §8.11           | Maskering van gegevens                 | — (_code-aanpassing vereist_)                       | ❌                     |
| §8.12           | Preventie van gegevenslekken           | Secret scanning                                     | ✅                     |
| §8.15           | Logboekregistratie                     | Signing keys + _config-aanpassing vereist_          | ⚠️ Deels               |
| §8.17           | Synchronisatie van klokken             | — (_code-aanpassing vereist_)                       | ⚠️ Deels               |
| §8.24           | Gebruik van cryptografie               | Signing keys + Secret scanning                      | ✅                     |
| §8.25           | Beveiligde ontwikkelingslevenscyclus   | Code quality + Code scanning                        | ✅                     |
| §8.26           | Beveiligingsvereisten voor applicaties | Code scanning                                       | ✅                     |
| §8.28           | Beveiligde codering                    | Code scanning + Code quality                        | ✅                     |
| §8.29           | Beveiligingstesten                     | Code scanning + Dependabot                          | ✅                     |

---

## 6. Directe actielijst (prioriteit)

| Prioriteit | Actie                                                                         | NEN-control | Locatie                             |
| ---------- | ----------------------------------------------------------------------------- | ----------- | ----------------------------------- |
| 🔴 Kritiek | Activeer **Private vulnerability reporting** in GitHub Security-tab           | §6.4        | GitHub Settings                     |
| 🔴 Kritiek | Maak `SECURITY.md` aan (responsible disclosure-beleid)                        | §5.2, §6.4  | Repository root                     |
| 🔴 Kritiek | Voeg `@RequireUserPrivilege` toe aan `exportAuditLogs()`                      | §8.3        | `ViewAuditLogController.java:47`    |
| 🟠 Hoog    | Wijzig default auditingstrategie van `NONE` naar `NONE_EXCEPT`                | §8.15       | `config.xml:32`                     |
| 🟠 Hoog    | Activeer `storeLastStateOfDeletedItems` standaard                             | §8.15       | `config.xml`                        |
| 🟡 Middel  | Documenteer retentietermijn 15 jaar (WGBO) in `README.md`                     | §5.33       | `README.md`                         |
| 🟡 Middel  | Voeg PII-maskeringsfilter toe in Hibernate-interceptor                        | §8.11       | `HibernateAuditLogInterceptor.java` |
| 🟡 Middel  | Voeg tijdzone toe aan `DATE_FORMAT` (ISO 8601 UTC)                            | §8.17       | `AuditLogConstants.java:29`         |
| 🟡 Middel  | Documenteer toegangsprocedure voor Teams (wie mag lid worden, wie beoordeelt) | §5.18       | `CONTRIBUTING.md` of projectwiki    |

---

_Einde document — Groep B18 · IN2.4 Software Security Audit · NEN 7510-2:2024_
