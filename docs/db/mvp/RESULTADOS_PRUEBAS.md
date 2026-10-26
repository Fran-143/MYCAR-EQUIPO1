# Resultados de verificación — MyCar MVP

Ejecución completada: 2026-10-26T13:10:01-05:00.

Entorno aislado: PostgreSQL 17.11 (Debian 17.11-1.pgdg13+2), JDK 21.0.12.1, Maven Wrapper 3.10.0. No se usaron la BD, el volumen Docker ni las credenciales locales del equipo.

Comando reproducible normal:

```bash
python3 scripts/test_mvp_db.py
```

En el entorno de revisión se añadieron `--maven-settings /tmp/mycar-maven/settings.xml --maven-repo /tmp/mycar-maven/repository` para el proxy/caché externos. Se utilizó un JDK completo temporal; la imagen inicial del entorno solo tenía runtime. Esos ajustes no se versionan ni son necesarios en una instalación normal con JDK 21.

## Resultado final

| Verificación | Resultado |
|---|---|
| Suite PostgreSQL/Flyway | PASÓ: 34 pruebas, 0 fallos, 0 errores, 0 omitidas. |
| Prueba existente BackendApplicationTests | PASÓ: 1 prueba, 0 fallos, 0 errores. |
| Arranque HTTP del jar Spring Boot | PASÓ: `/actuator/health` devuelve `{"status":"UP"}`. |
| Migraciones | PASÓ: V1 → V2 → V3 → V4; validación y segunda ejecución sin cambios. |
| V1 | PASÓ: contenido/hash conservado respecto de main. |
| Modelo | PASÓ: 21 tablas de negocio, 21 PK, 149 columnas documentadas presentes, FK compuestas e índices parciales. |
| Datos previos | PASÓ: fila en otro esquema conservada; esquema ocupado rechazado sin alterar su fila ni dejar tablas parciales. |
| Fixtures principales | PASÓ: después de cada prueba se revierten las filas; conteo final sin datos de usuarios. |
| Datos de las carreras | Bases aisladas con commits reales dentro del contenedor; el ejecutor retiró exclusivamente su contenedor y volúmenes anónimos al terminar. |

## Casos ejecutados

| Caso JUnit | Resultado |
|---|---|
| `alertasLimitesTemporalesKmExactoYAmbos` | PASÓ |
| `altaCuentaEsAtomicaYFreeNoSeDuplica` | PASÓ |
| `catalogosSonLosAprobadosSinPremiumOperativo` | PASÓ |
| `columnasDelDiccionarioAprobadoEstanPresentes` | PASÓ |
| `concurrenciaAlertasEquivalentesSoloUnaConHistorial` | PASÓ |
| `concurrenciaFreeSoloUnAltaYReintentoMismaOperacion` | PASÓ |
| `concurrenciaLecturasConVersionSoloUnaConfirmacion` | PASÓ |
| `concurrenciaMismaOperacionRecuperaMismoVehiculoSinLimiteFree` | PASÓ |
| `cuentaCorreoEdadConsentimientoYRol` | PASÓ |
| `esquemaNoVacioSeRechazaSinModificarFilaExistente` | PASÓ |
| `estructuraTiene21TablasClavesYIndicesParciales` | PASÓ |
| `fallosLoginVentanaMovilRealYBloqueoSinExtender` | PASÓ |
| `fallosSeparadosNoBloqueanYLoginExitosoReiniciaSinBorrar` | PASÓ |
| `flywayV1IntactaActualizaYReintentaSinModificarDatos` | PASÓ |
| `mesesCalendarioDesdeBaseOriginal` | PASÓ |
| `motoresOtroYAnioRespetanReglas` | PASÓ |
| `normalizacionCompletaSinAlterarTextoVisible` | PASÓ |
| `placaGlobalVinOpcionalYFormatosInvalidos` | PASÓ |
| `rf08ContratoSqlIdempotenciaAntesDeCupoYRollback` | PASÓ |
| `rf08FalloRecordatorioRevierteLosTresRegistros` | PASÓ |
| `rf14NoSincronizaRf23CambiaSoloUnaVez` | PASÓ |
| `rf14SoloIncrementaYLecturaNoSeMuta` | PASÓ |
| `rf17HistoricoNoAlteraOdometroYRecomendaciones` | PASÓ |
| `rf17LecturaExactaDelMismoVehiculoYTipoActivo` | PASÓ |
| `rf17RepuestosRepetiblesConPropiedadYSnapshot` | PASÓ |
| `rf22EquivalenteActivoNoDuplicaYBasesSeRevalidan` | PASÓ |
| `rf22ReferenciaUltimaNormalizadaYNoOtroVehiculo` | PASÓ |
| `rf23AmbosEligePrimeraEvidenciaYEmpateFavoreceTiempo` | PASÓ |
| `rf23KmUsaPrimeraLecturaVerificableYNoLaUltima` | PASÓ |
| `rf23NoAnulaPorAntiguedadSinEvidenciaKm` | PASÓ |
| `rf23RechazaTitularAjenoYRollbackSiFallaHistorial` | PASÓ |
| `rf23VeintinueveVsTreintaDiasDesdeEvidenciaNoDesdeCommit` | PASÓ |
| `sesionesPorInstalacionYLogoutIndividual` | PASÓ |
| `smokeOriginalAdaptadoAHistoryDeFlyway` | PASÓ |

