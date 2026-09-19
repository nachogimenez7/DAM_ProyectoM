# Primera colección de logros con recompensas de perfil

Estado: propuesta de diseño aprobada; no implica implementación todavía.

Los primeros cuatro recursos visuales ya están preparados en
`assets/logros_perfil/primeras_sospechas/`. Son arte de perfil interno y no íconos para Google
Play Games.

## Objetivo

Sumar una colección inicial, pequeña y fácilmente entendible de logros que otorguen una
recompensa cosmética básica. No usa monedas, tienda ni estilos completos de perfil.

Cada logro entrega una sola recompensa. El perfil podrá mostrar, cuando exista soporte para
ello, un banner, una insignia y un título equipados.

## Colección: Primeras sospechas

| Logro | Condición visible y medible | Recompensa | Nombre de la recompensa |
| --- | --- | --- | --- |
| El que toca nunca baila | Ser elegido Traidor por primera vez. | Insignia | Máscara del Traidor |
| ¿No se puede ser maaas lento? | Ser el último jugador en votar en tres votaciones. | Título | El Lento |
| No aclares que oscurece | Votar por un Inocente que finalmente sea expulsado. | Insignia | Vela Apagada |
| Hermano Juramentado | Ganar una partida como Inocente por primera vez. | Banner | Juramento |
| Superviviente | Llegar vivo al final de tres partidas, sin importar qué equipo gane. | Banner | Amanecer |

## Dirección visual

### Insignias

Las insignias deben ser simples y leerse claramente a tamaño pequeño:

- **Máscara del Traidor:** máscara color borgoña sobre fondo oscuro.
- **Vela Apagada:** vela marfil con una sola voluta de humo sobre fondo azul oscuro.

Mantienen el aro fino dorado y los tonos rústicos ya usados por la interfaz. No llevan texto,
personajes ni fondos narrativos.

Archivos finales:

- `insignia_mascara_del_traidor.png`
- `insignia_vela_apagada.png`

### Banners

Los banners sí usan personajes, pero deben contar una única escena horizontal sencilla y sin
marcos incorporados:

- **Juramento:** una aldeana medieval y un gaucho se estrechan la mano al anochecer. Comunica
  confianza entre jugadores y permite mezclar los mundos del juego sin recargar la imagen.
- **Amanecer:** un gaucho superviviente descansa sobre un cerco de la pampa al amanecer, con una
  pequeña lámpara encendida. Comunica que llegó con vida al final.

En ambos casos se priorizan uno o dos personajes grandes, un fondo muy discreto y espacio libre
para que el banner siga funcionando detrás de la información del perfil.

Archivos finales:

- `banner_hermano_juramentado.png`
- `banner_superviviente.png`

## Límites iniciales

- Cinco logros y cinco recompensas en total.
- Dos banners, dos insignias y un título.
- Sin monedas, precios, tienda, rarezas ni recompensas aleatorias.
- No se añaden por ahora fondos, marcos de avatar ni temas globales del perfil.

## Pendiente para una fase posterior

1. Elegir la ubicación exacta del banner, insignia y título en el perfil.
2. Decidir si un jugador puede equipar solo una insignia o exhibir varias.
3. Adaptar o exportar los cuatro recursos a las dimensiones finales que use la interfaz, cuando
   se implemente el selector.
4. Definir una pantalla o aviso breve de desbloqueo.
