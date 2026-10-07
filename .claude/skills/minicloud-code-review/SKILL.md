---
name: minicloud-code-review
description: Revisión de seguridad, consumo, correctitud y complejidad de MiniCloud. Úsala al revisar cambios o un diff de este proyecto.
---

# Code Review

Orden:
1. Seguridad
2. Correctitud
3. Consumo
4. Tests
5. Mantenibilidad

Red flags:
- `Files.readAllBytes()` para archivos arbitrarios
- filenames usados como paths
- endpoints sin autorización
- uploads sin límites
- extracción automática de ZIP
- secretos en código/logs
- nuevos servicios sin necesidad
- dependencias pesadas sin justificación

Reportar:
Severity
Location
Problem
Risk
Recommendation
