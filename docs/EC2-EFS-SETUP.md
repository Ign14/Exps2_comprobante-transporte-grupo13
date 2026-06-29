# Aprovisionamiento de EC2, montaje de EFS y despliegue

Guía de comandos para preparar la instancia EC2 (AWS Academy / Learner Lab), montar Amazon EFS
y ejecutar el contenedor. Reemplaza los valores `TU_...` por los de tu laboratorio.

---

## 1. Crear los recursos en AWS Academy

1. **S3**: crea un bucket privado, p.ej. `transportes-guias-grupo13` (Block Public Access activado).
2. **EFS**: crea un sistema de archivos EFS en la misma VPC que la EC2.
   - Anota el **EFS ID** (`fs-xxxxxxxx`) o su DNS (`fs-xxxxxxxx.efs.us-east-1.amazonaws.com`).
   - En el **Security Group del EFS**, permite el puerto **NFS 2049** desde el Security Group de la EC2.
3. **EC2**: lanza una instancia Amazon Linux 2023 (o Ubuntu).
   - **Security Group**: abre `22` (SSH) y `8080` (API). Opcionalmente `80`.
   - Asocia el rol de instancia `LabRole` si quieres usar credenciales por rol IAM.

## 2. Conexión SSH

```bash
ssh -i TU_LLAVE.pem ec2-user@TU_IP_EC2      # Amazon Linux
# ssh -i TU_LLAVE.pem ubuntu@TU_IP_EC2      # Ubuntu
```

## 3. Instalar Docker

**Amazon Linux 2023:**
```bash
sudo yum update -y
sudo yum install -y docker
sudo systemctl enable --now docker
sudo usermod -aG docker $USER
# cierra y reabre la sesión SSH para aplicar el grupo docker
```

**Ubuntu:**
```bash
sudo apt-get update -y
sudo apt-get install -y docker.io
sudo systemctl enable --now docker
sudo usermod -aG docker $USER
```

## 4. Montar Amazon EFS en /mnt/efs

**Amazon Linux 2023 (con amazon-efs-utils):**
```bash
sudo yum install -y amazon-efs-utils
sudo mkdir -p /mnt/efs
sudo mount -t efs -o tls fs-XXXXXXXX:/ /mnt/efs

# Verificar
df -h | grep efs
```

**Alternativa con NFS (Ubuntu o si no hay efs-utils):**
```bash
sudo apt-get install -y nfs-common      # Ubuntu
sudo mkdir -p /mnt/efs
sudo mount -t nfs4 -o nfsvers=4.1 \
  fs-XXXXXXXX.efs.us-east-1.amazonaws.com:/ /mnt/efs
```

**Montaje persistente (opcional, sobrevive reinicios)** — añade a `/etc/fstab`:
```
fs-XXXXXXXX:/ /mnt/efs efs _netdev,tls 0 0
```

Prueba de escritura:
```bash
sudo touch /mnt/efs/prueba.txt && ls -l /mnt/efs && sudo rm /mnt/efs/prueba.txt
```

## 5. Ejecutar el contenedor (despliegue manual)

> En el flujo normal lo hace **GitHub Actions** automáticamente. Estos comandos sirven para
> pruebas manuales o demostración en el video.

```bash
docker login -u TU_USUARIO_DOCKERHUB

docker pull TU_USUARIO_DOCKERHUB/transportes-guias-api:latest

docker stop transportes-guias-api 2>/dev/null || true
docker rm   transportes-guias-api 2>/dev/null || true

docker run -d \
  --name transportes-guias-api \
  --restart unless-stopped \
  -v /mnt/efs:/app/efs \
  -p 8080:8080 \
  -e AWS_REGION=us-east-1 \
  -e AWS_ACCESS_KEY_ID=ASIA... \
  -e AWS_SECRET_ACCESS_KEY=... \
  -e AWS_SESSION_TOKEN=... \
  -e AWS_S3_BUCKET=transportes-guias-grupo13 \
  -e EFS_MOUNT_PATH=/app/efs \
  TU_USUARIO_DOCKERHUB/transportes-guias-api:latest
```

## 6. Verificación post-despliegue

```bash
docker ps                                   # contenedor en ejecución
docker logs -f transportes-guias-api        # logs de arranque
curl http://localhost:8080/actuator/health  # {"status":"UP"}

# Crear una guía de prueba (se escribe en /mnt/efs vía el volumen)
curl -X POST http://TU_IP_EC2:8080/api/guias \
  -H "Content-Type: application/json" \
  -H "X-Usuario-Nombre: Transportista X" \
  -d '{"transportista":"Transportista X","cliente":"Cliente Demo","direccionOrigen":"Origen","direccionDestino":"Destino"}'

# Evidencia de que el PDF quedó PRIMERO en EFS
sudo find /mnt/efs/guias -type f
```

## 7. Refrescar credenciales del Learner Lab (cuando expiran)

Las credenciales de AWS Academy caducan. Para reanudar el servicio:

```bash
docker rm -f transportes-guias-api
# volver a ejecutar el docker run del paso 5 con las nuevas credenciales
```
En el pipeline: actualiza los **GitHub Secrets** `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`
y `AWS_SESSION_TOKEN`, y vuelve a ejecutar el workflow (re-run o nuevo push).
