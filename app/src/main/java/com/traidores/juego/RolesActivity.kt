package com.traidores.juego

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class RolesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_roles)

        val btnBack: ImageButton = findViewById(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        val rolesList = listOf(
            Role(
                "Aldeana",
                "Feudo de Hierro",
                "Pueblo",
                "Vive donde el barro llega antes que las noticias. No tiene poder, titulo ni espada, pero conoce cada puerta, cada deuda y cada silencio raro del pueblo. Cuando el feudo empieza a pudrirse, ella lo nota antes que nadie.",
                "No tiene habilidad especial. Participa en el debate y en las votaciones para descubrir a los traidores.",
                "rol_aldeano_medieval"
            ),
            Role(
                "Detective",
                "Feudo de Hierro",
                "Pueblo",
                "Antes resolvia robos menores y mentiras de taberna. Ahora sigue rastros de sangre disfrazados de accidentes, sabiendo que descubrir la verdad no sirve de nada si no vive lo suficiente para contarla.",
                "Cada noche investiga a un jugador y recibe una pista sobre si parece inocente o sospechoso.",
                "rol_detective_medieval"
            ),
            Role(
                "Medico",
                "Feudo de Hierro",
                "Pueblo",
                "Todos en el feudo le deben algo: una fiebre curada, una herida cerrada o una vida salvada a tiempo. No pregunta de que lado viene el paciente, pero recuerda cada corte, cada veneno y cada mentira.",
                "Cada noche protege a un jugador. Si ese jugador iba a morir, la eliminacion se cancela.",
                "rol_medico_medieval"
            ),
            Role(
                "Alcalde",
                "Feudo de Hierro",
                "Pueblo",
                "Gobierna desde una silla demasiado grande y una mesa demasiado llena. Esta perdiendo la cordura entre rumores, copas servidas y ordenes que apenas recuerda, pero todavia conserva el poder de salvar o condenar al feudo.",
                "Puede revelar su identidad durante el debate. Desde ese momento, su voto vale doble.",
                "rol_alcalde_medieval"
            ),
            Role(
                "Asesino",
                "Feudo de Hierro",
                "Asesino",
                "Una familia misteriosa lo compro con oro, tierras y promesas de un apellido respetado. No mata por fe ni por justicia: mata porque vio una escalera hacia el poder y decidio subirla con las manos manchadas.",
                "Forma parte del bando traidor. Cada noche participa en la eleccion de la victima.",
                "rol_asesino_medieval"
            ),
            Role(
                "Espia",
                "Feudo de Hierro",
                "Asesino",
                "Aconseja al alcalde desde muy cerca, eligiendo que verdades llegan completas y cuales llegan torcidas. Su arma no es una daga, sino una voz tranquila que alimenta la paranoia del poder.",
                "Forma parte del bando traidor, pero ante la investigacion del Detective aparece como inocente.",
                "rol_espia_medieval"
            ),
            Role(
                "Mercenario",
                "Feudo de Hierro",
                "Asesino",
                "No sirve a ningun linaje, solo al contrato correcto. Ha peleado por senores que olvidaron su nombre y ahora cobra para que ciertas conversaciones terminen antes de tiempo.",
                "Forma parte del bando traidor. Puede impedir que una victima hable o vote durante el dia siguiente.",
                "rol_mercenario_medieval"
            ),
            Role(
                "Desertora",
                "Feudo de Hierro",
                "Neutral",
                "Una vez juro proteger el feudo, hasta que entendio a quien estaba protegiendo realmente. Desde entonces vive entre caminos y nombres falsos, cargando con la duda de si huyo por cobardia o por lucidez.",
                "No pertenece al pueblo ni a los traidores. Su objetivo es sobrevivir hasta el final de la partida.",
                "rol_desertor_medieval"
            ),
            Role(
                "Bufon",
                "Feudo de Hierro",
                "Rol de Mapa",
                "Sobrevive haciendo reir a quienes podrian mandarlo a morir. Mientras el feudo se hunde en acusaciones, el tropieza, exagera y dice verdades que todos prefieren tomar como broma. Si lo votan, al menos tendra publico completo.",
                "Quiere ser eliminado por votacion del pueblo. Si lo consiguen votar, gana su propia condicion especial.",
                "rol_bufon_medieval"
            ),

            Role(
                "Aldeana",
                "Antigua Grecia",
                "Pueblo",
                "Vende aceite, pan o telas bajo estatuas demasiado grandes para una ciudad tan pequena. Escucha a los hombres hablar de gloria en la plaza mientras ella cuenta monedas para llegar al invierno. No cree en discursos: cree en sobrevivir al proximo decreto.",
                "No tiene habilidad especial. Participa en el debate y en las votaciones para descubrir a los traidores.",
                "rol_aldeano_griego"
            ),
            Role(
                "Detective",
                "Antigua Grecia",
                "Pueblo",
                "No busca huellas en callejones, sino contradicciones en discursos publicos. Sabe que en la polis nadie miente en silencio: todos mienten frente a testigos, con palabras hermosas y las manos limpias.",
                "Cada noche investiga a un jugador y recibe una pista sobre si parece inocente o sospechoso.",
                "rol_detective_griego"
            ),
            Role(
                "Medica",
                "Antigua Grecia",
                "Pueblo",
                "Aprendio a curar cuerpos entrenados para la guerra y estomagos vacios por culpa de la politica. En su mesa no importa si alguien invoca a Ares, a Atenea o a nadie; la sangre se seca igual en todos.",
                "Cada noche protege a un jugador. Si ese jugador iba a morir, la eliminacion se cancela.",
                "rol_medico_griego"
            ),
            Role(
                "Alcalde",
                "Antigua Grecia",
                "Pueblo",
                "No gobierna desde un trono, sino desde la palabra. Cada decision debe sonar justa, fuerte y digna ante una polis que exige grandeza aunque apenas pueda sostenerse. Su miedo no es morir: es quedar como el lider que hizo pequena a su ciudad.",
                "Puede revelar su identidad durante el debate. Desde ese momento, su voto vale doble.",
                "rol_alcalde_griego"
            ),
            Role(
                "Asesina",
                "Antigua Grecia",
                "Asesino",
                "Se puso la mascara dorada para dejar de ser una voz ignorada entre la multitud. El culto le prometio que Ares no escucha plegarias, escucha actos. Desde entonces mata convencida de que cada cuerpo caido obliga a la polis a despertar.",
                "Forma parte del bando traidor. Cada noche participa en la eleccion de la victima.",
                "rol_asesino_griego"
            ),
            Role(
                "Espia",
                "Antigua Grecia",
                "Asesino",
                "No necesita ocultarse en sombras: se sienta en la plaza, aplaude discursos y repite frases en el oido correcto. En una ciudad gobernada por reputaciones, una palabra bien colocada puede arruinar mas que una daga.",
                "Forma parte del bando traidor, pero ante la investigacion del Detective aparece como inocente.",
                "rol_espia_griego"
            ),
            Role(
                "Mercenario",
                "Antigua Grecia",
                "Asesino",
                "Vino de guerras ajenas, donde los dioses cambiaban de nombre pero los muertos pesaban igual. La polis lo mira como extranjero util: indigno para votar, perfecto para hacer lo que ningun ciudadano quiere admitir.",
                "Forma parte del bando traidor. Puede impedir que una victima hable o vote durante el dia siguiente.",
                "rol_mercenario_griego"
            ),
            Role(
                "Desertora",
                "Antigua Grecia",
                "Neutral",
                "Abandono la defensa de la polis cuando entendio que sus murallas protegian mas orgullo que vidas. No traiciono una bandera; se nego a morir por discursos escritos por hombres que nunca pisan el campo de batalla.",
                "No pertenece al pueblo ni a los traidores. Su objetivo es sobrevivir hasta el final de la partida.",
                "rol_desertor_griego"
            ),
            Role(
                "Oraculo",
                "Antigua Grecia",
                "Rol de Mapa",
                "La respetan porque habla con lo que nadie puede controlar. Pero su don no obedece a la politica ni al deseo del pueblo: puede traer de vuelta una verdad salvadora o una voz que incendie la ciudad desde la tumba.",
                "Resucita a un jugador muerto para el debate del dia siguiente, sin voto ni accion nocturna.",
                "rol_oraculo_griego"
            ),

            Role(
                "Aldeana",
                "Pueblo del Interior - 1915",
                "Pueblo",
                "Vive en un pueblucho donde el polvo entra por las ventanas y las noticias llegan tarde, si llegan. No espera justicia de nadie: ni del comisario, ni del patron, ni del cura. Aprendio que sobrevivir tambien es saber cuando cerrar la boca.",
                "No tiene habilidad especial. Participa en el debate y en las votaciones para descubrir a los traidores.",
                "rol_aldeano_gaucho"
            ),
            Role(
                "Detective",
                "Pueblo del Interior - 1915",
                "Pueblo",
                "No tiene oficina limpia ni placa respetada. Investiga entre pulperias, amenazas y expedientes que desaparecen cuando incomodan a alguien con poder. En este pueblo, encontrar la verdad es facil; lo dificil es que alguien se anime a decirla.",
                "Cada noche investiga a un jugador y recibe una pista sobre si parece inocente o sospechoso.",
                "rol_detective_gaucho"
            ),
            Role(
                "Medica",
                "Pueblo del Interior - 1915",
                "Pueblo",
                "Antes curaba heridas de hombres buscados por la ley, sin preguntar demasiado. Logro alejarse de esa vida, pero todavia reconoce el sonido de ciertos pasos en la puerta. Ahora cura al pueblo, aunque sabe que el pasado siempre vuelve montado y con sed.",
                "Cada noche protege a un jugador. Si ese jugador iba a morir, la eliminacion se cancela.",
                "rol_medico_gaucho"
            ),
            Role(
                "Alcalde",
                "Pueblo del Interior - 1915",
                "Pueblo",
                "Gobierna poco y firma mucho. Entre comisarios corruptos, favores politicos y patrones que hablan mas fuerte que la ley, intenta parecer autoridad en un pueblo donde todos saben quien manda de verdad.",
                "Puede revelar su identidad durante el debate. Desde ese momento, su voto vale doble.",
                "rol_alcalde_gaucho"
            ),
            Role(
                "Asesino",
                "Pueblo del Interior - 1915",
                "Asesino",
                "No mata por gloria ni por fe: mata porque la banda lo ordena y porque el miedo funciona mejor que cualquier discurso. Se cree parte de una justicia nueva, pero cada cuerpo que deja atras lo acerca mas a los mismos corruptos que dice odiar.",
                "Forma parte del bando traidor. Cada noche participa en la eleccion de la victima.",
                "rol_asesino_gaucho"
            ),
            Role(
                "Espia",
                "Pueblo del Interior - 1915",
                "Asesino",
                "Escucha detras de mostradores, en patios de tierra y en mesas donde el vino afloja la lengua. Nadie le presta demasiada atencion, y por eso mismo sabe mas que todos. En un pueblo chico, el secreto siempre pasa por alguien.",
                "Forma parte del bando traidor, pero ante la investigacion del Detective aparece como inocente.",
                "rol_espia_gaucho"
            ),
            Role(
                "Mercenario",
                "Pueblo del Interior - 1915",
                "Asesino",
                "No pregunta si el trabajo es justo, solo cuanto pagan y quien queda parado al final. Ha servido a comisarios, bandidos y patrones con la misma cara cansada. Su moral dura lo mismo que una bolsa de monedas.",
                "Forma parte del bando traidor. Puede impedir que una victima hable o vote durante el dia siguiente.",
                "rol_mercenario_gaucho"
            ),
            Role(
                "Desertora",
                "Pueblo del Interior - 1915",
                "Neutral",
                "Pertenecio a la banda el tiempo suficiente para entender que el codigo tenia letra chica. Se fue antes de terminar convertida en leyenda o cadaver. Desde entonces cambia de rumbo cada vez que escucha cascos acercarse.",
                "No pertenece al pueblo ni a los traidores. Su objetivo es sobrevivir hasta el final de la partida.",
                "rol_desertor_gaucho"
            ),
            Role(
                "Payador",
                "Pueblo del Interior - 1915",
                "Rol de Mapa",
                "Es el hazmerreir del pueblo, pero todos lo quieren un poco. Canta mal cuando esta sobrio y peor cuando esta inspirado, aunque sus coplas siempre terminan juntando gente. En casi todas repite la historia de aquel comisario viejo que una vez le pidio que le diera placer a su esposa mientras el miraba, y nadie sabe si creerle o reirse.",
                "Abre el debate de forma exclusiva, decide cuando inicia la votacion y en caso de empate usa el Voto de Gracia.",
                "rol_payador_gaucho"
            )
        )

        val recyclerView: RecyclerView = findViewById(R.id.rolesRecycler)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = RoleAdapter(this, rolesList)
    }
}
