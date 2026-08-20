Add-Type -AssemblyName System.Drawing

$sourcePath = 'C:\Users\Nacho\OneDrive\Desktop\Facultad\testeo TRAIDORES\Google Play\Traidores_Grafico_Fondo_v1.png'
$outputPath = 'C:\Users\Nacho\OneDrive\Desktop\Facultad\testeo TRAIDORES\Google Play\Traidores_Grafico_Funciones_TRES_MAPAS_1024x500_v2.jpg'
$fontPath = 'C:\Users\Nacho\OneDrive\Desktop\Facultad\testeo TRAIDORES\app\src\main\res\font\cinzel.ttf'

$source = [System.Drawing.Image]::FromFile($sourcePath)
$canvas = New-Object System.Drawing.Bitmap 1024, 500
$canvas.SetResolution(96, 96)
$graphics = [System.Drawing.Graphics]::FromImage($canvas)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

$destination = New-Object System.Drawing.Rectangle 0, 0, 1024, 500
$sourceRect = New-Object System.Drawing.Rectangle 0, 8, $source.Width, ($source.Height - 16)

function Draw-TintedSection {
    param(
        [System.Drawing.Point[]]$Points,
        [single]$Red,
        [single]$Green,
        [single]$Blue,
        [single]$Brightness
    )

    $state = $graphics.Save()
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddPolygon($Points)
    $graphics.SetClip($path)

    $matrix = New-Object System.Drawing.Imaging.ColorMatrix
    $matrix.Matrix00 = $Red
    $matrix.Matrix11 = $Green
    $matrix.Matrix22 = $Blue
    $matrix.Matrix33 = 1.0
    $matrix.Matrix40 = $Brightness
    $matrix.Matrix41 = $Brightness
    $matrix.Matrix42 = $Brightness
    $matrix.Matrix44 = 1.0

    $attributes = New-Object System.Drawing.Imaging.ImageAttributes
    $attributes.SetColorMatrix($matrix)
    $graphics.DrawImage($source, $destination, $sourceRect.X, $sourceRect.Y, $sourceRect.Width, $sourceRect.Height, [System.Drawing.GraphicsUnit]::Pixel, $attributes)

    $attributes.Dispose()
    $path.Dispose()
    $graphics.Restore($state)
}

$left = @(
    (New-Object System.Drawing.Point 0, 0),
    (New-Object System.Drawing.Point 378, 0),
    (New-Object System.Drawing.Point 326, 500),
    (New-Object System.Drawing.Point 0, 500)
)
$middle = @(
    (New-Object System.Drawing.Point 378, 0),
    (New-Object System.Drawing.Point 724, 0),
    (New-Object System.Drawing.Point 676, 500),
    (New-Object System.Drawing.Point 326, 500)
)
$right = @(
    (New-Object System.Drawing.Point 724, 0),
    (New-Object System.Drawing.Point 1024, 0),
    (New-Object System.Drawing.Point 1024, 500),
    (New-Object System.Drawing.Point 676, 500)
)

Draw-TintedSection -Points $left -Red 0.78 -Green 0.86 -Blue 1.02 -Brightness -0.035
Draw-TintedSection -Points $middle -Red 1.08 -Green 1.04 -Blue 0.91 -Brightness 0.005
Draw-TintedSection -Points $right -Red 1.08 -Green 0.92 -Blue 0.72 -Brightness -0.005

$topOverlay = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(148, 0, 0, 0))
$bottomOverlay = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(170, 0, 0, 0))
$graphics.FillRectangle($topOverlay, 0, 0, 1024, 130)
$graphics.FillRectangle($bottomOverlay, 0, 407, 1024, 93)

$shadowPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(190, 0, 0, 0)), 9
$whitePen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(235, 255, 255, 255)), 4
$graphics.DrawLine($shadowPen, 366, 132, 338, 406)
$graphics.DrawLine($whitePen, 364, 132, 336, 406)
$graphics.DrawLine($shadowPen, 713, 132, 687, 406)
$graphics.DrawLine($whitePen, 711, 132, 685, 406)

$fontCollection = New-Object System.Drawing.Text.PrivateFontCollection
$fontCollection.AddFontFile($fontPath)
$fontFamily = $fontCollection.Families[0]
$titleFont = New-Object System.Drawing.Font $fontFamily, 68, ([System.Drawing.FontStyle]::Regular), ([System.Drawing.GraphicsUnit]::Pixel)
$mapFont = New-Object System.Drawing.Font $fontFamily, 19, ([System.Drawing.FontStyle]::Regular), ([System.Drawing.GraphicsUnit]::Pixel)
$taglineFont = New-Object System.Drawing.Font $fontFamily, 30, ([System.Drawing.FontStyle]::Regular), ([System.Drawing.GraphicsUnit]::Pixel)
$centered = New-Object System.Drawing.StringFormat
$centered.Alignment = [System.Drawing.StringAlignment]::Center
$centered.LineAlignment = [System.Drawing.StringAlignment]::Center

$gold = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 222, 175, 82))
$cream = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 244, 230, 192))
$white = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(245, 255, 255, 255))
$textShadow = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(220, 0, 0, 0))

$graphics.DrawString('TRAIDORES', $titleFont, $textShadow, (New-Object System.Drawing.RectangleF 3, 18, 1024, 100), $centered)
$graphics.DrawString('TRAIDORES', $titleFont, $gold, (New-Object System.Drawing.RectangleF 0, 14, 1024, 100), $centered)

$labelY = 372
$labelBg = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(165, 0, 0, 0))
$graphics.FillRectangle($labelBg, 38, $labelY, 250, 34)
$graphics.FillRectangle($labelBg, 407, $labelY, 225, 34)
$graphics.FillRectangle($labelBg, 745, $labelY, 240, 34)
$graphics.DrawString('MAPA MEDIEVAL', $mapFont, $white, (New-Object System.Drawing.RectangleF 38, $labelY, 250, 34), $centered)
$graphics.DrawString('MAPA GRIEGO', $mapFont, $white, (New-Object System.Drawing.RectangleF 407, $labelY, 225, 34), $centered)
$graphics.DrawString('MAPA PAMPEANO', $mapFont, $white, (New-Object System.Drawing.RectangleF 745, $labelY, 240, 34), $centered)

$graphics.DrawString('¿EN QUIÉN VAS A CONFIAR?', $taglineFont, $textShadow, (New-Object System.Drawing.RectangleF 2, 425, 1024, 62), $centered)
$graphics.DrawString('¿EN QUIÉN VAS A CONFIAR?', $taglineFont, $cream, (New-Object System.Drawing.RectangleF 0, 422, 1024, 62), $centered)

$encoder = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object { $_.MimeType -eq 'image/jpeg' }
$parameters = New-Object System.Drawing.Imaging.EncoderParameters 1
$parameters.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter ([System.Drawing.Imaging.Encoder]::Quality), 94L
$canvas.Save($outputPath, $encoder, $parameters)

$parameters.Dispose()
$titleFont.Dispose()
$mapFont.Dispose()
$taglineFont.Dispose()
$fontCollection.Dispose()
$centered.Dispose()
$gold.Dispose()
$cream.Dispose()
$white.Dispose()
$textShadow.Dispose()
$labelBg.Dispose()
$topOverlay.Dispose()
$bottomOverlay.Dispose()
$shadowPen.Dispose()
$whitePen.Dispose()
$graphics.Dispose()
$canvas.Dispose()
$source.Dispose()
