# GitHub Environments – configuratie-instructies
## openmrs-module-auditlog OTAP

Dit bestand documenteert hoe de drie GitHub Environments aangemaakt
en geconfigureerd moeten worden in de repository-instellingen.

---

## Vereiste Environments

Ga naar **Settings → Environments** en maak onderstaande environments aan.

---

### 1. `development`

| Instelling | Waarde |
|---|---|
| Environment name | `development` |
| Required reviewers | *(geen – automatisch)* |
| Wait timer | 0 minuten |
| Deployment branches | `develop` only |

**Secrets:**

| Secret naam | Beschrijving |
|---|---|
| `DEV_DB_PASSWORD` | Wachtwoord voor de OpenMRS-databasegebruiker (dev) |
| `DEV_DB_ROOT_PASSWORD` | MySQL root-wachtwoord (dev) |

---

### 2. `test`

| Instelling | Waarde |
|---|---|
| Environment name | `test` |
| Required reviewers | *(geen – automatisch na DEV)* |
| Wait timer | 0 minuten |
| Deployment branches | `develop` only |

**Secrets:**

| Secret naam | Beschrijving |
|---|---|
| `TEST_DB_PASSWORD` | Wachtwoord voor de OpenMRS-databasegebruiker (test) |
| `TEST_DB_ROOT_PASSWORD` | MySQL root-wachtwoord (test) |

---

### 3. `production`

| Instelling | Waarde |
|---|---|
| Environment name | `production` |
| **Required reviewers** | **Minimaal 1 teamlid verplicht** |
| Wait timer | 5 minuten (cooling-off) |
| Deployment branches | `main` only |

**Secrets:**

| Secret naam | Beschrijving |
|---|---|
| `PROD_DB_NAME` | Databasenaam productie |
| `PROD_DB_USER` | Databasegebruiker productie |
| `PROD_DB_PASSWORD` | Wachtwoord databasegebruiker productie |
| `PROD_DB_ROOT_PASSWORD` | MySQL root-wachtwoord productie |

> **NEN-7510 A.8.3 / A.9.2:** Productie-secrets worden uitsluitend via
> GitHub Secrets beheerd. Nooit als plaintext in de repository.