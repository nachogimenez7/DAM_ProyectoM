# Modo historia — diseño (borrador 1, 7/10/2026)

Cómo se juega el modo historia, antes de escribir ninguna historia. Lo que está en
**Decidido** lo eligió el usuario; lo marcado **Propuesta** queda para revisar.

## Decidido

| Tema | Decisión |
|---|---|
| Historias | Tres, una por mapa (Pampa, Grecia, Medieval), cortas y simples |
| Personajes | Propios, inventados para el juego. A lo sumo Martina Chapanay en la Pampa |
| Protagonista | Fijo por historia |
| Formato | Escenas con decisiones más desafíos; capítulos de 3–4 min, unos 5 capítulos, 15–20 min por historia |
| Finales | 2 o 3 por historia |
| Tono | Oscuro y tenso, con algo de humor en los personajes |
| Presentación | Novela visual: imagen del lugar, retrato de quien habla, caja de texto abajo |
| Desafíos | Partidas con objetivo especial e interrogatorios |
| Consecuencias | Ocultas, con un aviso corto cuando una decisión va a pesar («Dora recordará esto») |
| Derrota | Nunca hay «Game over»: perder lleva a otra rama |
| Desbloqueo | En orden: Pampa → Grecia → Medieval |
| Premios | En el perfil (banner por historia, título por final) |
| Guion | Lo escribimos juntos |
| Imágenes | Generadas antes y empaquetadas en la app; sin costo por jugador |

## 1. Ciclo de un capítulo

Cada capítulo alterna tres momentos y termina con un gancho al siguiente.

1. **Escena** — narración y diálogo. Tocás para avanzar; pocas líneas por pantalla.
2. **Decisión** — 2 o 3 opciones. Algunas tienen efecto inmediato; otras dejan una marca oculta.
3. **Desafío** — una partida con objetivo especial o un interrogatorio.

**Propuesta:** un capítulo tiene como máximo un desafío, y la historia tiene 2 partidas y 1–2
interrogatorios en total. El resto son escenas y decisiones.

## 2. Consecuencias (marcas)

- Cada decisión puede encender **marcas**: `confia_en_benitez`, `dora_viva`, `sabe_tu_secreto`.
- Las marcas deciden qué escena sigue, cómo arranca una partida y qué final toca.
- Cuando una decisión deja una marca importante aparece un aviso de una línea, sin números.
- **Propuesta:** las marcas son verdadero/falso o un contador chico (0–3), nada más; así cada
  historia sigue siendo fácil de escribir y de probar.

## 3. Partidas con objetivo especial

Son partidas reales contra la IA, armadas por la historia.

**Lo que la historia controla:**
- Mapa, cantidad de jugadores y nombres (los personajes de la historia se sientan a la mesa).
- Tu rol y, si hace falta, algunos roles fijos («el capataz es el Asesino»).
- Cómo arranca la partida según tus marcas: con un aliado que te cree, con una pista, con
  alguien que ya sospecha de vos.

**Tipos de objetivo (Propuesta):**
| Objetivo | Ejemplo |
|---|---|
| Ganar | La clásica |
| Proteger | «Que Dora siga viva al terminar» |
| Descubrir | «Que el pueblo expulse al traidor antes de la noche 3» |
| Ocultarse | «Ganá sin que nadie te acuse en público» |
| Sobrevivir | «Llegá vivo al tercer día» |

Cumplir o fallar el objetivo lleva a ramas distintas. Las partidas de la historia dependen de
la IA nueva (`ANALISIS_IA_ANDROID.md`): se programan cuando los bots jueguen bien.

## 4. Interrogatorios

Una escena de deducción corta, sin bots ni azar.

- Un sospechoso, un tema («¿dónde estabas anoche?») y 3–4 preguntas para elegir, con un límite
  de preguntas.
- Cada respuesta agrega una frase a tus **notas**. Algunas se contradicen entre sí o con una pista.
- Para cerrar, **marcás la contradicción** (tocás las dos frases que no cierran) o acusás.
- Acertar da una marca a favor (una pista para la próxima partida); fallar no corta nada, solo
  cambia la rama.
- **Propuesta:** sin reloj, para que se pueda pensar; con 1 o 2 intentos para marcar la contradicción.

## 5. Finales y premios

- 2 o 3 finales por historia, según las marcas y el resultado de los desafíos.
- Pantalla de final con la imagen, un cierre corto y el premio desbloqueado.
- **Propuesta de premios:** banner de la historia al terminarla por primera vez y un título por
  cada final (los tres finales completan la historia al 100 %). Se ven en el perfil junto a los
  logros actuales.
- Al terminar, una pantalla de **resumen** con los finales vistos y los que faltan (sin revelar
  cuáles son), para invitar a rejugar.

## 6. Progreso y guardado

- Se guarda al empezar cada capítulo; podés salir y seguir donde quedaste.
- **Propuesta:** «Elegir capítulo» después de terminar la historia, para buscar los otros finales
  sin rejugar todo desde el principio.

## 7. Dónde vive en la app

- Menú principal → JUGAR → una entrada nueva **MODO HISTORIA**, junto a «Jugar contra IA».
- Pantalla de historias: las tres tarjetas por mapa; las bloqueadas, apagadas con «Terminá X para abrirla».

## 8. Formato de datos (para Android e iOS)

Una historia es un archivo de datos que leen las dos apps; escribir un capítulo no requiere programar.

```json
{
  "id": "pampa",
  "chapters": [{
    "id": "c1",
    "nodes": [
      { "id": "c1_s1", "type": "scene", "background": "pampa_pulperia_noche",
        "lines": [{ "speaker": "narrador", "text": "…" }], "next": "c1_d1" },
      { "id": "c1_d1", "type": "choice",
        "options": [
          { "text": "Contarle al comisario", "set": { "confia_en_benitez": true }, "next": "c1_s2" },
          { "text": "Guardar la carta", "set": { "tiene_carta": true }, "notice": "Guardaste la carta de Anselmo.", "next": "c1_s2" }
        ] },
      { "id": "c2_m1", "type": "match", "map": "pampa", "players": 6, "role": "policia",
        "objective": { "kind": "protect", "target": "dora" },
        "success": "c2_s3", "failure": "c2_s3b" }
    ]
  }],
  "endings": [{ "id": "justicia", "when": { "benitez_expulsado": true }, "reward": "titulo_el_justo" }]
}
```

Escenas, decisiones, interrogatorios y partidas son nodos del mismo grafo. Un validador verifica
que todos los caminos lleguen a un final y que ninguna marca se use sin haberse definido.

## 9. Orden de trabajo propuesto

1. Revisar este documento (decisiones marcadas **Propuesta**).
2. **Capítulo de prueba jugable** con imágenes provisorias: una escena, una decisión, un
   interrogatorio y un final. Sirve para sentir si es divertido antes de producir historias.
3. Escribir juntos la historia de la Pampa (esquema de capítulos → texto).
4. Generar las imágenes de la Pampa (fondos y retratos con un estilo fijo por personaje).
5. Partidas con objetivo, cuando la IA nueva esté lista.
6. Grecia y Medieval.

## Pendiente de decidir

- Si la Pampa usa a Martina Chapanay o a una protagonista propia inspirada en ella.
- Música y sonidos propios del modo, o los del juego.
- Si los premios incluyen algo más que cosméticos (por ejemplo, un rol nuevo).
