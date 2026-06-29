# Transportes Guías API — Solución Cloud Native (Semana 3, Grupo 13)

Sistema de **Gestión de Pedidos y Generación de Guías de Despacho** para una empresa
transportista. Las guías se generan como PDF, se almacenan **temporalmente en Amazon EFS**,
se suben a **Amazon S3** organizadas por fecha y transportista, y se despliegan mediante un
**pipeline automatizado** (GitHub Actions → Docker Hub → EC2).

Asignatura: **Desarrollo Cloud Native (CDY2204)** — Actividad sumativa Semana 3.

---

## 1. Arquitectura

```
                          ┌──────────────────────── GitHub Actions (CI/CD) ───────────────────────┐
   git push main  ───────▶│ build & test (Maven, Java 21) → docker build/push → SSH deploy a EC2   │
                          └───────────────────────────────────────────────────────────────────────┘
                                                          │
                                                          ▼
   Cliente (Postman) ──HTTP──▶  EC2  ──docker run -v /mnt/efs:/app/efs──▶  Contenedor Spring Boot
                                                          │                         │
                                                          │  1) genera PDF          │
                                                          ▼                         ▼
                                              Amazon EFS (/app/efs)   ───sube───▶  Amazon S3 (privado)
                                              guias/<fecha>/<transp>/         guias/<fecha>/<transp>/guia.pdf
                                              (almacenamiento temporal)        (almacenamiento final)
```

**Flujo de una guía:** se crea el registro en BD → se genera el PDF → se escribe **primero en EFS**
→ se sube a **S3** → puede descargarse (con validación de permisos), actualizarse (regenera y
reemplaza en S3) o eliminarse (borra de S3 y marca estado `ELIMINADA`). Todo queda en el **historial**.

## 2. Stack técnico

| Componente        | Versión / Detalle                          |
|-------------------|--------------------------------------------|
| Java              | 21 LTS                                      |
| Spring Boot       | 3.3.5 (Web, Data JPA, Validation, Actuator) |
| Base de datos     | H2 (archivo, configurable a Oracle)         |
| AWS SDK           | v2 (S3)                                      |
| Generación PDF    | iText 5.5.13.4                              |
| Build             | Maven                                        |
| Contenedor        | Docker (multi-stage)                         |
| CI/CD             | GitHub Actions + Docker Hub + EC2 (SSH)      |
| Almacenamiento    | Amazon EFS (temporal) + Amazon S3 (final)    |

## 3. Estructura del proyecto

```
transportes-guias-api/
├── pom.xml
├── Dockerfile
├── .dockerignore
├── .gitignore
├── .github/workflows/main.yml        # Pipeline CI/CD
├── docs/
│   ├── EC2-EFS-SETUP.md              # Comandos EC2, montaje EFS, despliegue manual
│   ├── CHECKLIST-EVIDENCIAS.md       # Guion y evidencias del video
│   └── postman/
│       └── Transportes-Guias.postman_collection.json
└── src/main/java/com/grupo13/transportes/
    ├── TransportesGuiasApiApplication.java
    ├── controller/GuiaDespachoController.java
    ├── service/   (GuiaDespachoService, PdfGuiaService, EfsStorageService, S3StorageService)
    ├── repository/GuiaDespachoRepository.java
    ├── entity/    (GuiaDespacho, EstadoGuia)
    ├── dto/       (CrearGuiaRequest, ActualizarGuiaRequest, GuiaResponse)
    ├── config/S3Config.java
    ├── security/  (Rol, ValidadorPermisos)
    └── exception/ (GlobalExceptionHandler + excepciones de dominio)
```

## 4. Endpoints REST

| Método | Ruta                                          | Descripción                                              |
|--------|-----------------------------------------------|---------------------------------------------------------|
| POST   | `/api/guias`                                  | Crea la guía, genera el PDF y lo guarda en **EFS**       |
| POST   | `/api/guias/{id}/subir-s3`                    | Sube la guía desde EFS hacia **S3**                      |
| GET    | `/api/guias/{id}/descargar`                   | Descarga desde S3 **validando permisos** (ADMIN/TRANSP.) |
| PUT    | `/api/guias/{id}`                             | Modifica, regenera el PDF y **reemplaza en S3**          |
| DELETE | `/api/guias/{id}`                             | Elimina de S3 y marca estado `ELIMINADA`                |
| GET    | `/api/guias?transportista={t}&fecha={yyyy-MM-dd}` | **Historial** filtrado por transportista y fecha    |
| GET    | `/api/guias/{id}`                             | Detalle de una guía                                     |
| GET    | `/actuator/health`                            | Health check (usado por el pipeline)                    |

### Validación de permisos (descarga)
Se envían por cabeceras HTTP:
- `X-Usuario-Rol`: `ADMIN` o `TRANSPORTISTA`.
- `X-Usuario-Nombre`: nombre del transportista (requerido si el rol es `TRANSPORTISTA`).

Regla: **ADMIN** descarga cualquier guía; **TRANSPORTISTA** solo las propias (su nombre debe
coincidir con el campo `transportista` de la guía). En caso contrario se responde **403 Forbidden**.

## 5. Variables de entorno (sin credenciales en el código)

