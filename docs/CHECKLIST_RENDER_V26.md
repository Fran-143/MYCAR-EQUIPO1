# Checklist Render / V26 (NO ejecutar deploy)

Documento de preparación. **No** Flyway, **no** writes Neon, **no** redeploy Render desde este agente.

## Precondiciones
- [ ] Backup Neon verificable (snapshot/export con timestamp y tamaño)
- [ ] Health Render actual: `GET https://mycar-backend-1dxi.onrender.com/actuator/health`
- [ ] Confirmar Flyway producción en **V25** (sin `taller_global` aún)
- [ ] Confirmar 0 teléfonos MANUAL duplicados (RO) antes de V26

## Migración V26 (autorización humana)
- [ ] Revisar `V26__rf33_taller_asociado_contacto.sql` (incl. `contacto_autorizado`)
- [ ] Aplicar Flyway V26 en Neon (ventana acordada)
- [ ] Verificar columnas: `taller.contacto_autorizado`, `taller_global.contacto_autorizado`
- [ ] Verificar demos catálogo: `es_demo=true`, `contacto_autorizado=false` por defecto

## Backend compatible
- [ ] Redeploy backend con código que expone `ContactoTallerDto.contactoAutorizado`
- [ ] Health post-deploy OK
- [ ] Endpoints RF34 talleres: listar / catalogo-global / vincular / favorito
- [ ] Detalle alerta RF24 incluye `contactoTaller.contactoAutorizado`

## Piloto (privado, solo local/Docker — NUNCA Neon)
- [ ] `mycar.taller-piloto.habilitado=true` solo en entorno autorizado
- [ ] `MYCAR_TALLER_PILOTO_USUARIO_ID` > 0 (obligatorio; sin default fijo)
- [ ] `MYCAR_TALLER_PILOTO_WHATSAPP` válido PE
- [ ] `MYCAR_TALLER_PILOTO_CONTACTO_AUTORIZADO=true` solo con prueba autorizada

## Seguridad catálogo
- [ ] Filas `es_demo` visibles solo para `usuario-id` piloto configurado
- [ ] Vincular ASOCIADO copia `contacto_autorizado` desde global (default false)

## APK de prueba
- [ ] Instalar APK debug o sidecar `.hotfix`
- [ ] Probar sesión/recovery/vehículos/mant/perfil/talleres MANUAL
- [ ] Catálogo ASOCIADO vs Render: **inútil hasta V26 Live** (sin `taller_global`)
- [ ] WhatsApp/llamada real: **tras Agente 2** (solo si `contactoAutorizado==true`)

## Post Agente 2
- [ ] Cherry-pick/merge LOCAL del SHA de Agente 2
- [ ] Re-tests + APK final de prueba
- [ ] Sin push/deploy hasta autorización explícita
