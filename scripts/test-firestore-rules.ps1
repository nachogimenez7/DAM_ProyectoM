$ErrorActionPreference = "Stop"

$androidStudioJbr = "C:\Program Files\Android\Android Studio\jbr"
if (Test-Path $androidStudioJbr) {
    $env:JAVA_HOME = $androidStudioJbr
    $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
}

npm run test:firestore-rules
