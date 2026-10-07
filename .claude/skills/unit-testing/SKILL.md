---
name: unit-testing
description: Genera tests unitarios JUnit 5 para comportamiento y seguridad de MiniCloud. Úsala al escribir o revisar tests.
---

# Unit Testing

Preferir tests aislados sin levantar Spring.

Herramientas:
- JUnit 5
- AssertJ
- Mockito cuando sea necesario

Los tests deben proteger invariantes reales.

Ejemplos:
- `shouldRejectPathTraversal()`
- `shouldRejectOversizedUpload()`
- `shouldRejectInfectedFile()`
- `shouldNotExposeFilesystemPath()`
- `shouldStreamDownload()`

No probar getters, setters ni comportamiento trivial del framework.