| Variable                | Descripción                                  | Ejemplo                         |
|-------------------------|----------------------------------------------|---------------------------------|
| `AWS_REGION`            | Región AWS                                    | `us-east-1`                     |
| `AWS_ACCESS_KEY_ID`     | Credencial temporal (AWS Academy)            | `ASIA...`                       |
| `AWS_SECRET_ACCESS_KEY` | Credencial temporal                          | `...`                           |
| `AWS_SESSION_TOKEN`     | Token de sesión temporal (Learner Lab)       | `...`                           |
| `AWS_S3_BUCKET`         | Bucket S3 (privado)                          | `transportes-guias-grupo13`     |
| `EFS_MOUNT_PATH`        | Punto de montaje EFS en el contenedor        | `/app/efs`                      |
| `SERVER_PORT`           | Puerto HTTP                                   | `8080`                          |
| `DB_URL` (opcional)     | JDBC; por defecto H2 en archivo              | `jdbc:h2:file:./data/guias`     |

> ⚠️ **Nunca** se versionan credenciales. En local usa variables de entorno o un archivo
> `application-local.yml` (ignorado por git). En el pipeline se usan **GitHub Secrets**.

## 6. Ejecución local

```bash
# 1) Compilar y testear
mvn clean package

# 2) Exportar variables (ejemplo Linux/Mac; en PowerShell usar $env:VAR="...")
export AWS_REGION=us-east-1
export AWS_ACCESS_KEY_ID=...      # credenciales del Learner Lab
export AWS_SECRET_ACCESS_KEY=...
export AWS_SESSION_TOKEN=...
export AWS_S3_BUCKET=transportes-guias-grupo13
export EFS_MOUNT_PATH=./efs-local # carpeta local que simula EFS

# 3) Ejecutar
java -jar target/transportes-guias-api-1.0.0.jar
```

La app queda en `http://localhost:8080`. Consola H2 en `http://localhost:8080/h2-console`.

## 7. Docker

```bash
docker build -t TU_USUARIO_DOCKERHUB/transportes-guias-api .

docker run -d --name transportes-guias-api \
  -v /mnt/efs:/app/efs \
  -p 8080:8080 \
  -e AWS_REGION=us-east-1 \
  -e AWS_ACCESS_KEY_ID=... \
  -e AWS_SECRET_ACCESS_KEY=... \
  -e AWS_SESSION_TOKEN=... \
  -e AWS_S3_BUCKET=transportes-guias-grupo13 \
  TU_USUARIO_DOCKERHUB/transportes-guias-api
```

## 8. CI/CD (GitHub Actions)

Al hacer `git push` a `main`, el workflow `.github/workflows/main.yml`:
1. Compila y testea con Maven (Java 21).
2. Construye la imagen y la publica en **Docker Hub** (`:latest` y `:<sha>`).
3. Se conecta por **SSH a EC2**, hace `docker pull` y ejecuta el contenedor con el volumen
   **EFS montado** (`-v /mnt/efs:/app/efs`).

**Secrets requeridos** (Settings → Secrets and variables → Actions):

| Secret                  | Descripción                              |
|-------------------------|------------------------------------------|
| `DOCKERHUB_USERNAME`    | Usuario de Docker Hub                     |
| `DOCKERHUB_TOKEN`       | Access token de Docker Hub                |
| `EC2_HOST`              | IP/DNS público de la EC2                  |
| `EC2_USER`              | Usuario SSH (`ec2-user` o `ubuntu`)       |
| `EC2_SSH_KEY`           | Clave privada SSH (contenido del .pem)    |
| `AWS_REGION`            | Región AWS                                |
| `AWS_ACCESS_KEY_ID`     | Credencial temporal                       |
| `AWS_SECRET_ACCESS_KEY` | Credencial temporal                       |
| `AWS_SESSION_TOKEN`     | Token de sesión temporal                  |
| `AWS_S3_BUCKET`         | Nombre del bucket S3                       |

Detalle de aprovisionamiento de EC2 y montaje de EFS: ver [`docs/EC2-EFS-SETUP.md`](docs/EC2-EFS-SETUP.md).

## 9. Inicializar el repositorio Git y subir a GitHub

```bash
cd transportes-guias-api
git init
git add .
git commit -m "V2 Grupo13: solución Cloud Native completa"
git branch -M main
git remote add origin https://github.com/TU_USUARIO/transportes-guias-api.git
git push -u origin main      # esto dispara el pipeline
```

## 10. AWS Academy 2026 — recomendaciones

- Las credenciales `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` y `AWS_SESSION_TOKEN` son
  **temporales** y cambian cada vez que se reinicia el Learner Lab. Hay que **actualizar los
  GitHub Secrets** (y reiniciar el contenedor en EC2) cuando expiren.
- El rol disponible suele ser `LabRole`; el bucket S3 debe mantenerse **privado**.
- Si la EC2 tiene un rol IAM con acceso a S3, puedes omitir las credenciales: el `S3Config`
  usa automáticamente el `DefaultCredentialsProvider`.
- Verifica que el **Security Group** de la EC2 permita el puerto `8080` (y `22` para SSH/deploy).
- EFS requiere `amazon-efs-utils` o `nfs-utils` y que el Security Group de EFS permita NFS (2049)
  desde la EC2.

## 11. Pruebas (Postman)

Importa `docs/postman/Transportes-Guias.postman_collection.json`. La tabla de pruebas y el
guion del video están en [`docs/CHECKLIST-EVIDENCIAS.md`](docs/CHECKLIST-EVIDENCIAS.md).
