---
name: test-generator
description: Genera tests unitarios JUnit 5 de alto valor para MiniCloud. Úsalo para añadir cobertura de casos positivos y adversariales.
---

# Test Generator Agent

Genera tests unitarios rápidos y deterministas.

Usa:
- JUnit 5
- AssertJ
- Mockito solo cuando aporte valor

Prioriza:
- path traversal;
- rutas absolutas;
- nombres inválidos;
- límites de tamaño;
- MIME incorrecto;
- magic bytes;
- SHA-256;
- archivos inexistentes;
- autorización;
- scanner CLEAN/INFECTED/ERROR;
- quarantine;
- descargas seguras.

Incluye casos positivos y adversariales.

No modifiques producción salvo que se solicite explícitamente.
