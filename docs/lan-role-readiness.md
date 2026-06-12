# Espera de lectura de roles en LAN

El modo LAN actual todavia no sincroniza dispositivos. Esta regla queda definida
para conectarla cuando exista un anfitrion autoritativo y mensajes entre clientes.

## Regla

- Cada cliente muestra su carta y envia `ROLE_REVEAL_READY` al tocar `ENTENDIDO`.
- Nadie puede iniciar la partida antes del tiempo minimo protegido.
- Mientras espera, un jugador listo ve `Aun hay jugadores leyendo su carta` y el
  progreso `listos / conectados`.
- El anfitrion decide el inicio usando `RoleRevealGate`; los clientes no adelantan
  la partida localmente.
- Al iniciar, el anfitrion envia un instante comun de comienzo de la primera fase.
- La identidad de quienes siguen leyendo no se publica. Solo se comparte el total.
- El tiempo individual de lectura nunca se usa como senal publica ni se registra
  en el historial de la partida.

## Modos

- `WAIT_FOR_ALL`: comienza despues del minimo cuando todos confirmaron.
- `BALANCED`: comienza cuando todos confirmaron o al alcanzar 30 segundos.
- `QUICK`: comienza al terminar el minimo protegido, configurado por defecto en
  10 segundos.

Los limites son configurables mediante `RoleRevealConfig`. El valor inicial
recomendado es `BALANCED`, con 10 segundos minimos y 30 segundos maximos.

## Pendiente de integracion

La capa LAN debera mantener un conjunto de identificadores de jugadores conectados
y otro de jugadores listos. Las desconexiones deben quitar al jugador de ambos
conjuntos antes de volver a evaluar la regla.
