# HibernateAuditLogInterceptor Refactoring - Implementatiehandleiding

## Status

✅ **50% Voltooid** - Basis refactoring afgerond, fijnafstemming nodig

## Voltooide Stappen

### 1. ✅ Imports Schoongemaakt

- Verwijderd: Ongebruikte BufferedReader, InputStreamReader imports
- Verwijderd: Ongebruikte Date, SimpleDateFormat imports
- Verwijderd: Ongebruikte ObjectMapper imports
- Toegevoegd: AuditLogSerializer, HibernateMetadataUtils imports

### 2. ✅ Class-level Refactoring

Origineel:

```java
private ThreadLocal<Stack<HashSet<Object>>> inserts = ...;
private ThreadLocal<Stack<HashSet<Object>>> updates = ...;
// ... 40+ regels ThreadLocal declarations
```

Refactored:

```java
private TransactionAuditState transactionState = new TransactionAuditState();
private AuditLogFactory auditLogFactory = new AuditLogFactory(transactionState);
```

### 3. ✅ afterTransactionBegin() Refactoring

Origineel (15 regels):

```java
public void afterTransactionBegin(Transaction tx) {
    initializeStacksIfNecessary();
    inserts.get().push(new HashSet<Object>());
    updates.get().push(new HashSet<Object>());
    // ... 6 meer pushes
}
```

Refactored (1 regel):

```java
public void afterTransactionBegin(Transaction tx) {
    transactionState.initializeForTransaction();
}
```

### 4. ✅ onSave() Refactoring

Origineel:

```java
inserts.get().peek().add(entity);
```

Refactored:

```java
transactionState.getInserts().add(entity);
```

---

## Resterende Stappen (Moeten nog worden gedaan)

### Stap 1: onFlushDirty() Refactoring

**Locatie**: Regel ~155-220 in HibernateAuditLogInterceptor.java

**Wijzigingen:**

```
Zoek:     `inserts.get().peek().add(entity);`
Vervang:  `transactionState.getInserts().add(entity);`

Zoek:     `updates.get().peek().add(entity);`
Vervang:  `transactionState.getUpdates().add(entity);`

Zoek:     `objectChangesMap.get().peek().put(entity, propertyChangesMap);`
Vervang:  `transactionState.getObjectChangesMap().put(entity, propertyChangesMap);`

Zoek:     `AuditLogUtil.serializeObject(...)`
Vervang:  `AuditLogSerializer.serializeObject(...)`
```

### Stap 2: onDelete() Refactoring

**Locatie**: Regel ~221-235

**Wijzigingen:**

```
Zoek:     `deletes.get().peek().add(entity);`
Vervang:  `transactionState.getDeletes().add(entity);`
```

### Stap 3: onCollectionUpdate() en onCollectionRemove() Refactoring

**Locatie**: Regel ~240-330

**Alle ThreadLocal.get().peek() vervangen met transactionState getter methoden**

**Voorbeelden:**

```
inserts.get().peek()          → transactionState.getInserts()
updates.get().peek()          → transactionState.getUpdates()
deletes.get().peek()          → transactionState.getDeletes()
objectChangesMap.get().peek() → transactionState.getObjectChangesMap()
entityCollectionsMap.get().peek() → transactionState.getEntityCollectionsMap()
ownerUuidChildLogsMap.get().peek() → transactionState.getOwnerUuidChildLogsMap()
childbjectUuidAuditLogMap.get().peek() → transactionState.getChildObjectUuidAuditLogMap()
entityRemovedChildrenMap.get().peek() → transactionState.getEntityRemovedChildrenMap()
date.get().peek() → transactionState.getCurrentDate()
```

### Stap 4: findDirty() Refactoring

**Locatie**: Regel ~331-370

**Wijzigingen:**

```
Zoek:     `entityCollectionsMap.get().peek().get(entity)`
Vervang:  `transactionState.getEntityCollectionsMap().get(entity)`

Zoek:     `entityCollectionsMap.get().peek().put(...)`
Vervang:  `transactionState.getEntityCollectionsMap().put(...)`

Zoek:     `AuditLogUtil.getCollectionPersister(...)`
Vervang:  `HibernateMetadataUtils.getCollectionPersister(...)`
```

### Stap 5: beforeTransactionCompletion() Refactoring

**Locatie**: Regel ~371-480

