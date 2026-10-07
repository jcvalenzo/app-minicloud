---
name: spring-boot-lightweight
description: Arquitectura Spring Boot de bajo consumo para MiniCloud. Úsala al añadir dependencias, configuración o componentes Spring.
---

# Lightweight Spring Boot

Preferir:
- Spring Boot Web
- Spring Security
- Thymeleaf
- HTMX
- Java NIO
- streaming
- constructor injection

Evitar:
- JPA/Hibernate sin necesidad real
- Redis
- message brokers
- servicios externos
- frontend SPA
- procesamiento reactivo por moda

No cargues archivos completos en memoria.

Antes de agregar una dependencia, comprobar si Java o Spring ya resuelven el problema.
