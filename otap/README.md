# OTAP-omgeving – openmrs-module-auditlog

> Sprint-taak **5.3** – OTAP-omgeving opzetten

---

## Overzicht

| Omgeving | Branch | Poort | Doel |
|---|---|---|---|
| **O**ntwikkel (DEV) | `develop` | 8080 | Lokaal bouwen & debuggen |
| **T**est | `develop` | 8081 | Integratietests, QA |
| **A**cceptatie | *(zie noot)* | *(extern)* | UAT door stakeholders |
| **P**roductie | `main` | 443 (HTTPS) | Live systeem |

> **Noot:** Acceptatie is op dit moment nog niet aanwezig.

---

## Mapstructuur

```
.
├── .github/
│   ├── workflows/
│   │   └── otap-pipeline.yml     # CI/CD pipeline (build → dev → test → prod)
│   └── environments/
│       └── README.md             # Instructies GitHub Environments
├── docker/
│   ├── dev/
│   │   └── docker-compose.yml    # DEV-omgeving
│   ├── test/
│   │   └── docker-compose.yml    # TEST-omgeving
│   └── prod/
│       └── docker-compose.yml    # PRODUCTIE-omgeving (+ Nginx proxy)
├── config/
│   ├── dev/.env.example          # Sjabloon omgevingsvariabelen DEV
│   ├── test/.env.example         # Sjabloon omgevingsvariabelen TEST
│   └── prod/.env.example         # Sjabloon omgevingsvariabelen PROD
└── .gitignore
```

---

## Snel starten (lokaal DEV)

```bash
# 1. Bouw de module
mvn clean package

# 2. Kopieer .omod naar build/
copy omod/target/auditlog-*.omod build/auditlog.omod

# 3. Maak .env aan op basis van het sjabloon
copy config/dev/.env.example config/dev/.env
# Pas wachtwoorden aan in config/dev/.env

# 4. Start de DEV-omgeving
docker compose --env-file config/dev/.env -f docker/dev/docker-compose.yml up -d

# 5. Open OpenMRS
open http://localhost:8080/openmrs
```

---

## CI/CD-stroom

```
Push naar develop
    │
    ▼
Build & Unit Tests (Maven)
    │
    ├── Geslaagd ──► Deploy DEV ──► Smoke-test ──► Deploy TEST ──► Integratietests
    │
    └── Mislukt  ──► Pipeline stopt, notificatie naar team

Push naar main (via PR + review)
    │
    ▼
Build & Unit Tests
    │
    └── Geslaagd ──► [Handmatige goedkeuring] ──► Deploy PRODUCTIE
```

---

## NEN-7510 verband

| Control | Maatregel in deze OTAP |
|---|---|
| A.8.3 Toegangsbeveiliging | Productie achter Nginx + TLS; DB niet publiek exposed |
| A.8.5 Authenticatie | GitHub Environment secrets; geen plaintext credentials |
| A.9.2 Gebruikersregistratie | GitHub Environments vereisen reviewer voor productie-deploy |
| A.12.1 Wijzigingsbeheer | Alle deploys via CI/CD; geen handmatige deploys in prod |

---
