# Gap-analyse: openmrs-module-auditlog vs. NEN 7510-2:2024

**Module:** openmrs-module-auditlog v1.1-SNAPSHOT  
**Norm:** NEN 7510-2:2024+A1:2026 (Informatiebeveiliging in de zorg — Deel 2: Beheersmaatregelen)  
**Beoordeeld:** A.8.3 Toegangsbeveiliging, A.8.5 Authenticatie, A.8.15 Logging  
**Datum:** 2026-06-03  
**Beoordelaar:** Groep B18

---

## Leeswijzer

| Status          | Betekenis                                                       |
| --------------- | --------------------------------------------------------------- |
| ✅ Aanwezig     | Maatregel is aantoonbaar geïmplementeerd in de broncode         |
| ⚠️ Gedeeltelijk | Maatregel bestaat, maar is onvolledig of inconsistent toegepast |
| ❌ Afwezig      | Geen implementatie aangetroffen; vereiste ontbreekt geheel      |

---

## A.8.3 — Beperking toegang tot informatie (Information Access Restriction)

> **NEN 7510-2:2024+A1:2026 vereiste:** De toegang tot informatie en andere gerelateerde bedrijfsmiddelen behoort te worden beperkt overeenkomstig het vastgestelde onderwerpspecifieke beleid inzake toegangsbeveiliging.

### Bevindingen

| #   | Bevinding                                                             | Status      | Bestand : regel                       |
| --- | --------------------------------------------------------------------- | ----------- | ------------------------------------- |
| 1   | Vijf privileges gedefinieerd in `config.xml`                          | ✅ Aanwezig | `config.xml` : 88–101                 |
| 2   | DWR-service controleert privilege voor details                        | ✅ Aanwezig | `DWRAuditLogService.java` : 64-128    |
| 3   | `PRIV_VIEW_AUDITLOG` gebruikt maar niet geregistreerd in `config.xml` | ❌ Afwezig  | `AuditLogWebConstants.java` : 21      |
| 4   | `showForm()` heeft geen enkele toegangscontrole                       | ❌ Afwezig  | `ViewAuditLogController.java` : 41–49 |
| 5   | `exportAuditLogs()` heeft geen toegangscontrole (IDOR)                | ❌ Afwezig  | `ViewAuditLogController.java` : 51–64 |

### Bewijs

**Bevinding 1 — Privileges wél gedefinieerd in config.xml (✅)**

```xml
<!-- config.xml  88-111 -->
<privilege>
    <name>Get Audit Logs</name>
    <description>Able to get audit logs</description>
</privilege>
<!-- idem voor: Get Audit Strategy, Get Items, Check For Audited Items, Manage Audit Log -->
```

**Bevinding 2 — DWR-service controleert privilege correct (✅)**

```java
// DWRAuditLogService.java 64-128
public AuditLogDetails getAuditLogDetails(String auditLogUuid) {
    Context.requirePrivilege(AuditLogWebConstants.PRIV_VIEW_AUDITLOG);
    ...
}
```

**Bevinding 3 — `PRIV_VIEW_AUDITLOG` nooit geregistreerd (❌)**

```java
// AuditLogWebConstants.java 21
public static final String PRIV_VIEW_AUDITLOG = "View Audit Log";
```

Deze privilege-string wordt wél gebruikt in `DWRAuditLogService` maar **staat niet als `<privilege>` in `config.xml`**. De privilege kan dus nooit aan een rol worden toegekend, waardoor de controle op regel 55 in de praktijk altijd faalt of wordt genegeerd.

**Bevinding 4 — `showForm()` zonder toegangscontrole (❌)**

```java
// ViewAuditLogController.java 41-49
@RequestMapping(VIEW_AUDIT_LOG_FORM)
public void showForm(ModelMap model) {
    // GEEN Context.requirePrivilege() of isAuthenticated() check
    model.addAttribute("auditLogs",
        Context.getService(AuditLogService.class)
               .getAuditLogs(null, null, null, null, true, null, null));
}
```

Elke HTTP-aanroep naar `module/auditlog/viewAuditLog.htm` levert alle auditlogs op, ongeacht of de gebruiker ingelogd is of de benodigde privilege heeft.

**Bevinding 5 — Export-endpoint zonder toegangscontrole + IDOR (❌)**

```java
// ViewAuditLogController.java 51-64
/**
 * VULNERABILITY: Missing authentication - @RequestMapping has no @RequireUserPrivilege
 * VULNERABILITY: IDOR - any user can export all audit logs including data about other users
 */
@RequestMapping("module/auditlog/exportAuditLog")
public void exportAuditLogs(String userId, HttpServletResponse response) throws IOException {
    response.setContentType("text/csv");
    // Exporteert ALLE auditlogs ongeacht userId of inlogstatus
    Context.getService(AuditLogService.class)
           .getAuditLogs(null, null, null, null, false, null, null)
           .forEach(al -> writer.println(...));
}
```

