package com.traidores.juego

/**
 * Resolución estática de los nombres que forman parte del protocolo online.
 *
 * Los nombres se conservan en el modelo para compatibilidad entre versiones, pero las
 * referencias directas permiten que R8 elimine arte viejo que no usa el juego publicado.
 */
object DrawableResourceCatalog {
    fun resolve(name: String): Int = when (name) {
        "mapa_grecia" -> R.drawable.mapa_grecia
        "mapa_medieval" -> R.drawable.mapa_medieval
        "mapa_pampa" -> R.drawable.mapa_pampa

        "rol_alcalde_gaucho" -> R.drawable.rol_alcalde_gaucho
        "rol_alcalde_griego" -> R.drawable.rol_alcalde_griego
        "rol_alcalde_medieval" -> R.drawable.rol_alcalde_medieval
        "rol_aldeano_gaucho" -> R.drawable.rol_aldeano_gaucho
        "rol_aldeano_griego" -> R.drawable.rol_aldeano_griego
        "rol_aldeano_medieval" -> R.drawable.rol_aldeano_medieval
        "rol_asesino_gaucho" -> R.drawable.rol_asesino_gaucho
        "rol_asesino_griego" -> R.drawable.rol_asesino_griego
        "rol_asesino_medieval" -> R.drawable.rol_asesino_medieval
        "rol_bufon_medieval" -> R.drawable.rol_bufon_medieval
        "rol_desertor_gaucho" -> R.drawable.rol_desertor_gaucho
        "rol_desertor_griego" -> R.drawable.rol_desertor_griego
        "rol_desertor_medieval" -> R.drawable.rol_desertor_medieval
        "rol_detective_gaucho" -> R.drawable.rol_detective_gaucho
        "rol_detective_griego" -> R.drawable.rol_detective_griego
        "rol_detective_medieval" -> R.drawable.rol_detective_medieval
        "rol_espia_gaucho" -> R.drawable.rol_espia_gaucho
        "rol_espia_griego" -> R.drawable.rol_espia_griego
        "rol_espia_medieval" -> R.drawable.rol_espia_medieval
        "rol_medico_gaucho" -> R.drawable.rol_medico_gaucho
        "rol_medico_griego" -> R.drawable.rol_medico_griego
        "rol_medico_medieval" -> R.drawable.rol_medico_medieval
        "rol_mercenario_gaucho" -> R.drawable.rol_mercenario_gaucho
        "rol_mercenario_griego" -> R.drawable.rol_mercenario_griego
        "rol_mercenario_medieval" -> R.drawable.rol_mercenario_medieval
        "rol_oraculo_griego" -> R.drawable.rol_oraculo_griego
        "rol_payador_gaucho" -> R.drawable.rol_payador_gaucho
        else -> 0
    }

    fun resolveOrPlaceholder(name: String): Int =
        resolve(name).takeIf { it != 0 } ?: R.drawable.placeholder_local
}
