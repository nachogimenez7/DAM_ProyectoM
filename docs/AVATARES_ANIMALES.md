# Avatares animales — iOS y Android

Los 14 retratos usan la lámina aprobada por el usuario, incluida Mamona y los tres retratos
mirando a la derecha. La lámina se generó con la herramienta integrada de imágenes, tomando
como referencia las cartas pampeanas y las fotos de Mamona. El atlas cuadrado `atlas_animales_v2.png` corrige el encuadre y elimina marcos vecinos
mediante edición con la herramienta integrada de imágenes. El prompt pide conservar los
14 animales, el estilo y Mamona, en una grilla 4×4 sin marcos ni textos; las dos últimas
celdas son paisaje. No se vuelve a generar el arte al exportarlo: `swift tools/export_animal_avatars.swift` empaqueta los mismos píxeles para
Android e iOS. Fuente y exportaciones: `assets/avatars_animales/`.

## Identidad pública

Los identificadores compartidos son `avatar_carpincho`, `avatar_buho`, `avatar_cuervo`,
`avatar_lobo`, `avatar_mamona`, `avatar_liebre`, `avatar_puma`, `avatar_zorzal`,
`avatar_calandria`, `avatar_hornero`, `avatar_zorro`, `avatar_yaguarete`, `avatar_nandu`,
`avatar_yacare`. Viajan en el campo existente `avatarPerfil`. No requieren cambios de reglas
ni de protocolo. No se publica el rol secreto en la imagen del avatar.

Una instalación nueva elige un animal al azar una sola vez y lo conserva. Elegir otro animal
lo reemplaza. Las fotos personales siguen teniendo prioridad. Los antiguos avatares de roles
se migran de forma determinista a animales; el rol favorito conserva su catálogo de roles.

## Plantel fijo compartido

| Bot original | Animal |
|---|---|
| Thiago | Carpincho |
| Mora | Búho |
| Lautaro | Cuervo |
| Valen | Lobo |
| Rami | Mamona |
| Juli | Liebre |
| Santi | Puma |
| Mili | Zorzal |
| Toto | Calandria |
| Agus | Hornero |
| Bruno | Zorro |
| Lola | Yaguareté |
| Fede | Ñandú |
| Cata | Yacaré |

El avatar pertenece al integrante del plantel, no al nombre visible ni al rol asignado.
Por ejemplo, renombrar a Agus como Lucas conserva el hornero. Android conserva esa identidad
por slot y por el perfil guardado en la sesión. iOS mantiene las claves junto a los bots de
la sala y guarda `avatarKey` en cada `ClassicPlayer`; las partidas antiguas usan su slot como
respaldo. Quitar a otro bot no cambia los avatares de los restantes.

## Verificación

- Kotlin: `ProfileAvatarCatalogTest` (plantel completo, claves y migración).
- Android debug: `AnimalAvatarSmokeActivity`, pruebas con preferencias aisladas para
  asignación inicial, persistencia, elección manual y dos cambios de nombre consecutivos.
  Extra `screen=selector` abre el selector real de avatares; sin extra muestra los 14
  mediante `GameplayAvatarView`. Reporte: caché `animal_avatar_qa.txt`.
- Swift: `AnimalAvatarTests` (identidades, nombres diferentes, claves explícitas y guardados).
- iOS UI: `testLocalProfileSavesNameAvatarBannerAndFavorite`, actualizado para seleccionar
  un animal y verificar que el rol favorito siga siendo independiente.