Er vindt geen authenticatie- of autorisatiecontrole plaats. Elke anonieme aanroep kan de volledige audittrail exporteren als CSV. Dit is tevens een **IDOR** (Insecure Direct Object Reference) omdat de `userId`-parameter niet wordt gevalideerd of gebruikt als filter.

### Huidig vs. gewenst

| Aspect                | Huidig                                  | Gewenst (NEN 7510-2 A.8.3)                              |
| --------------------- | --------------------------------------- | ------------------------------------------------------- |
| Privilege-registratie | Incompleet — `View Audit Log` ontbreekt | Alle gebruikte privileges geregistreerd in `config.xml` |
| Toegang audit-view    | Geen controle                           | `Context.requirePrivilege()` vóór service-aanroep       |
| Toegang export        | Anoniem mogelijk                        | Authenticatie + privilege-check verplicht               |
| Export-filter         | Alle data, ongeacht userId              | Gegevens gefilterd op bevoegdheid aanvrager             |

### Gap (A.8.3)

> **Aanzienlijk.** De helft van de web-endpoints die toegang geven tot (alle) auditlogs heeft geen toegangscontrole. Het bewust geïntroduceerde export-endpoint vormt een ernstige schending van A.8.3: ongeautoriseerde export van de volledige audittrail is mogelijk. Gecombineerd met de niet-geregistreerde privilege maakt dit de beveiligingslaag grotendeels illusoir.

---

## A.8.5 — Beveiligde authenticatie (Secure Authentication)

> **NEN 7510-2:2024+A1:2026 vereiste:** Er behoren beveiligde authenticatietechnologieën en -procedures te worden geïmplementeerd op basis van beperkingen van de toegang tot informatie en het onderwerpspecifieke beleid inzake toegangsbeveiliging.

### Bevindingen

| #   | Bevinding                                                                | Status          | Bestand : regel                               |
| --- | ------------------------------------------------------------------------ | --------------- | --------------------------------------------- |
| 6   | Interceptor registreert de geauthenticeerde gebruiker bij elke log-entry | ✅ Aanwezig     | `HibernateAuditLogInterceptor.java` : 535     |
| 7   | Unauthenticated transacties worden niet geblokkeerd maar stil verwerkt   | ⚠️ Gedeeltelijk | `HibernateAuditLogInterceptor.java` : 381-471 |
| 8   | Export-endpoint vereist geen authenticatie                               | ❌ Afwezig      | `ViewAuditLogController.java` : 56-64         |

### Bewijs

**Bevinding 6 — Gebruiker vastgelegd per log-entry (✅)**

```java
// HibernateAuditLogInterceptor.java : 535
AuditLog auditLog = new AuditLog(
    object.getClass(),
    serializedId,
    action,
    Context.getAuthenticatedUser(), // ← gebruiker opgeslagen
    date.get().peek()
);
```

Elke auditlog-entry koppelt de wijziging aan de op dat moment ingelogde gebruiker. Dit voldoet aan het traceerbaarheids-vereiste van A.8.5.

**Bevinding 7 — Unauthenticated transacties niet afgevangen (⚠️)**

```java
// HibernateAuditLogInterceptor.java : 381-471
try {
    //TODO handle daemon or un authenticated operations
    for (Map.Entry<Object, ...> entry : entityCollectionsMap...) {
        ...
    }
}
```

De `TODO`-comment bevestigt dat operaties door niet-ingelogde gebruikers (daemon-processen of directe DB-aanroepen) wél worden verwerkt maar zonder geldige gebruiker worden opgeslagen. `Context.getAuthenticatedUser()` retourneert `null` in zo'n geval, waardoor logs ontstaan zonder eigenaar — en de traceerbaarheid van de wijziging verloren gaat.

**Bevinding 8 — Export zonder authenticatiecontrole (❌)**

```java
// ViewAuditLogController.java : 56-64
@RequestMapping("module/auditlog/exportAuditLog")
public void exportAuditLogs(String userId, HttpServletResponse response) throws IOException {
    // Geen: Context.isAuthenticated() check
    // Geen: requirePrivilege()
```

### Huidig vs. gewenst

