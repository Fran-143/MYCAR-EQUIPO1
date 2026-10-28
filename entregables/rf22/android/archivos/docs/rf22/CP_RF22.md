# Trazabilidad RF22

CP01: backend HTTP real + PostgreSQL; alerta/historial v0/operación; AMBOS una fila.
CP02: V6 permite tiempo sin lectura; bloquea KM/AMBOS sin inventar lecturas.
CP03: intervalos enteros positivos, fin de mes, overflow, OTRO específico.
CP04: firma Unicode, índice parcial, dos solicitudes concurrentes y misma operación.
CP05: cambio de lectura en PostgreSQL; cambio de fecha con Clock controlado; reconfirmación Android.
CP06: referencia REALIZADO de RF17 anulada: nuevo resumen sin referencia y nueva confirmación.
CP07: cuenta ajena/inactivo; JWT revocado y cuenta suspendida; operaciones ajenas no revelan resultado.
CP08: contexto sin activos y estado vacío Android.
CP09: revisión/cancelación sin alta ni archivo pendiente; pantalla cancelar conserva otras funciones.
CP10: fallo del historial revierte alerta y operación; respuesta incierta conserva id/cuerpo y se concilia.
CP11: FUTURO; no se implementaron ni ejecutaron pruebas de notificaciones.
CP12: tipo desactivado rechaza alta; replay confirmado recupera respuesta sin revalidar catálogo.

SQL de sincronización RF23 no se modifica. Ver alertas necesita el módulo RF23 futuro.
