# Migrate Azure App Service: Code → Docker Container

> One-time setup. Sau khi xong, mọi deploy chỉ cần push code lên GitHub.

---

## Bước 1 — Tạo GitHub PAT để Azure pull GHCR

1. GitHub → **Settings → Developer settings → Personal access tokens → Tokens (classic)**
2. **Generate new token (classic)**
3. Note: `azure-pull-ghcr`
4. Expiration: `No expiration` (hoặc 1 năm)
5. Scope: chỉ tick ✅ `read:packages`
6. **Generate token** → **Copy và lưu lại** (chỉ hiện 1 lần)

---

## Bước 2 — Đổi App Service từ Code → Container

### Cách A: Azure Portal (khuyến nghị lần đầu)

1. Vào [portal.azure.com](https://portal.azure.com)
2. Tìm App Service **`tapo-api`**
3. Menu trái → **Deployment Center**
4. **Source**: chọn **Container Registry**
5. Điền thông tin:

   | Field | Giá trị |
   |-------|---------|
   | Registry source | **Private Registry** |
   | Server URL | `https://ghcr.io` |
   | Username | `ndmthuan97` |
   | Password | `<PAT vừa tạo ở Bước 1>` |
   | Full image name and tag | `ghcr.io/ndmthuan97/tapo-api:latest` |
   | Startup command | *(để trống — dùng CMD trong Dockerfile)* |

6. Click **Save**

> ⚠️ Azure sẽ **restart App Service** và pull image từ GHCR ngay.

---

### Cách B: Azure CLI

```bash
# Đăng nhập
az login

# Set container registry credentials
az webapp config container set \
  --name tapo-api \
  --resource-group <RESOURCE_GROUP_NAME> \
  --docker-custom-image-name ghcr.io/ndmthuan97/tapo-api:latest \
  --docker-registry-server-url https://ghcr.io \
  --docker-registry-server-user ndmthuan97 \
  --docker-registry-server-password <GHCR_PAT>

# (Optional) Bật Continuous Deployment webhook
az webapp deployment container config \
  --name tapo-api \
  --resource-group <RESOURCE_GROUP_NAME> \
  --enable-cd true
```

---

## Bước 3 — Update Publish Profile Secret

Publish Profile sau khi đổi sang container sẽ **reset** → cần lấy lại:

1. Azure Portal → App Service `tapo-api`
2. **Overview** → **Get publish profile** → Download file XML
3. GitHub → Repo `Tapo_Backend` → **Settings → Secrets → Actions**
4. Cập nhật secret **`AZURE_WEBAPP_PUBLISH_PROFILE`** = nội dung file XML vừa download

---

## Bước 4 — Xóa file deploy cũ

File `deploy-backend.yml` (deploy JAR) không còn dùng nữa:

```bash
git rm .github/workflows/deploy-backend.yml
git commit -m "ci: remove JAR-based deploy, replaced by deploy-to-azure.yml (container)"
git push
```

---

## Bước 5 — Kiểm tra hoạt động

Push 1 commit nhỏ lên `main` và theo dõi:

```
GitHub Actions tab:
  1. "Test → Build → Push Docker"     → chạy ~3–5 phút
                    ↓ (hoàn thành)
  2. "Deploy → Azure (Container)"     → chạy ~1 phút
                    ↓
  https://tapo-api.azurewebsites.net/actuator/health → {"status":"UP"}
```

---

## Rollback khi cần

```
GitHub → Repo Tapo_Backend → Actions
→ "Deploy → Azure (Container)"
→ Run workflow
→ image_tag: sha-a1b2c3d   ← nhập SHA của commit muốn rollback
→ Run workflow
```

---

## Lưu ý về App Settings

Sau khi chuyển sang container, Azure **giữ nguyên App Settings** (env vars) — không cần nhập lại.

Kiểm tra tại: App Service → **Configuration → Application settings**

Đảm bảo có đủ:
```
SPRING_PROFILES_ACTIVE   = prod
DB_HOST                  = ...
DB_PORT                  = ...
DB_NAME                  = ...
DB_USERNAME              = ...
DB_PASSWORD              = ...
REDIS_URL                = rediss://...
JWT_SECRET               = ...
MAIL_USERNAME            = ...
MAIL_PASSWORD            = ...
ALLOWED_ORIGINS          = https://tapo-store.vercel.app
```