| Aspect                 | Huidig                                  | Gewenst (NEN 7510-2 A.8.5)              |
| ---------------------- | --------------------------------------- | --------------------------------------- |
| Gebruiker per log      | Opgeslagen via `getAuthenticatedUser()` | ✅ Conform                              |
| Daemon/unauthenticated | Verwerkt, `user = null`                 | Detecteren, blokkeren of apart markeren |
| Web-endpoints          | Deels onbeveiligd                       | Elke endpoint vereist geldige sessie    |

### Gap (A.8.5)

> **Matig.** De kern van A.8.5 — de binding van een wijziging aan een geauthenticeerde gebruiker — is aanwezig. De gap zit in de randgevallen: daemon-operaties en directe aanroepen produceren logs zonder gebruiker, en de export-URL omzeilt authenticatie volledig. Dit maakt forensisch onderzoek naar de bron van een wijziging in sommige scenario's onmogelijk.

---

## A.8.15 — Logging (Event Logging)

> **NEN 7510-2:2024+A1:2026 vereiste:** Er behoren logbestanden waarin activiteiten, uitzonderingen, fouten en andere relevante gebeurtenissen worden geregistreerd, te worden geproduceerd, opgeslagen, beschermd en geanalyseerd.

### Bevindingen

| #   | Bevinding                                                           | Status          | Bestand : regel                               |
| --- | ------------------------------------------------------------------- | --------------- | --------------------------------------------- |
| 9   | CREATED, UPDATED en DELETED worden gelogd via Hibernate-interceptor | ✅ Aanwezig     | `HibernateAuditLogInterceptor.java` : 134–248 |
| 10  | Logvelden: type, identifier, actie, gebruiker, tijdstip, versie     | ✅ Aanwezig     | `AuditLog.java` : 38–62                       |
| 11  | READ-acties worden niet gelogd                                      | ❌ Afwezig      | `AuditLog.java` 64-66                         |
| 12  | Standaardstrategie is `NONE` — er wordt standaard niets gelogd      | ❌ Afwezig      | `config.xml` : 47-54                          |
| 13  | Geen IP-adres of sessie-ID vastgelegd                               | ❌ Afwezig      | `AuditLog.java` (ontbrekend veld)             |
| 14  | Auditlogs zijn muteerbaar — geen integriteitsbeveiliging            | ⚠️ Gedeeltelijk | `AuditLog.java` : setters                     |
| 15  | Directe databasetoegang omzeilt logging volledig                    | ⚠️ Gedeeltelijk | `HibernateAuditLogInterceptor.java` (javadoc) |

### Bewijs

**Bevinding 9 — Mutaties correct onderschept (✅)**

```java
// HibernateAuditLogInterceptor.java 134-248
@Override
public boolean onSave(Object entity, ...) {         // INSERT → CREATED
    if (InterceptorUtil.isAudited(entity.getClass())) {
        inserts.get().peek().add(entity);
    }
}
@Override
public boolean onFlushDirty(Object entity, ...) {   // UPDATE → UPDATED
    ...
    updates.get().peek().add(entity);
}
@Override
public void onDelete(Object entity, ...) {           // DELETE → DELETED
    deletes.get().peek().add(entity);
}
```

**Bevinding 10 — Veldstructuur AuditLog-entity (✅)**

```java
// AuditLog.java : 38–62
private Class<?>    type;          // entiteitstype
private Serializable identifier;   // primaire sleutel
private Action      action;        // CREATED | UPDATED | DELETED
private User        user;          // wie
private Date        dateCreated;   // wanneer
private String      openmrsVersion;
private String      moduleVersion;
private Blob        serializedData; // oude/nieuwe waarden
```

**Bevinding 11 — Enum `Action` mist READ (❌)**

```java
// AuditLog.java : 64-66
public enum Action {
    CREATED, UPDATED, DELETED
    // ← READ ontbreekt volledig
}
```

Raadpleging van patiëntgegevens via de applicatie wordt nooit gelogd. Dit is een directe schending van NEN 7510-2 A.8.15, dat vereist dat ook _toegang tot_ (lees: raadpleging van) gegevens wordt bijgehouden.

**Bevinding 12 — Standaard niets gelogd (❌)**

```xml
<!-- config.xml : 47–54 -->
<globalProperty>
    <property>auditlog.auditingStrategy</property>
    <defaultValue>NONE</defaultValue>  <!-- ← bij installatie: geen logging -->
    <description>
        Allowed values are: ALL, ALL_EXCEPT, NONE, NONE_EXCEPT
    </description>
</globalProperty>
```

Na installatie zonder extra configuratie legt de module **geen enkele** wijziging vast. Er is geen documentatie of activeringscheck die beheerders hierop attendeert.

**Bevinding 13 — IP-adres en sessie-ID ontbreken (❌)**

```java
// AuditLog.java — geen van de volgende velden bestaat:
// private String ipAddress;
// private String sessionId;
// private String hostname;
```

