# Distributed Database Project #127
## Blue-Green Deployment for Schema Migration: "Zero-Downtime Update"

**Student:** Vương Minh Thắng  
**Team:** Hirono  
**Course:** Distributed Database Systems  
**Category:** Cloud-Native Databases (Docker & K8s)

---

## 📖 Overview

This project implements a **zero-downtime schema migration** between two MySQL clusters using the **Blue-Green Deployment Pattern** combined with a custom **Synchronization Bridge**. The system enables an e-commerce service to upgrade from schema V1 (old) to schema V2 (new) — including type changes, column splits, and normalization — without interrupting user traffic.

The Synchronization Bridge implements **Lazy Distributed Replication** (Özsu & Valduriez, Ch.13) using the **Outbox Pattern** for atomic dual-write, with **bidirectional schema translation** between V1 and V2.

---

## 🏗️ Architecture

```
                   ┌─────────────────────────┐
                   │  Kubernetes Service     │
                   │  (Traffic Router)       │
                   └────────┬────────────────┘
                            │
                ┌───────────┴───────────┐
                │                       │
                ▼                       ▼
       ┌────────────────┐      ┌────────────────┐
       │ Blue Service   │      │ Green Service  │
       │ (V1 schema)    │      │ (V2 schema)    │
       │ Port 8081      │      │ Port 8082      │
       └────────┬───────┘      └────────┬───────┘
                │                       │
                ▼                       ▼
       ┌────────────────┐      ┌────────────────┐
       │ MySQL Blue     │      │ MySQL Green    │
       │ catalog_v1     │      │ catalog_v2     │
       │ Port 3307      │      │ Port 3308      │
       └────────┬───────┘      └────────┬───────┘
                │                       │
                │  outbox_events        │  outbox_events
                │                       │
                └───────────┬───────────┘
                            ▼
                  ┌─────────────────────┐
                  │  Sync Bridge        │
                  │  - Outbox Poller    │
                  │  - Schema Translator│
                  │  - ID Mapping       │
                  │  - Replicator       │
                  │  - Loop Prevention  │
                  │  Port 8083          │
                  └─────────────────────┘
```

---

## 🔧 Tech Stack

| Component | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2 |
| Database | MySQL 8.0 (2 instances) |
| ORM | Spring Data JPA + Hibernate 6 |
| Migration | Liquibase |
| Message Queue | RabbitMQ |
| Containerization | Docker Compose |
| Orchestration | Kubernetes (minikube) |
| Build | Maven (multi-module) |

---

## 📁 Project Structure

```
dist-db-project/
├── docker-compose.yml          # 4 containers: 2 MySQL + RabbitMQ + Adminer
├── pom.xml                     # Parent Maven POM
├── db/changelog/               # Liquibase changelogs (V1 & V2)
├── blue-service/               # Spring Boot - V1 schema (port 8081)
├── green-service/              # Spring Boot - V2 schema (port 8082)
└── sync-bridge/                # Spring Boot - Replication middleware (port 8083)
```

---

## 🗃️ Schema Differences (V1 vs V2)

The Sync Bridge handles **9 types of schema mappings** between V1 and V2:

| # | Field V1 (Blue) | Field V2 (Green) | Mapping Type |
|---|---|---|---|
| 1 | `id BIGINT` | `id UUID` | Type change |
| 2 | `price DECIMAL` | `price_amount + currency CHAR(3)` | 1-to-N split |
| 3 | `category VARCHAR` | `category_id BIGINT FK` | Denormalize → Normalize |
| 4 | `brand VARCHAR` | `brand_id BIGINT FK` | Denormalize → Normalize |
| 5 | `stock INT` | `stock_quantity INT` | Rename |
| 6 | `availability_status VARCHAR` | `availability_status ENUM` | String → ENUM |
| 7 | `sku VARCHAR` | `sku VARCHAR UNIQUE` | Constraint added |
| 8 | (not present) | `dimensions JSON` | New field |
| 9 | (not present) | `metadata JSON` | New field |

---

## 🚀 How to Run

### Prerequisites
- Java 17+
- Maven 3.8+
- Docker Desktop
- (Optional) minikube for Kubernetes deployment

### 1. Start infrastructure (MySQL + RabbitMQ + Adminer)

```bash
docker-compose up -d
```

Wait ~30 seconds for MySQL to initialize.

### 2. Verify infrastructure

```bash
docker ps
```

Expected: 4 containers running (`mysql-blue`, `mysql-green`, `rabbitmq`, `adminer`).

### 3. Run Blue Service (port 8081)

```bash
cd blue-service
mvn spring-boot:run
```

Liquibase will auto-create V1 schema (`products`, `outbox_events`, `id_mapping`).

### 4. Run Green Service (port 8082)

```bash
cd green-service
mvn spring-boot:run
```

Liquibase will auto-create V2 schema (`products`, `categories`, `brands`, `outbox_events`, `id_mapping`).

### 5. Run Sync Bridge (port 8083)

```bash
cd sync-bridge
mvn spring-boot:run
```

The bridge will start polling both `outbox_events` tables every 2 seconds.

---

## 🧪 Testing the Replication

### Test 1: Blue → Green (V1 to V2)

Create a product on Blue:

```bash
curl -X POST http://localhost:8081/api/products \
  -H "Content-Type: application/json" \
  -d '{"title":"iPhone 15","price":25000000,"stock":50,"category":"smartphones","brand":"Apple","availabilityStatus":"In Stock"}'
```

Wait 2-3 seconds, then check Green:

```bash
curl http://localhost:8082/api/products
```

