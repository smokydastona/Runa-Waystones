param(
    [string]$Path = "src/main/resources/assets/waystoneinjector/textures/slot/waystones.png",
    [switch]$Fix,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'

function Fail([string]$Message) {
    Write-Error $Message
    exit 1
}

function Info([string]$Message) {
    Write-Host $Message
}

if (-not (Test-Path $Path)) {
    Fail "Slot icon not found: $Path"
}

$fullPath = (Resolve-Path $Path).Path

function Get-PngSize([string]$PngPath) {
    # Parse PNG IHDR width/height directly (no image decoder / no caching).
    function Read-Int32BE([byte[]]$Bytes, [int]$Offset) {
        return (
            (($Bytes[$Offset]   -band 0xFF) -shl 24) -bor
            (($Bytes[$Offset+1] -band 0xFF) -shl 16) -bor
            (($Bytes[$Offset+2] -band 0xFF) -shl 8)  -bor
            (($Bytes[$Offset+3] -band 0xFF))
        )
    }

    $fs = [System.IO.File]::Open($PngPath, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
    try {
        $br = New-Object System.IO.BinaryReader($fs)
        $sig = $br.ReadBytes(8)
        $pngSig = [byte[]](137,80,78,71,13,10,26,10)
        for ($i=0; $i -lt 8; $i++) {
            if ($sig[$i] -ne $pngSig[$i]) {
                throw "Not a PNG file (bad signature)"
            }
        }

        $lenBytes = $br.ReadBytes(4)
        $typeBytes = $br.ReadBytes(4)
        $type = [System.Text.Encoding]::ASCII.GetString($typeBytes)
        if ($type -ne 'IHDR') {
            throw "Unexpected first chunk '$type' (expected IHDR)"
        }
        $ihdr = $br.ReadBytes(13)
        if ($ihdr.Length -ne 13) {
            throw "Truncated IHDR"
        }

        $w = Read-Int32BE $ihdr 0
        $h = Read-Int32BE $ihdr 4
        return @($w, $h)
    } finally {
        $fs.Dispose()
    }
}

try {
    $size = Get-PngSize $fullPath
    $w = [int]$size[0]
    $h = [int]$size[1]
} catch {
    Fail "Failed to read PNG header (may be corrupt): $fullPath ($($_.Exception.Message))"
}

Info "Slot icon: $Path (${w}x${h})"

# Curios docs recommend 16x16 for slot icons.
if ($w -eq 16 -and $h -eq 16) {
    Info "OK: Slot icon is 16x16."
    exit 0
}

if (-not $Fix) {
    Fail "Slot icon is ${w}x${h}. Recommended is 16x16. Re-export as 16x16 or run: .\\validate_slot_icon.ps1 -Fix"
}

if (-not $Force) {
    Info "About to overwrite $Path with a 16x16 resized version. Re-run with -Force to proceed."
    exit 1
}

try {
    Add-Type -AssemblyName PresentationCore | Out-Null
    Add-Type -AssemblyName WindowsBase | Out-Null
} catch {
    Fail "Unable to load WPF assemblies for resize/write: $($_.Exception.Message)"
}

# Resize to 16x16 (nearest-neighbor) and overwrite.
$srcStream = [System.IO.File]::Open($fullPath, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
try {
    $srcBmp = New-Object System.Windows.Media.Imaging.BitmapImage
    $srcBmp.BeginInit()
    $srcBmp.StreamSource = $srcStream
    $srcBmp.CacheOption = [System.Windows.Media.Imaging.BitmapCacheOption]::OnLoad
    $srcBmp.CreateOptions = [System.Windows.Media.Imaging.BitmapCreateOptions]::IgnoreImageCache
    $srcBmp.EndInit()
    $srcBmp.Freeze()
} finally {
    $srcStream.Dispose()
}

$scaleX = 16.0 / [double]$w
$scaleY = 16.0 / [double]$h

$transformed = New-Object System.Windows.Media.Imaging.TransformedBitmap
$transformed.BeginInit()
$transformed.Source = $srcBmp
$transformed.Transform = New-Object System.Windows.Media.ScaleTransform($scaleX, $scaleY)
$transformed.EndInit()

$dv = New-Object System.Windows.Media.DrawingVisual
$dc = $dv.RenderOpen()
[System.Windows.Media.RenderOptions]::SetBitmapScalingMode($dv, [System.Windows.Media.BitmapScalingMode]::NearestNeighbor)
$dc.DrawImage($transformed, (New-Object System.Windows.Rect(0, 0, 16, 16)))
$dc.Close()

$rtb = New-Object System.Windows.Media.Imaging.RenderTargetBitmap(16, 16, 96, 96, [System.Windows.Media.PixelFormats]::Pbgra32)
$rtb.Render($dv)

$encoder = New-Object System.Windows.Media.Imaging.PngBitmapEncoder
$encoder.Frames.Add([System.Windows.Media.Imaging.BitmapFrame]::Create($rtb))

$backup = "$fullPath.bak"
Copy-Item -LiteralPath $fullPath -Destination $backup -Force

$fs = [System.IO.File]::Open($fullPath, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
try {
    $encoder.Save($fs)
} finally {
    $fs.Dispose()
}

Info "Wrote resized 16x16 icon to $Path (backup at $backup)"
exit 0
