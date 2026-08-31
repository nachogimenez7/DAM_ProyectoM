# Las sesiones viajan por Intent y savedInstanceState mediante Serializable. Sus nombres de
# clase y campos forman parte del formato, pero sus metodos pueden seguir optimizandose.
-keepnames class com.traidores.juego.** implements java.io.Serializable
-keepclassmembers,allowoptimization class com.traidores.juego.** implements java.io.Serializable {
    <fields>;
}

# Firebase y Credential Manager aportan sus reglas de consumidor.
-keepattributes Signature,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
