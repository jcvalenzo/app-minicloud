# MiniCloud

Aplicación web privada y mínima para subir y descargar archivos con autenticación.
Un único contenedor Spring Boot (Java 21, Spring Security, Thymeleaf) que guarda los archivos
en el filesystem bajo `/data`, sin base de datos ni servicios adicionales.

- Cada usuario solo ve y descarga sus propios archivos.
- Los archivos se guardan con un ID generado por el servidor; el nombre enviado por el cliente
  solo se usa (validado) como nombre de descarga.
- Las subidas pasan por cuarentena, validación de nombre/tamaño/firma, SHA-256 y escáner antes
  del almacenamiento final.
- Las descargas se sirven por streaming como `attachment` con `X-Content-Type-Options: nosniff`.

## Ejecutar en local

Requiere JDK 21.

```sh
export MINICLOUD_USERS_0_USERNAME=juan
export MINICLOUD_USERS_0_PASSWORD_HASH='$2y$12$...'   # ver "Generar hash de contraseña"
./gradlew bootRun --args='--spring.profiles.active=local'
```

Abrir <http://localhost:8080/login>. El perfil `local` guarda los datos en `./data`, desactiva la
cookie `Secure` (HTTP sin TLS) y la caché de plantillas.

Tests y build:

```sh
./gradlew test
./gradlew build
```

## Generar hash de contraseña

Las contraseñas se configuran como hash BCrypt (coste mínimo 10). Con `htpasswd` (Apache utils):

```sh
htpasswd -bnBC 12 "" 'mi-contraseña' | tr -d ':\n'
```

Usa comillas simples al exportar el hash para que el shell no interprete los `$`.

## Variables de entorno

| Variable | Descripción |
| --- | --- |
| `MINICLOUD_USERS_n_USERNAME` | Usuario `n` (0, 1, 2…). Minúsculas, dígitos, `_` o `-`, máx. 32. Al menos un usuario es obligatorio. |
| `MINICLOUD_USERS_n_PASSWORD_HASH` | Hash BCrypt de la contraseña del usuario `n`. |
| `PORT` | Puerto HTTP (por defecto `8080`; Railway lo inyecta). |
| `JAVA_TOOL_OPTIONS` | Opciones de la JVM. La imagen Docker trae valores de bajo consumo (`SerialGC`, `-Xmx192m`, …); sobrescríbela solo si hace falta. |
| `MINICLOUD_UPLOAD_MAX_FILE_SIZE` | Opcional. Tamaño máximo por archivo (por defecto `100MB`). El límite de la petición multipart se deriva de este valor (+1MB). |
| `MINICLOUD_SCANNER_TYPE` | Opcional. `none` (por defecto) o `malwarebazaar`. |
| `MINICLOUD_SCANNER_MALWAREBAZAAR_AUTH_KEY` | Obligatoria si el escáner es `malwarebazaar`. Clave gratuita de https://auth.abuse.ch/. |

Si la configuración es inválida (sin usuarios, hash mal formado, coste < 10…) la aplicación no arranca.

## Escaneo de malware (opcional)

Con `MINICLOUD_SCANNER_TYPE=malwarebazaar`, cada subida se consulta en
[MalwareBazaar](https://bazaar.abuse.ch/) (abuse.ch) por su SHA-256:

- Solo se envía el **hash**, nunca el archivo ni su nombre.
- Si el hash es malware conocido, la subida se rechaza.
- Si MalwareBazaar no responde, devuelve un error o la clave no es válida, la subida **se rechaza**
  (falla cerrado). Para seguir subiendo sin escaneo, vuelve a `MINICLOUD_SCANNER_TYPE=none`.
- Requiere salida a Internet hacia `mb-api.abuse.ch`. La API es gratuita bajo uso razonable; revisa
  sus condiciones si el uso es comercial.

Limitación importante: solo detecta archivos **idénticos** a muestras ya conocidas. Un malware nuevo o
modificado en un solo byte no se detecta. "Sin amenazas detectadas" significa solo que el hash no
estaba en MalwareBazaar en el momento de la subida; los archivos no se vuelven a analizar.

## Despliegue en Railway

1. Crear un servicio desde este repositorio; Railway construye el `Dockerfile`.
2. Añadir un **Persistent Volume** montado en `/data`.
3. Definir las variables `MINICLOUD_USERS_0_USERNAME` y `MINICLOUD_USERS_0_PASSWORD_HASH`
   (y más usuarios si se desea).
4. Healthcheck path: `/login`.
5. Usar **una sola réplica**: el almacenamiento es un volumen local y las sesiones viven en memoria.

Railway termina TLS; la aplicación confía en las cabeceras `X-Forwarded-*` para redirecciones y
cookies seguras. El contenedor corrige la propiedad de `/data` al arrancar y luego se ejecuta como
usuario sin privilegios.

## Limitaciones

- El escáner por defecto es `NoOpMalwareScanner`: **no analiza nada** y los archivos quedan marcados
  como "sin escanear". El escáner `malwarebazaar` solo detecta malware ya conocido por su hash exacto.
- Ningún escaneo antivirus hace que un archivo sea 100 % seguro. Trata los archivos descargados como
  no confiables.
- Una sola instancia: sin replicación ni copias de seguridad automáticas del volumen.
- Las sesiones se pierden al reiniciar el contenedor.
- Tras 5 contraseñas incorrectas la cuenta se bloquea 5 minutos. Quien conozca un nombre de usuario
  puede mantenerlo bloqueado; usa nombres de usuario no obvios.
- Las comprobaciones de contraseña simultáneas están limitadas (2) para que un ataque de fuerza bruta
  no agote la CPU; bajo ataque, un login legítimo puede fallar y habrá que reintentarlo.