NEN 7510-2 A.8.15 vereist dat logregels voldoende context bevatten voor forensisch onderzoek. Het ontbreken van netwerkcontext maakt het onmogelijk om te bepalen vanaf welk apparaat of locatie een wijziging is doorgevoerd.

**Bevinding 14 — Auditlogs zijn muteerbaar (⚠️)**

```java
// AuditLog.java
public void setAction(Action action) { this.action = action; }
public void setUser(User user)       { this.user = user; }
public void setDateCreated(Date d)   { this.dateCreated = d; }
```

Een gebruiker met databasetoegang of voldoende applicatierechten kan bestaande auditlog-entries aanpassen of verwijderen. Er is geen hashketen, write-once mechanisme of integriteitscontrole aanwezig.

**Bevinding 15 — Directe DB-toegang omzeilt logging (⚠️)**

```java
// HibernateAuditLogInterceptor.java : javadoc
/**
 * Any changes/inserts/deletes made to the DB that are not made through
 * the application won't be detected by the module.
 */
```

Dit is een inherente architectuurbeperking die in de javadoc is erkend maar niet gemitigeerd. SQL-injecties, directe DB-scripts of ETL-processen laten geen spoor achter.

### Huidig vs. gewenst

| Aspect           | Huidig                 | Gewenst (NEN 7510-2 A.8.15)                                             |
| ---------------- | ---------------------- | ----------------------------------------------------------------------- |
| Mutaties (C/U/D) | Gelogd via interceptor | ✅ Conform                                                              |
| Raadpleging (R)  | Niet gelogd            | READ-actie toevoegen aan `Action` enum + interceptie                    |
| Standaard actief | `NONE` (inactief)      | Minimaal `NONE_EXCEPT` als veilige standaard; actieve configuratiecheck |
| Netwerkcontext   | Geen IP/sessie         | IP-adres en sessie-ID toevoegen aan `AuditLog`-entity                   |
| Integriteit logs | Volledig muteerbaar    | Write-once tabel of hashketen ter bescherming                           |
| DB-bypass        | Geen mitigatie         | Database-niveau triggers of melding bij directe wijziging               |

### Gap (A.8.15)

> **Aanzienlijk.** De basis-logging (wie veranderde wat, wanneer) is aanwezig, maar de configuratie, volledigheid en integriteit voldoen niet aan A.8.15. De meest kritieke gap is het ontbreken van READ-logging: in een zorgcontext is raadpleging van patiëntgegevens een even relevante beveiligingsgebeurtenis als mutatie. Daarnaast maakt de standaard `NONE`-strategie en de volledig muteerbare datastructuur de module onbetrouwbaar als compliance-instrument.

---

## Samenvatting gap-analyse

| Control                       | Status          | Kritiekste bevinding                                                                    |
| ----------------------------- | --------------- | --------------------------------------------------------------------------------------- |
| **A.8.3** Toegangsbeveiliging | ❌ Onvoldoende  | Export-endpoint volledig onbeveiligd; `View Audit Log` privilege niet geregistreerd     |
| **A.8.5** Authenticatie       | ⚠️ Gedeeltelijk | Unauthenticated transacties produceren logs zonder eigenaar; export vereist geen sessie |
| **A.8.15** Logging            | ⚠️ Gedeeltelijk | Geen READ-logging; standaard NONE; auditlogs muteerbaar                                 |

---

## Aanbevolen acties (actieplan)

| Prioriteit | Actie                                                                      | Norm-referentie |
| ---------- | -------------------------------------------------------------------------- | --------------- |
| 🔴 Kritiek | Verwijder of beveilig `exportAuditLogs()` met `Context.requirePrivilege()` | A.8.3, A.8.5    |
| 🔴 Kritiek | Registreer `View Audit Log` als `<privilege>` in `config.xml`              | A.8.3           |
| 🔴 Kritiek | Voeg `Context.requirePrivilege()` toe aan `showForm()`                     | A.8.3           |
| 🟠 Hoog    | Voeg `READ` toe aan `Action` enum en intercepteer lees-operaties           | A.8.15          |
| 🟠 Hoog    | Verander standaard `auditingStrategy` van `NONE` naar `NONE_EXCEPT`        | A.8.15          |
| 🟡 Middel  | Behandel unauthenticated/daemon operaties expliciet in interceptor         | A.8.5           |
| 🟡 Middel  | Voeg `ipAddress` en `sessionId` toe aan `AuditLog`-entity                  | A.8.15          |
| 🟢 Laag    | Implementeer write-once bescherming voor bestaande log-entries             | A.8.15          |
