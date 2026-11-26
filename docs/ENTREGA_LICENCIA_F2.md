# Entrega Fase 2 — Licencia de conducir

## 1. RF / alcance
Registro, consulta, edición, renovación e historial post-renovación (manual A-I / A-IIa). Sin MTC, sin aprobación admin. Entrada: Perfil → Cuenta → Licencia de conducir.

## 2. Pantallas
A vacía · B datos · C fotos · D detalle (máscara + ojo + fotos ocultas + tabs si hay historial) · E editar · F editar fotos · G renovar · H historial resumido.

## 3. Validaciones
Número `^[A-Z][0-9]{8}$`; vigencia `plusMonths(6)`…`plusYears(10)`; emisión ≤ hoy; EA12 permite vencida histórica en alta; renovación exige vencimiento > anterior.

## 4. Escenarios
Cubiertos en `ValidadorLicenciaTest` + `LicenciaFormularioTest` (número, rangos, máscara, emisión futura, vencida histórica). Flujos UI Android implementados (EA01–EA32 a nivel de reglas; IT de red pendientes de entorno con V29).

## 5. Renovación e historial
Transaccional + idempotencia `RENOVAR_LICENCIA`; archiva ACTUAL→HISTORICO; pestaña Historial solo si `tieneHistorial`.

## 6. Pruebas
```
mvn -Dtest=ValidadorLicenciaTest,DatosSensiblesCryptoTest,ValidadorSoatTest,SoatAutorizacionDocumentoTest test
gradlew testDebugUnitTest --tests com.mycar.app.features.licencia.LicenciaFormularioTest --tests com.mycar.app.features.soat.SoatFormularioTest
```

## 7. Limitaciones
- No migrate Neon / no deploy Render sin autorización.
- Tab Alertas RF23 LICENCIA sigue vacío hasta listado personal de alertas en API de alertas.
- Claves `MYCAR_DATOS_SENSIBLES_*` obligatorias para mutaciones.
