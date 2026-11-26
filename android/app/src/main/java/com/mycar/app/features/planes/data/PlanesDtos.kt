package com.mycar.app.features.planes.data

data class PlanDto(
    val codigo: String? = null,
    val nombre: String? = null,
    val maxVehiculosRegistrados: Int? = null,
    val precioDemo: Double? = null,
    val moneda: String? = null,
    val periodicidad: String? = null,
    val descripcionBeneficios: String? = null,
    val contratacionSandboxHabilitada: Boolean = false,
    val listoParaContratar: Boolean = false,
    val motivoNoContratable: String? = null,
    val pruebaGratuitaDisponible: Boolean = false,
    val motivoNoPruebaGratuita: String? = null,
    val vigenciaPruebaConfigurada: String? = null,
    val vigenciaSandboxConfigurada: String? = null
)

data class CatalogoPlanesDto(
    val planes: List<PlanDto> = emptyList(),
    val sandboxLocalDisponible: Boolean = false,
    val mercadoPagoTestDisponible: Boolean = false,
    val pruebaGratuitaHabilitada: Boolean = false,
    val proveedorPreferido: String? = null
)

data class MiSuscripcionDto(
    val planCodigo: String? = null,
    val planNombre: String? = null,
    val modalidad: String? = null,
    val estadoSuscripcionUi: String? = null,
    val estado: String? = null,
    val origen: String? = null,
    val inicioUtc: String? = null,
    val finUtc: String? = null,
    val diasRestantes: Int? = null,
    val maxVehiculosRegistrados: Int? = null,
    val vehiculosRegistrados: Long = 0,
    val vehiculosActivos: Long = 0,
    val etiquetaSandbox: Boolean = false,
    val puedeSolicitarPruebaGratuita: Boolean = false,
    val motivoNoPruebaGratuita: String? = null,
    val puedeContratarSandbox: Boolean = false,
    val motivoNoSandbox: String? = null,
    val referenciaOperacionUltima: String? = null,
    val codigoSolicitudAbierta: String? = null,
    val avisoUsuario: String? = null,
    val puedeCancelar: Boolean = false,
    val cancelacionProgramada: Boolean = false
)

data class CancelarPremiumResponse(
    val codigo: String? = null,
    val estado: String? = null,
    val mensaje: String? = null,
    val finUtc: String? = null,
    val modalidad: String? = null
)

data class EventoSuscripcionDto(
    val idEvento: Long = 0,
    val tipoEvento: String? = null,
    val detalle: String? = null,
    val creadoUtc: String? = null,
    val referenciaOperacion: String? = null
)

data class EventosPageDto(val items: List<EventoSuscripcionDto> = emptyList())

data class SolicitudSuscripcionDto(
    val codigo: String? = null,
    val modalidad: String? = null,
    val estado: String? = null,
    val creadoUtc: String? = null,
    val decididoUtc: String? = null,
    val motivoRechazo: String? = null,
    val observacionAdmin: String? = null,
    val referenciaOperacion: String? = null,
    val proveedorOperacion: String? = null,
    val estadoOperacion: String? = null,
    val importeOperacion: Double? = null,
    val monedaOperacion: String? = null
)

data class SolicitudesPageDto(val items: List<SolicitudSuscripcionDto> = emptyList())

data class SolicitudDetalleDto(
    val solicitud: SolicitudSuscripcionDto? = null,
    val historial: List<EventoSuscripcionDto> = emptyList()
)

data class CrearPagoBody(
    val idPlanCodigo: String = "PREMIUM",
    val proveedor: String = "SIMULADOR_LOCAL",
    val idOperacion: String? = null
)

data class SimularPagoBody(val escenario: String)

data class OperacionPagoDto(
    val referencia: String? = null,
    val proveedor: String? = null,
    val estado: String? = null,
    val moneda: String? = null,
    val importe: Double? = null,
    val initPoint: String? = null,
    val sandboxInitPoint: String? = null,
    val creadoUtc: String? = null,
    val verificadoUtc: String? = null,
    val evidenciaVerificacion: String? = null,
    val suscripcionActivada: Boolean = false,
    val codigoSolicitud: String? = null,
    val estadoSolicitud: String? = null,
    val mensajeUsuario: String? = null
)

data class ApiErrorPlanes(
    val codigo: String? = null,
    val mensaje: String? = null,
    val reintentable: Boolean = false
)
