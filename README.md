# 🔄 Blue-Green Deployment for Schema Migration

> **Distributed Database Project #127** — Zero-Downtime Schema Migration with Bidirectional Replication

[![Java](https://img.shields.io/badge/Java-17-orange)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue)](https://www.mysql.com/)
[![Docker](https://img.shields.io/badge/Docker-Compose-blue)](https://docs.docker.com/compose/)
[![Kubernetes](https://img.shields.io/badge/Kubernetes-minikube-326CE5)](https://minikube.sigs.k8s.io/)

---

## 👤 Author

**Vương Minh Thắng** — Hirono Team
_Project Category: Cloud-Native Databases (Docker & K8s)_

---

## 📖 Problem Statement

Trong môi trường production của e-commerce, việc nâng cấp schema database (V1 → V2) thường gây **downtime** vì:

- Schema migration kiểu blocking yêu cầu khóa bảng
- Application code phải sửa đồng thời với schema
- Rollback khó khăn nếu phát hiện bug ở schema mới

**Giải pháp:** Triển khai **Blue-Green Deployment Pattern** kết hợp **Lazy Distributed Replication** (Özsu & Valduriez, Ch.6) để:

- Chạy song song 2 cluster với 2 schema khác nhau (Blue = V1, Green = V2)
- Một **Synchronization Bridge** thực hiện bidirectional async replication
- Schema mapping V1 ↔ V2 (Özsu & Valduriez, Ch.7 §7.1.4)
- Zero-downtime cutover + safe rollback

---

## 🏗️ System Architecture

```
                  ┌─────────────────────────────────────┐
                  │        Kubernetes Cluster           │
                  │                                     │
        ┌─────────┼──────────┐               ┌──────────┼─────────┐
        │  Blue Service      │               │  Green Service     │
        │  (V1 Schema)       │               │  (V2 Schema)       │
        │  Port: 8081        │               │  Port: 8082        │
        └────────┬───────────┘               └────────────┬───────┘
                 │                                        │
                 │ ┌──────────────────────────────────┐   │
                 ▼ ▼                                  ▼   ▼
        ┌─────────────────┐                    ┌─────────────────┐
        │  MySQL Blue     │                    │  MySQL Green    │
        │  catalog_v1     │                    │  catalog_v2     │
        │  + outbox       │                    │  + outbox       │
        └─────────────────┘                    └─────────────────┘
                 ▲                                        ▲
                 │                                        │
                 │       ┌──────────────────────┐         │
                 └───────┤   Sync Bridge        ├─────────┘
                         │   (Outbox Poller +   │
                         │    Schema Translator)│
                         │   Port: 8083         │
                         └──────────────────────┘
                                    │
                                    │
                         ┌──────────▼──────────┐
                         │     RabbitMQ        │
                         │  (Retry Queue)      │
                         └─────────────────────┘
```

### Components

| Service           | Port | Role                                |
| ----------------- | ---- | ----------------------------------- |
| **Blue Service**  | 8081 | REST API với schema V1 (legacy)     |
| **Green Service** | 8082 | REST API với schema V2 (new)        |
| **Sync Bridge**   | 8083 | Bidirectional async replication     |
| **MySQL Blue**    | 3307 | Database V1 + Outbox                |
| **MySQL Green**   | 3308 | Database V2 + Outbox                |
| **RabbitMQ**      | 5672 | Retry queue cho failed replications |
| **Adminer**       | 8091 | Web UI cho database                 |

---

## 🔄 Schema V1 → V2 Mapping (9 transformations)

| #   | V1 Field (Blue)               | V2 Field (Green)           | Transformation                             |
| --- | ----------------------------- | -------------------------- | ------------------------------------------ |
| 1   | `id BIGINT`                   | `id UUID`                  | Type change + ID mapping table             |
| 2   | `price DECIMAL`               | `price_amount + currency`  | Field split (default `USD`)                |
| 3   | `stock`                       | `stock_quantity`           | Field rename                               |
| 4   | `category VARCHAR`            | `category_id FK`           | String → Foreign Key (normalize)           |
| 5   | `brand VARCHAR`               | `brand_id FK`              | String → Foreign Key (normalize)           |
| 6   | `availability_status VARCHAR` | `availability_status ENUM` | Free text → Enum (`In Stock` → `IN_STOCK`) |
| 7   | (no field)                    | `dimensions JSON`          | New optional field                         |
| 8   | (no field)                    | `metadata JSON`            | New optional field                         |
| 9   | `description TEXT`            | (removed)                  | Field deprecated in V2                     |

---

## 🧠 Core Concepts

### 1. Outbox Pattern (Transactional Outbox)

- Mỗi business write vào `products` được kèm 1 row vào `outbox_events` trong **cùng transaction**
- Đảm bảo atomic dual-write: hoặc cả 2 đều thành công, hoặc cả 2 rollback
- Sync Bridge poll `outbox_events` mỗi 2 giây để replicate

### 2. Lazy (Async) Replication

- Theo Özsu & Valduriez Ch.6 §6.3.3 + §6.3.4 (Lazy Centralized + Distributed Protocols)
- Đánh đổi: **Eventual Consistency** thay vì **Strong Consistency**
- Lợi ích: High Availability, không bị block khi 1 cluster down

### 3. Schema Mapping Layer

- Theo Özsu & Valduriez Ch.7 §7.1.4 (Schema Mapping)
- Translator dịch payload V1 → V2 (và ngược lại)
- ID Mapping Service: BIGINT ↔ UUID

### 4. Loop Prevention via Origin Tagging

- Mỗi event có field `origin` (`BLUE` hoặc `GREEN`)
- Sync Bridge chỉ replicate event có `origin` trùng với DB nó đang đọc
- Tránh vòng lặp vô tận

### 5. Bidirectional Replication

- Blue → Green: User write Blue, sync sang Green
- Green → Blue: User write Green, sync ngược về Blue
- Cho phép cả 2 cluster active cùng lúc

---

## 🛠️ Tech Stack

| Layer             | Technology                                        |
| ----------------- | ------------------------------------------------- |
| **Language**      | Java 17                                           |
| **Framework**     | Spring Boot 3.2 (Web, Data JPA)                   |
| **Database**      | MySQL 8.0 (2 instances)                           |
| **Migration**     | Liquibase                                         |
| **Build**         | Maven (multi-module)                              |
| **Container**     | Docker Compose (dev) + Kubernetes minikube (prod) |
| **Message Queue** | RabbitMQ                                          |
| **Boilerplate**   | Lombok                                            |

---

## 📁 Project Structure

```
dist-db-project/
├── docker-compose.yml           # 4 services: mysql-blue, mysql-green, rabbitmq, adminer
├── pom.xml                      # Parent POM (multi-module)
├── README.md
│
├── db/changelog/                # Liquibase migrations
│   ├── v1-changelog.xml         # Schema V1 (Blue)
│   └── v2-changelog.xml         # Schema V2 (Green)
│
├── blue-service/                # Spring Boot service (V1)
│   ├── src/main/java/com/hirono/blue/
│   │   ├── controller/          # REST endpoints
│   │   ├── service/             # Business logic + outbox publisher
│   │   ├── entity/              # JPA entities (Product, OutboxEvent)
│   │   └── repository/
│   ├── Dockerfile
│   └── pom.xml
│
├── green-service/               # Spring Boot service (V2)
│   ├── src/main/java/com/hirono/green/
│   │   ├── controller/
│   │   ├── service/             # createFromReplication() for sync
│   │   ├── entity/              # UUID, FK, Enum, JSON fields
│   │   └── repository/
│   ├── Dockerfile
│   └── pom.xml
│
├── sync-bridge/                 # Bidirectional async replicator
│   ├── src/main/java/com/hirono/bridge/
│   │   ├── config/              # 2 DataSource configs (Blue + Green)
│   │   ├── poller/              # OutboxPoller (@Scheduled every 2s)
│   │   ├── translator/          # SchemaTranslator V1↔V2
│   │   ├── service/             # IdMappingService (BIGINT ↔ UUID)
│   │   ├── replicator/          # Replicator (HTTP calls to target)
│   │   └── entity/
│   ├── Dockerfile
│   └── pom.xml
│
├── k8s/                         # Kubernetes manifests
│   ├── 00-namespace.yaml
│   ├── 01-mysql-blue.yaml
│   ├── 02-mysql-green.yaml
│   ├── 03-rabbitmq.yaml
│   ├── 04-blue-service.yaml
│   ├── 05-green-service.yaml
│   └── 06-sync-bridge.yaml
│
└── demo/                        # Demo video (failure scenarios)
    └── failure-scenarios-demo.mp4
```

---

## 🚀 Quick Start

### Option 1: Docker Compose (Development)

```bash
# Clone repo
git clone <repo-url>
cd dist-db-project

# Build all modules
mvn clean package -DskipTests

# Start infrastructure (MySQL Blue/Green, RabbitMQ, Adminer)
docker-compose up -d

# Run 3 services (in 3 separate terminals)
cd blue-service && mvn spring-boot:run
cd green-service && mvn spring-boot:run
cd sync-bridge && mvn spring-boot:run
```

Access:

- Blue: http://localhost:8081
- Green: http://localhost:8082
- Sync Bridge: http://localhost:8083
- Adminer: http://localhost:8091

### Option 2: Kubernetes (Production)

```bash
# Start minikube
minikube start --driver=docker --memory=4096 --cpus=2

# Use minikube's Docker daemon (Windows PowerShell)
minikube docker-env | Invoke-Expression

# Build images into minikube
docker build -t blue-service:1.0 ./blue-service
docker build -t green-service:1.0 ./green-service
docker build -t sync-bridge:1.0 ./sync-bridge

# Deploy
kubectl apply -f k8s/

# Check pods
kubectl get pods -n distdb

# Get service URLs (must keep terminals open)
minikube service blue-service -n distdb --url
minikube service green-service -n distdb --url
minikube service sync-bridge -n distdb --url
```

---

## 📡 API Examples

### Create product on Blue (V1 schema)

```bash
curl -X POST http://localhost:8081/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "title": "iPhone 15",
    "price": 25000000,
    "stock": 100,
    "category": "smartphones",
    "brand": "Apple",
    "availabilityStatus": "In Stock"
  }'
```

### Verify on Green (V2 schema - auto-replicated after ~2s)

```bash
curl http://localhost:8082/api/products
```

Response shows V2 schema:

```json
{
  "id": "abc-uuid-xyz",
  "title": "iPhone 15",
  "priceAmount": 25000000,
  "currency": "USD",
  "stockQuantity": 100,
  "category": { "id": 14, "name": "smartphones" },
  "brand": { "id": 1, "name": "Apple" },
  "availabilityStatus": "IN_STOCK"
}
```

---

## 🧪 Failure Scenarios (Tested)

5 scenarios demonstrated in [`demo/failure-scenarios-demo.mp4`](demo/failure-scenarios-demo.mp4):

| #   | Scenario                                                | Result                                                                |
| --- | ------------------------------------------------------- | --------------------------------------------------------------------- |
| 1   | **Kill Green pod** mid-operation                        | ✅ K8s self-heals (~20s), pending event replicated after recovery     |
| 2   | **Kill Sync Bridge** during write                       | ✅ Blue accepts writes, outbox persists, Bridge resumes after restart |
| 3   | **Network partition** (scale Bridge=0)                  | ✅ Outbox accumulates events, all replicated when partition heals     |
| 4   | **Concurrent conflict** (both clusters write same time) | ✅ Last-Write-Wins resolution, eventual consistency achieved          |
| 5   | **Rollback** (Green down, route to Blue)                | ✅ Zero downtime, Blue serves alone                                   |

---

## 📚 Theoretical References

Based on **Özsu, M. T., & Valduriez, P. (2020). _Principles of Distributed Database Systems_, 4th Edition. Springer Nature.**

| Design Choice                              | Reference (4th Edition)                                            |
| ------------------------------------------ | ------------------------------------------------------------------ |
| Lazy (async) replication over Eager (2PC)  | **Ch.6 §6.3.3** (Lazy Centralized) + **§6.3.4** (Lazy Distributed) |
| Eventual consistency for zero-downtime     | **Ch.6 §6.1** (Replication purposes)                               |
| Schema mapping V1 ↔ V2                     | **Ch.7 §7.1.4** (Schema Mapping)                                   |
| Outbox Pattern (transactional consistency) | **Ch.6 §6.3.3** (Lazy update propagation)                          |
| Loop prevention via origin tagging         | **Ch.6 §6.3.4** (Update anywhere model)                            |
| Last-Write-Wins conflict resolution        | **Ch.6 §6.3.4** (Conflict resolution in lazy distributed)          |
| Failure handling without blocking          | **Ch.6 §6.5** (Replication and Failures)                           |
| Distributed transaction reliability        | **Ch.5 §5.4** (Distributed DBMS Reliability)                       |

---

## 🎬 Demo Video

📹 **[Failure Scenarios Demo](demo/failure-scenarios-demo.mp4)**

Video demonstrates:

- Baseline bidirectional sync (Blue → Green)
- 5 failure scenarios on Kubernetes cluster
- Recovery and eventual consistency

---

## ✅ Project Status — COMPLETED

- [x] **Milestone 1**: Docker Compose + Liquibase + Blue/Green CRUD
- [x] **Milestone 2**: Sync Bridge (Outbox + Translator + Replicator)
- [x] **Milestone 3**:
  - [x] Kubernetes deployment (minikube, 6 pods)
  - [x] 5 failure scenarios tested
  - [x] Demo video recorded → [`demo/failure-scenarios-demo.mp4`](demo/failure-scenarios-demo.mp4)
  - [x] [Design Document (2 pages)](DesignDocument.docx)
  - [x] [Analysis Report (12 pages)](AnalysisReport.docx)

## 📦 Deliverables

| #   | Item                           | File                                                                 |
| --- | ------------------------------ | -------------------------------------------------------------------- |
| 1   | Project Proposal               | (submitted earlier)                                                  |
| 2   | Design Document                | [`DesignDocument.docx`](DesignDocument.docx)                         |
| 3   | GitHub Repository + README     | This repo                                                            |
| 4   | Analysis Report                | [`AnalysisReport.docx`](AnalysisReport.docx)                         |
| 5   | Demo Video (failure scenarios) | [`demo/failure-scenarios-demo.mp4`](demo/failure-scenarios-demo.mp4) |

---

## 📄 License

Educational project for Distributed Database course.

---

## 📧 Contact

**Vương Minh Thắng**
Distributed Database Project — 2026
