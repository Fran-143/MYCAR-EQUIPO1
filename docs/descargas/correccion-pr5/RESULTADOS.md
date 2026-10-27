# Corrección PR #5 — resultados

27 de octubre de 2026. Base: feat/vehiculos-mvp a936ab6.

- Se reprodujeron primero dos fallos: decimales que llegaban truncados al servicio y contenido no soportado que devolvía 500.
- Después de la corrección, ambas reproducciones pasan.
- Se conservaron los 26 casos MVC anteriores y se añadieron 21 casos: ahora hay 47 casos MVC aprobados.
- Regresión completa sobre PostgreSQL 17 desechable: 42 pruebas de BD + 164 pruebas de validación/servicios/consultas/controlador, todas sin fallos ni errores.
- Contexto Spring y JAR HTTP health UP.
- Las cuatro rutas siguen rechazando peticiones anónimas con 401/403.
- Contenedor temporal retirado correctamente.

Se utiliza deserialización estricta únicamente en los cinco campos numéricos del DTO RF08. No se altera ObjectMapper global, la serialización de solicitudes válidas, el plan, la idempotencia ni las migraciones. Los null opcionales siguen permitidos.

No se modificó Android; sus 47 pruebas y APK ya verificados no se atribuyen a una nueva ejecución en esta corrección. La integración real de autenticación/navegación sigue pendiente.

El paquete es para copiar personalmente cinco archivos y actualizar el mismo PR #5. No se publicó el arreglo en la rama de desarrollo ni se aprobó o fusionó el PR automáticamente.
