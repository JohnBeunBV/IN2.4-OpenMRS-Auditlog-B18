# Bow Tie Analyse — SEC-11: SQL Injection

## Tabel 1 — Oorzaken & Preventieve Barrières

| # | Oorzaak | Preventieve barrière |
|---|---------|----------------------|
| 1 | Geen input-validatie op gebruikersinvoer | Input-validatie: sanitize alle invoer vóór verwerking |
| 2 | Dynamische SQL-queries in stored procedures | Gebruik een ORM of query builder die SQL automatisch escaped |
| 3 | Gebruikersdata direct samengevoegd in SQL-strings | Pas database-specifieke escape functions toe |
| 4 | Geen gebruik van prepared statements / parameterized queries | Verplicht gebruik van parameterized queries / prepared statements |
| 5 | Te brede databaserechten (geen least-privilege) | Verbind met de database via een least-privilege account |

---

## TOP EVENT

> **SQL Injection succesvol uitgevoerd**

---

## Tabel 2 — Gevolgen & Mitigerende Barrières

| # | Gevolg | Mitigerende barrière | Type |
|---|--------|----------------------|------|
| 1 | PII-data blootgesteld | TDE / CLE Encryptie (data at rest) | Informatie |
| 2 | HBI-data blootgesteld | Threat Detection (Azure SQL) | Informatie |
| 3 | Ongeautoriseerde data-extractie | Audit- en loginlogging inschakelen | Informatie |
| 4 | Data-corruptie of verwijdering | Versleutelde backup & restore procedure | Operationeel |
| 5 | Reputatieschade en AVG-boetes | Incident response plan activeren | Operationeel |
