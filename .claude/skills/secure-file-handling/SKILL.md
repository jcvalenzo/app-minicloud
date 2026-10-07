---
name: secure-file-handling
description: Implementa upload/download seguro y acceso seguro al filesystem en MiniCloud. Úsala al tocar subida, descarga, quarantine, storage o rutas de archivos.
---

# Secure File Handling

Flujo recomendado:

authenticate
-> size limit
-> validate filename
-> quarantine
-> MIME/signature validation
-> SHA-256
-> malware scan if enabled
-> atomic move
-> final storage

Nunca concatenar directamente input del usuario con rutas.

El path normalizado debe permanecer debajo del storage root.

Probar como mínimo:
- `../secret.txt`
- `../../secret.txt`
- rutas absolutas
- nombres vacíos
- separadores
- MIME mismatch
- archivos demasiado grandes
- scanner infected
- scanner error
- archivo inexistente

Para descargas de contenido no confiable preferir:

`Content-Disposition: attachment`

y:

`X-Content-Type-Options: nosniff`