You should see the same product appear with V2 schema:
- `id` as UUID
- `priceAmount: 25000000` + `currency: "USD"`
- `category: {id: ..., name: "smartphones"}`
- `availabilityStatus: "IN_STOCK"`

### Test 2: Green → Blue (V2 to V1)

Create a product on Green:

```bash
curl -X POST http://localhost:8082/api/products \
  -H "Content-Type: application/json" \
  -d '{"title":"MacBook Pro","priceAmount":2500,"currency":"USD","stockQuantity":10,"categoryName":"laptops","brandName":"Apple","availabilityStatus":"IN_STOCK"}'
```

Wait 2-3 seconds, then check Blue:

```bash
curl http://localhost:8081/api/products
```

The product appears with V1 schema (`price`, `stock`, `category` as string, `availabilityStatus: "In Stock"`).

---

## 🌐 Useful URLs

| Service | URL | Credentials |
|---|---|---|
| Blue Service API | http://localhost:8081/api/products | - |
| Green Service API | http://localhost:8082/api/products | - |
| Sync Bridge | http://localhost:8083 | - |
| Adminer (DB UI) | http://localhost:8091 | root / root |
| RabbitMQ Console | http://localhost:15672 | admin / admin |

---

## 📊 Monitoring

### Health Endpoints

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
```

### Verify Outbox Events

Login to Adminer with `mysql-blue` server, check tables:
- `products`: business data
- `outbox_events`: each write creates an event (`processed=0` if pending, `1` if synced)
- `id_mapping`: maps Blue `BIGINT` ↔ Green `UUID`

---

## 🔄 How the Synchronization Bridge Works

1. **Application writes to Blue** → MySQL atomically writes to `products` + `outbox_events` (origin=BLUE) in one transaction (Outbox Pattern).
2. **Sync Bridge polls Blue's outbox** every 2 seconds, finds events with `processed=false`.
3. **Schema Translator** converts V1 payload to V2 format (id BIGINT→UUID, price→amount+currency, etc.).
4. **ID Mapping Service** stores `blueId ↔ greenUuid` for future reference.
5. **Replicator** calls `POST /api/products/_replicate` on Green Service.
6. **Green Service** writes to its own `products` table + outbox event with **origin=BLUE** (loop prevention marker).
7. **Sync Bridge polls Green's outbox**, sees `origin=BLUE`, skips replication (avoids infinite loop).

This corresponds to **Lazy Replication with Replication Mediator Service** (Özsu & Valduriez, Ch.13, §13.6).

---

## 📚 Theoretical References

All design choices are justified using **Özsu & Valduriez — *Principles of Distributed Database Systems*, 3rd Edition (Springer, 2011)**.

| Design Choice | Reference |
|---|---|
| Lazy (async) replication over Eager (2PC) | Ch.13 §13.2 |
| Eventual consistency for zero-downtime | Ch.13 §13.1.1 + Ch.12 §12.6 (CAP) |
| Schema mapping V1 ↔ V2 | Ch.4 §4.4 |
| Outbox Pattern (transactional consistency) | Ch.13 §13.2.2 |
| Replication Mediator Service | Ch.13 §13.6 |
| Last-Write-Wins conflict resolution | Ch.13 §13.3.4 |
| Failure handling without blocking | Ch.13 §13.5 + Ch.12 §12.5 |

Detailed analysis is provided in `docs/Analysis_Report.pdf`.

---

## ✅ Project Status

- [x] **Milestone 1**: Docker Compose + Liquibase migrations + Blue/Green CRUD APIs
- [x] **Milestone 2**: Sync Bridge with bidirectional async replication
    - [x] Outbox Poller (polls both databases every 2s)
    - [x] Schema Translator (V1 ↔ V2 bidirectional)
    - [x] ID Mapping Service (BIGINT ↔ UUID)
    - [x] Replicator (REST API calls)
    - [x] Loop Prevention (origin-based filtering)
- [x] **Milestone 3 (in progress)**:
    - [x] Kubernetes deployment (minikube)
        - [x] Dockerize 3 services
        - [x] K8s manifests (Namespace, Deployment, Service)
        - [x] Deploy all 6 pods successfully
    - [ ] Failure scenarios test
    - [ ] Demo video
    - [ ] Analysis Report
---

## 📝 Deliverables

| # | Item | Status |
|---|---|---|
| 1 | Project Proposal | ✅ Submitted |
| 2 | 2-page Design Document | ⏳ In progress |
| 3 | GitHub Repository with README | ✅ This file |
| 4 | Analysis Report (Özsu & Valduriez theory) | ⏳ In progress |
| 5 | Screen recording (3-5 min, failure scenarios) | ⏳ Pending |

---
## ☸️ Kubernetes Deployment

### Prerequisites
- minikube installed
- kubectl installed
- Docker Desktop running

### Deploy

```bash
# Start minikube
minikube start --driver=docker --memory=4096 --cpus=2

# Use minikube's Docker daemon
minikube docker-env | Invoke-Expression

# Build images
docker build -t blue-service:1.0 ./blue-service
docker build -t green-service:1.0 ./green-service
docker build -t sync-bridge:1.0 ./sync-bridge

# Deploy all resources
kubectl apply -f k8s/

# Check pods
kubectl get pods -n distdb

# Get service URLs
minikube service blue-service -n distdb --url
minikube service green-service -n distdb --url
minikube service sync-bridge -n distdb --url
```

### Architecture in K8s
## 👤 Author

**Vương Minh Thắng**  
Distributed Database Systems — Final Project #127