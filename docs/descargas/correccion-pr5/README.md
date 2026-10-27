# Corrección del PR #5

[Descargar Correccion_PR5_MyCar.zip](Correccion_PR5_MyCar.zip): abre el archivo y pulsa Download raw file.

Sigue [LEEME_PRIMERO.md](LEEME_PRIMERO.md) para reemplazar cuatro archivos y añadir uno, ejecutar la verificación y actualizar el mismo PR. [Resultados](RESULTADOS.md): 42 pruebas BD + 164 backend sin fallos, contexto Spring y health UP.

La corrección rechaza decimales/cadenas/desbordamientos en campos enteros RF08 y devuelve 415 para contenido no soportado. No modifica BD, migraciones ni Android.

Este commit solo publica el paquete para que Francesca lo incorpore personalmente. No modifica feat/vehiculos-mvp ni main ni hace merge del PR.