**Kritieke wijzigingen:**

```
Zoek:     `inserts.get().peek().isEmpty() && updates.get().peek().isEmpty()`
Vervang:  `!transactionState.hasChanges()`

Zoek:     `for (Object insert : inserts.get().peek())`
Vervang:  `for (Object insert : transactionState.getInserts())`

Zoek:     `for (Object update : updates.get().peek())`
Vervang:  `for (Object update : transactionState.getUpdates())`

Zoek:     `for (Object delete : deletes.get().peek())`
Vervang:  `for (Object delete : transactionState.getDeletes())`

Zoek:     `createAuditLogIfNecessary(insert, Action.CREATED)`
Vervang:  `auditLogFactory.createAuditLogIfNecessary(insert, Action.CREATED)`

Zoek:     `createAuditLogIfNecessary(delete, Action.DELETED)`
Vervang:  `auditLogFactory.createAuditLogIfNecessary(delete, Action.DELETED)`

Zoek:     `createAuditLogIfNecessary(update, Action.UPDATED)`
Vervang:  `auditLogFactory.createAuditLogIfNecessary(update, Action.UPDATED)`

Zoek:     `instantiateAuditLog(obj, isInsert ? Action.CREATED : Action.UPDATED)`
Vervang:  `auditLogFactory.instantiateAuditLog(...)`
```

**Cleanup wijzigingen:**

```
Vervang de gehele cleanup sectie:
  inserts.get().pop();
  updates.get().pop();
  // ... 7 meer removes

Met:
  transactionState.cleanup();
```

### Stap 6: Privé methoden Verwijderen

**Verwijder de volgende privé methoden (worden niet meer gebruikt):**

- `createAuditLogIfNecessary()` - Verplaatst naar AuditLogFactory
- `instantiateAuditLog()` - Verplaatst naar AuditLogFactory
- `initializeStacksIfNecessary()` - Verplaatst naar TransactionAuditState
- `removeStacksIfEmpty()` - Verplaatst naar TransactionAuditState
- `handleUpdatedCollection()` - Kan blijven (nog steeds nodig voor collection handling)

### Stap 7: AuditLogSerializer Integratie

**Zoek alle usages van:**

```
AuditLogUtil.serializeObject(...)
AuditLogUtil.serializeToJson(...)
AuditLogUtil.serializeCollectionItems(...)
AuditLogUtil.serializeMapItems(...)
```

**Vervang met:**

```
AuditLogSerializer.serializeObject(...)
AuditLogSerializer.serializeToJson(...)
AuditLogSerializer.serializeCollectionItems(...)
AuditLogSerializer.serializeMapItems(...)
```

---

## Verfijningsprocedure

1. **Open HibernateAuditLogInterceptor.java** in VS Code
2. **Voer stap-voor-stap alle wijzigingen uit** (gebruik Find & Replace)
3. **Compileer het project**: `mvn clean compile`
4. **Voer tests uit**: `mvn test`
5. **Check voor fouten** met Eclipse/IntelliJ
6. **Voer code review uit**
7. **Commit en push** naar repository

---

## Validatie Checklist

Na completion, controleer:

- [ ] Bestand compileert zonder errors
- [ ] Geen ongebruikte ThreadLocal variabelen meer
- [ ] Alle `transactionState.` calls zijn correct
- [ ] Alle `auditLogFactory.` calls zijn correct
- [ ] Alle `AuditLogSerializer.` calls zijn correct
- [ ] Alle `HibernateMetadataUtils.` calls zijn correct
- [ ] Unit tests slagen
- [ ] Integration tests slagen
- [ ] Code review goedkeuring

---

## Expected Outcome

**Voor:**

- HibernateAuditLogInterceptor: ~650 regels
- 40+ ThreadLocal stacks
- 3 grote privé methoden
- Moeilijk te lezen/onderhouden

**Na:**

- HibernateAuditLogInterceptor: ~200 regels
- 0 ThreadLocal stacks
- Schone, gefocussde event handlers
- Duidelijk delegatie naar helper klassen

---

## Handmatige Controle

Controleer na refactoring handmatig:

1. Create een nieuwe AuditLog entry (INSERT)
2. Wijzig een entiteit (UPDATE)
3. Verwijder een entiteit (DELETE)
4. Controleer dat alle logs correct zijn opgeslagen
