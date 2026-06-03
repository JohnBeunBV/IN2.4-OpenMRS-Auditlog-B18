# Module-keuze: `openmrs-module-auditlog`

## Modulegegevens

| Veld             | Waarde                                                                          |
|------------------|---------------------------------------------------------------------------------|
| **Naam**         | openmrs-module-auditlog                                                         |
| **Versie**       | 1.1-SNAPSHOT                                                                    |
| **Platform**     | OpenMRS 1.8.3                                                                   |
| **Broncode**     | https://github.com/openmrs/openmrs-module-auditlog                              |
| **Aangeleverd**  | `openmrs-module-auditlog.zip`          |
| **Taal**         | Primair Java (Maven-project), met XML-configuratie en minimale frontend-code    |

---

## Motivatie voor de keuze

### 1. Optimale balans tussen complexiteit en beheersbaarheid

Bij het kiezen van een module voor een compliance-audit is omvang een kritische factor. Te eenvoudig en er valt weinig te vinden; te groot en de scope wordt onbeheersbaar. De auditlog-module zit precies in het juiste midden.

Met een cyclomatische complexiteit van **633** zijn er voldoende vertakkingen, lussen en condities om zinvolle bevindingen te doen, maar blijft de module volledig doorgrondbaar binnen de beschikbare tijd. De COCOMO-schatting van **7,08 maanden** en een geschatte ontwikkelwaarde van **$174.083** onderstreept dat dit een substantieel, maar goed afgebakend stuk software is.

---

### 2. Security-kritieke functionaliteit: inherent auditgevoelig

De module heeft een unieke eigenschap die hem bijzonder geschikt maakt: **het is zelf een beveiligingscomponent**. De auditlog-module is verantwoordelijk voor het bijhouden van *wie* welke wijziging heeft doorgevoerd in het OpenMRS-patiëntendossier. In een zorginformatiesysteem is dit een kernvereiste voor:

- **Gegevensbescherming (AVG/GDPR)** — aantoonbaarheid van toegang tot persoonsgegevens
- **Zorgcompliance (NEN 7510, HIPAA)** — onweerlegbare audittrails voor patiëntdata
- **Integriteitsborging** — detectie van ongeautoriseerde wijzigingen

Een kwetsbaarheid in een audit-log-module is daarmee tweemaal zo problematisch: het gaat niet alleen om een functionaliteitsfout, maar om het ondermijnen van het mechanisme dat alle andere fouten zichtbaar moet maken. Fouten in de auditlogic kunnen letterlijk sporen van misbruik verbergen.

---

### 3. Gefocuste, homogene codebase

Van de 59 bestanden in de module zijn er **40 Java-bronbestanden** (68%). De rest bestaat uit XML-configuratie (11), JSP-views (3) en een beperkte hoeveelheid CSS/JavaScript. Dit maakt de module uitermate geschikt voor statische analyse (SAST) zonder dat er ruis ontstaat vanuit complexe frontend-logica of gegenereerde code.

De verhouding van **1.834 commentaarregels op 7.445 Java-regels** (~25%) wijst bovendien op een codebase die historisch gedocumenteerd is — wat analyse vergemakkelijkt én opvallende afwijkingen (ontbrekende of misleidende documentatie) beter zichtbaar maakt.

---

### 4. Bewust geïntroduceerde fouten: extra motivatie voor grondig onderzoek

Zoals aangegeven in de opdrachtomschrijving is er met de aangeleverde modules doelbewust "gerommeld" om gegarandeerd issues te introduceren. Juist in een security-gevoelige module als de auditlog maakt dit de kans op impactvolle, betekenisvolle bevindingen aanzienlijk groter. Gebreken in logging, serialisatie, toegangscontrole of data-integriteit zijn in dit domein geen technische curiositeiten — ze vormen directe risico's voor patiëntveiligheid en wettelijke compliance.

---

## Samenvatting

De keuze voor `openmrs-module-auditlog` is gebaseerd op drie pijlers:

> **Scope**: Klein genoeg om grondig te analyseren, groot genoeg voor substantiële bevindingen.  
> **Relevantie**: Als beveiligingscomponent in een zorgsysteem is elke kwetsbaarheid direct impactvol.  
> **Analyseerbaarheid**: Een homogene Java-codebase met goede commentaardichtheid leent zich uitstekend voor SAST en handmatige review.

Deze combinatie maakt de auditlog-module tot de meest verantwoorde keuze voor een gedegen en inhoudelijk sterke compliance-audit.