## Alcance de las pruebas

Las pruebas de restricciones/funciones se ejecutan sobre PostgreSQL real, no H2 ni mocks. Las carreras abren dos conexiones concurrentes y exigen un solo resultado permitido. Los helpers ensayan el contrato SQL para cupo/idempotencia/versiones; no son servicios Java productivos.

Las filas históricas representan datos que habrían sido válidos al crearse. Solo su preparación utiliza privilegios del contenedor y `SET LOCAL session_replication_role=replica`; las validaciones negativas y la función RF23 se prueban con triggers activos. No se salta integridad en producción.

## Errores detectados durante la integración

- El primer ensayo de concurrencia Free produjo un interbloqueo 40P01 al combinar la FK de operación con FOR UPDATE de usuario. Se corrigió el patrón a FOR NO KEY UPDATE y pasaron las carreras de dos altas distintas y del mismo reintento.
- El primer ensayo de edad admitió un menor a un día de los 18 por comparación de intervalos. Se corrigió el trigger y se probaron 18, 85, menor de 18 y 86.
- La literal JSON de normalización se separó del delimitador dollar-quote para evitar que `${` fuese interpretado por Flyway como placeholder. V4 se compiló y migró correctamente después de corregirlo.
- El arranque aislado inicial mostró que el import literal ../.env no se sustituía mediante SPRING_CONFIG_IMPORT. Se parametrizó con MYCAR_ENV_FILE conservando el valor local por defecto; contexto y HTTP pasaron sin crear ni leer un .env del checkout.

## Pendiente / no ejecutado

- NO EJECUTADO contra PostgreSQL del PC de Francesca ni de sus compañeras. El PR no aplica cambios a esas bases.
- NO IMPLEMENTADOS ni probados de extremo a extremo: endpoints de los once RF, passwords reales, tokens, autorización HTTP/propiedad, generación de firmas canónicas en servicios, formularios y navegación Android.
- El contrato requiere validar cronología completa del km histórico y cumplir transacciones de usuario/Free/idempotencia en servicios Java; que los helpers SQL pasen no acredita esa implementación futura.
- NO se alteraron EF/EA congelados ni se añadieron RF ajenos al MVP.

Los logs detallados quedan en `backend/target/surefire-reports` y logs Spring en target, ignorados por Git. No se publican logs completos de Spring porque puede emitir una contraseña de desarrollo generada.
