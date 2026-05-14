# Distributed Database Project #127
## Blue-Green Deployment for Schema Migration

**Student:** Vương Minh Thắng  
**Course:** Distributed Database Systems

## Overview
Zero-downtime schema migration using Blue-Green deployment pattern with Synchronization Bridge.

## Tech Stack
- Java 17 + Spring Boot 3
- MySQL 8.0 (2 instances)
- Liquibase, RabbitMQ
- Docker Compose, Kubernetes

## Architecture
- **Blue Cluster** (port 8081): MySQL V1 schema
- **Green Cluster** (port 8082): MySQL V2 schema
- **Sync Bridge** (port 8083): Bidirectional async replication

## How to Run

### 1. Start infrastructure
```bash
docker-compose up -d
```

### 2. Run Blue Service
```bash
cd blue-service
mvn spring-boot:run
```

### 3. Run Green Service
```bash
cd green-service
mvn spring-boot:run
```

## Status
- [x] Milestone 1: Docker + Liquibase + CRUD APIs
- [ ] Milestone 2: Sync Bridge
- [ ] Milestone 3: Kubernetes + Failure scenarios