param(
    [Parameter(Mandatory = $true)]
    [string]$IconSource,

    [Parameter(Mandatory = $true)]
    [string]$HeaderSource
)

Add-Type -AssemblyName System.Drawing

$assetDirectory = $PSScriptRoot
$maximumBytes = 1000000

function Get-VersionedPath {
    param([Parameter(Mandatory = $true)][string]$BasePath)

    if (-not (Test-Path -LiteralPath $BasePath)) {
        return $BasePath
    }

    $directory = [System.IO.Path]::GetDirectoryName($BasePath)
    $name = [System.IO.Path]::GetFileNameWithoutExtension($BasePath)
    $extension = [System.IO.Path]::GetExtension($BasePath)
    $version = 2

    do {
        $candidate = Join-Path $directory "$name-v$version$extension"
        $version++
    } while (Test-Path -LiteralPath $candidate)

    return $candidate
}

function Convert-ToPlayJpeg {
    param(
        [Parameter(Mandatory = $true)][string]$SourcePath,
        [Parameter(Mandatory = $true)][string]$OutputPath,
        [Parameter(Mandatory = $true)][int]$TargetWidth,
        [Parameter(Mandatory = $true)][int]$TargetHeight,
        [Parameter(Mandatory = $true)][long]$MaximumBytes
    )

    $sourceImage = [System.Drawing.Image]::FromFile($SourcePath)

    try {
        $bitmap = New-Object System.Drawing.Bitmap(
            $TargetWidth,
            $TargetHeight,
            [System.Drawing.Imaging.PixelFormat]::Format24bppRgb
        )

        try {
            $graphics = [System.Drawing.Graphics]::FromImage($bitmap)

            try {
                $graphics.Clear([System.Drawing.Color]::FromArgb(12, 12, 12))
                $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
                $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
                $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
                $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

                $sourceAspect = $sourceImage.Width / [double]$sourceImage.Height
                $targetAspect = $TargetWidth / [double]$TargetHeight

                if ($sourceAspect -gt $targetAspect) {
                    $cropHeight = $sourceImage.Height
                    $cropWidth = [int][Math]::Round($cropHeight * $targetAspect)
                    $cropX = [int][Math]::Floor(($sourceImage.Width - $cropWidth) / 2)
                    $cropY = 0
                }
                else {
                    $cropWidth = $sourceImage.Width
                    $cropHeight = [int][Math]::Round($cropWidth / $targetAspect)
                    $cropX = 0
                    $cropY = [int][Math]::Floor(($sourceImage.Height - $cropHeight) / 2)
                }

                $destinationRectangle = New-Object System.Drawing.Rectangle(
                    0,
                    0,
                    $TargetWidth,
                    $TargetHeight
                )

                $graphics.DrawImage(
                    $sourceImage,
                    $destinationRectangle,
                    $cropX,
                    $cropY,
                    $cropWidth,
                    $cropHeight,
                    [System.Drawing.GraphicsUnit]::Pixel
                )
            }
            finally {
                $graphics.Dispose()
            }

            $jpegEncoder = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() |
                Where-Object { $_.MimeType -eq "image/jpeg" }

            $selectedQuality = $null
            $selectedBytes = $null

            for ($quality = 95; $quality -ge 45; $quality -= 2) {
                $memory = New-Object System.IO.MemoryStream
                $encoderParameters = New-Object System.Drawing.Imaging.EncoderParameters(1)

                try {
                    $encoderParameters.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter(
                        [System.Drawing.Imaging.Encoder]::Quality,
                        [long]$quality
                    )

                    $bitmap.Save($memory, $jpegEncoder, $encoderParameters)

                    if ($memory.Length -le $MaximumBytes) {
                        $selectedQuality = $quality
                        $selectedBytes = $memory.ToArray()
                        break
                    }
                }
                finally {
                    $encoderParameters.Dispose()
                    $memory.Dispose()
                }
            }

            if ($null -eq $selectedBytes) {
                throw "No se pudo comprimir por debajo del límite de $MaximumBytes bytes."
            }

            [System.IO.File]::WriteAllBytes($OutputPath, $selectedBytes)

            return [PSCustomObject]@{
                Path = $OutputPath
                JpegQuality = $selectedQuality
            }
        }
        finally {
            $bitmap.Dispose()
        }
    }
    finally {
        $sourceImage.Dispose()
    }
}

$iconOutput = Get-VersionedPath (
    Join-Path $assetDirectory "bandido-games-developer-icon-512x512.jpg"
)
$headerOutput = Get-VersionedPath (
    Join-Path $assetDirectory "bandido-games-developer-header-4096x2304.jpg"
)

$generated = @(
    Convert-ToPlayJpeg `
        -SourcePath $IconSource `
        -OutputPath $iconOutput `
        -TargetWidth 512 `
        -TargetHeight 512 `
        -MaximumBytes $maximumBytes

    Convert-ToPlayJpeg `
        -SourcePath $HeaderSource `
        -OutputPath $headerOutput `
        -TargetWidth 4096 `
        -TargetHeight 2304 `
        -MaximumBytes $maximumBytes
)

$results = foreach ($entry in $generated) {
    $checkImage = [System.Drawing.Image]::FromFile($entry.Path)

    try {
        $file = Get-Item -LiteralPath $entry.Path

        [PSCustomObject]@{
            Path = $entry.Path
            Width = $checkImage.Width
            Height = $checkImage.Height
            PixelFormat = $checkImage.PixelFormat.ToString()
            MimeType = "image/jpeg"
            Bytes = $file.Length
            Kilobytes = [Math]::Round($file.Length / 1024, 1)
            JpegQuality = $entry.JpegQuality
            SHA256 = (Get-FileHash -LiteralPath $entry.Path -Algorithm SHA256).Hash
        }
    }
    finally {
        $checkImage.Dispose()
    }
}

$results | Format-List
