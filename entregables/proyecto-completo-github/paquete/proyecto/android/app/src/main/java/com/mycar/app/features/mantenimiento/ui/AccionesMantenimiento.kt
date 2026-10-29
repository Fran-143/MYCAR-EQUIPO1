package com.mycar.app.features.mantenimiento.ui

import com.mycar.app.features.mantenimiento.domain.BorradorMantenimiento
import com.mycar.app.features.mantenimiento.domain.RepuestoBorrador
import com.mycar.app.features.mantenimiento.viewmodel.MantenimientoViewModel

internal interface AccionesMantenimiento {
    fun actualizar(cambio: (BorradorMantenimiento) -> BorradorMantenimiento)
    fun recargar()
    fun fecha(fecha: String)
    fun origen(origen: String)
    fun lectura(id: Long)
    fun solicitarVehiculo(id: Long)
    fun confirmarVehiculo()
    fun cancelarVehiculo()
    fun editarRepuesto(id: String? = null)
    fun cambiarEditor(cambio: (RepuestoBorrador) -> RepuestoBorrador)
    fun cerrarEditor()
    fun confirmarRepuesto()
    fun quitarRepuesto(id: String)
    fun proponerAceiteVehiculo()
    fun guardar()
    fun reintentarMismaOperacion()
    fun consultarOperacion()
    fun solicitarSalir()
    fun seguirEditando()
    fun descartar()
}

internal class AccionesDesdeViewModel(private val vm: MantenimientoViewModel) : AccionesMantenimiento {
    override fun actualizar(cambio: (BorradorMantenimiento) -> BorradorMantenimiento) { vm.actualizar(cambio) }
    override fun recargar() { vm.recargar() }
    override fun fecha(fecha: String) { vm.fecha(fecha) }
    override fun origen(origen: String) { vm.origen(origen) }
    override fun lectura(id: Long) { vm.lectura(id) }
    override fun solicitarVehiculo(id: Long) { vm.solicitarVehiculo(id) }
    override fun confirmarVehiculo() { vm.confirmarVehiculo() }
    override fun cancelarVehiculo() { vm.cancelarVehiculo() }
    override fun editarRepuesto(id: String?) { vm.editarRepuesto(id) }
    override fun cambiarEditor(cambio: (RepuestoBorrador) -> RepuestoBorrador) { vm.cambiarEditor(cambio) }
    override fun cerrarEditor() { vm.cerrarEditor() }
    override fun confirmarRepuesto() { vm.confirmarRepuesto() }
    override fun quitarRepuesto(id: String) { vm.quitarRepuesto(id) }
    override fun proponerAceiteVehiculo() { vm.proponerAceiteVehiculo() }
    override fun guardar() { vm.guardar() }
    override fun reintentarMismaOperacion() { vm.reintentarMismaOperacion() }
    override fun consultarOperacion() { vm.consultarOperacion() }
    override fun solicitarSalir() { vm.solicitarSalir() }
    override fun seguirEditando() { vm.seguirEditando() }
    override fun descartar() { vm.descartar() }
}
