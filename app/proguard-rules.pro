# Las sesiones viajan por Intent y savedInstanceState mediante Serializable.
# Mantener nombres y campos permite restaurarlas entre builds ofuscados.
-keep class com.traidores.juego.** implements java.io.Serializable { *; }

# Firebase y Credential Manager aportan sus reglas de consumidor.
-keepattributes Signature,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
