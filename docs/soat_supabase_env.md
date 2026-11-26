# SOAT — variables Supabase Storage (sin secretos en Git)

Proyecto Dashboard: **mycar-documentos**  
Bucket: **mycar-documentos-privados** (PRIVATE)  
MIME: `image/jpeg`, `image/png`, `application/pdf` · máx. **10 MB**

## Variables locales (backend)

Definir en `C:\Proyectos\MyCarEquipo1-talleres-apk\.env` (ya importado por Spring; **no** commitear):

| Variable | Valor a obtener en Supabase |
|----------|-----------------------------|
| `MYCAR_DOCUMENTOS_MODO` | `supabase` |
| `MYCAR_DOCUMENTOS_SUPABASE_URL` | Project Settings → API → **Project URL** (`https://xxxx.supabase.co`) |
| `MYCAR_DOCUMENTOS_SUPABASE_SECRET` | Project Settings → API → clave **secret** nueva (`sb_secret_…`) **o** legacy **service_role** JWT (`eyJ…`). Solo backend. |
| `MYCAR_DOCUMENTOS_SUPABASE_BUCKET` | `mycar-documentos-privados` (opcional si coincide el default) |

Equivalentes Spring (si no usas prefijo env):

- `mycar.documentos.modo=supabase`
- `mycar.documentos.supabase.url=…`
- `mycar.documentos.supabase.secret=…`
- `mycar.documentos.supabase.bucket=mycar-documentos-privados`

Por defecto (sin `.env`): `modo=fake` para tests.

## Qué hacer tú en el Dashboard

1. Crear bucket privado `mycar-documentos-privados` si aún no existe.
2. Restringir MIME a jpeg/png/pdf y tamaño ≤ 10 MB (políticas del bucket).
3. Copiar Project URL + `sb_secret_…` (o service_role) **solo** al `.env` local.
4. No hace falta clave `anon` en Android: la APK sube con URL firmada emitida por Spring.

## Render (después, cuando autorices deploy)

Mismas variables como **Environment** del servicio backend:

- `MYCAR_DOCUMENTOS_MODO=supabase`
- `MYCAR_DOCUMENTOS_SUPABASE_URL`
- `MYCAR_DOCUMENTOS_SUPABASE_SECRET`
- `MYCAR_DOCUMENTOS_SUPABASE_BUCKET` (opcional)

No poner secretos en la APK ni en Git. No modificar Render hasta tu OK.

## Prefijo de objetos

`soat/{idUsuario}/{idVehiculo}/{temp|idSoat}/…` — el backend valida titularidad antes de firmar.
