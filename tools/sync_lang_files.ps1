[CmdletBinding()]
param(
	[switch]$Check,
	[string]$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
	[double]$EnglishFallbackThreshold = 0.90
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($PSVersionTable.PSVersion.Major -lt 7) {
	throw "sync_lang_files.ps1 requires PowerShell 7+ (pwsh). Install from https://learn.microsoft.com/powershell/ and rerun with 'pwsh -File ./tools/sync_lang_files.ps1'."
}

# Locales that are exempt from the translation-coverage gate.
# - English variants (en_*) are exempt by default.
# - Novelty/pseudo locales: en_ud (upside-down), qya_aa (pirate), lol_us (lolcat).
$translationGateExempt = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
@(
	'en_ud',
	'qya_aa',
	'lol_us'
) | ForEach-Object { [void]$translationGateExempt.Add($_) }

function Get-LangMasters {
	param([string]$SearchRoot)
	Get-ChildItem -Path $SearchRoot -Recurse -File -Filter 'en_us.json' |
		Where-Object {
			$full = $_.FullName -replace '\\', '/'
			$full -match '/lang/en_us\.json$'
		}
}

function Read-LangJsonOrdered {
	param([string]$Path)

	$text = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
	try {
		$doc = [System.Text.Json.JsonDocument]::Parse($text)
	} catch {
		throw "Invalid JSON in $Path: $($_.Exception.Message)"
	}

	if ($doc.RootElement.ValueKind -ne [System.Text.Json.JsonValueKind]::Object) {
		throw "Expected top-level JSON object in $Path"
	}

	$ordered = [ordered]@{}
	foreach ($prop in $doc.RootElement.EnumerateObject()) {
		if ($prop.Value.ValueKind -ne [System.Text.Json.JsonValueKind]::String) {
			throw "Non-string value for key '$($prop.Name)' in $Path. Lang values must be strings."
		}
		$ordered[$prop.Name] = $prop.Value.GetString()
	}

	$doc.Dispose()
	return $ordered
}

function Write-LangJsonOrdered {
	param(
		[Parameter(Mandatory=$true)][string]$Path,
		[Parameter(Mandatory=$true)][System.Collections.IDictionary]$Object
	)

	$options = [System.Text.Json.JsonWriterOptions]::new()
	$options.Indented = $true
	$options.Encoder = [System.Text.Encodings.Web.JavaScriptEncoder]::UnsafeRelaxedJsonEscaping

	$stream = New-Object System.IO.MemoryStream
	$writer = [System.Text.Json.Utf8JsonWriter]::new($stream, $options)

	$writer.WriteStartObject()
	foreach ($key in $Object.Keys) {
		$writer.WriteString([string]$key, [string]$Object[$key])
	}
	$writer.WriteEndObject()
	$writer.Flush()
	$writer.Dispose()

	# Ensure trailing newline and UTF-8 without BOM.
	$bytes = $stream.ToArray()
	$stream.Dispose()
	$json = [System.Text.Encoding]::UTF8.GetString($bytes) + "`n"
	[System.IO.File]::WriteAllText($Path, $json, (New-Object System.Text.UTF8Encoding($false)))
}

function Is-TranslationGateExempt {
	param([string]$Locale)
	if ($Locale -match '^en_') { return $true }
	return $translationGateExempt.Contains($Locale)
}

function Get-LocaleFromFileName {
	param([System.IO.FileInfo]$File)
	return [System.IO.Path]::GetFileNameWithoutExtension($File.Name)
}

$masters = @(Get-LangMasters -SearchRoot $Root)
if ($masters.Count -eq 0) {
	Write-Host "No lang/en_us.json masters found under '$Root'. Nothing to do."
	exit 0
}

$rewritesNeeded = New-Object System.Collections.Generic.List[string]
$translationGateFailures = New-Object System.Collections.Generic.List[string]

foreach ($master in $masters) {
	$langDir = $master.Directory.FullName
	$enUs = Read-LangJsonOrdered -Path $master.FullName
	$enKeys = @($enUs.Keys)

	$localeFiles = @(Get-ChildItem -LiteralPath $langDir -File -Filter '*.json' | Sort-Object Name)
	if ($localeFiles.Count -eq 0) {
		continue
	}

	foreach ($localeFile in $localeFiles) {
		$locale = Get-LocaleFromFileName -File $localeFile
		$localeMap = Read-LangJsonOrdered -Path $localeFile.FullName

		# Build canonical locale object with master key order.
		$newMap = [ordered]@{}
		foreach ($key in $enKeys) {
			if ($localeMap.Contains($key)) {
				$newMap[$key] = [string]$localeMap[$key]
			} else {
				$newMap[$key] = [string]$enUs[$key]
			}
		}

		# Determine whether this file would be rewritten.
		$tmpPath = Join-Path ([System.IO.Path]::GetTempPath()) ("lang_sync_{0}_{1}.json" -f $locale, [System.Guid]::NewGuid().ToString('N'))
		Write-LangJsonOrdered -Path $tmpPath -Object $newMap
		$newText = Get-Content -LiteralPath $tmpPath -Raw -Encoding UTF8
		Remove-Item -LiteralPath $tmpPath -Force

		$oldText = Get-Content -LiteralPath $localeFile.FullName -Raw -Encoding UTF8
		if ($oldText -ne $newText) {
			$rewritesNeeded.Add($localeFile.FullName) | Out-Null
			if (-not $Check) {
				Write-LangJsonOrdered -Path $localeFile.FullName -Object $newMap
			}
		}

		# Translation coverage gate: fail if locale looks like English fallback.
		if ($locale -ne 'en_us' -and -not (Is-TranslationGateExempt -Locale $locale)) {
			$sameCount = 0
			foreach ($key in $enKeys) {
				$val = $null
				if ($localeMap.Contains($key)) { $val = [string]$localeMap[$key] }
				if ($null -eq $val -or $val -eq [string]$enUs[$key]) { $sameCount++ }
			}
			$ratio = if ($enKeys.Count -eq 0) { 0 } else { $sameCount / $enKeys.Count }
			if ($ratio -ge $EnglishFallbackThreshold) {
				$translationGateFailures.Add("$($localeFile.FullName) looks like English fallback ($([Math]::Round($ratio*100,2))% matches en_us)") | Out-Null
			}
		}
	}
}

if ($Check) {
	if ($rewritesNeeded.Count -gt 0) {
		Write-Error ("Lang files are out of sync with en_us.json. Run: pwsh -File ./tools/sync_lang_files.ps1`nWould rewrite:`n- " + ($rewritesNeeded -join "`n- "))
	}
	if ($translationGateFailures.Count -gt 0) {
		Write-Error ("Translation coverage gate failed (non-exempt locales still look like English fallback). Please provide real translations (keys unchanged, values translated).`n" + ($translationGateFailures -join "`n"))
	}
	Write-Host "Lang sync check passed."
	exit 0
}

if ($rewritesNeeded.Count -gt 0) {
	Write-Host "Updated lang files:" 
	$rewritesNeeded | ForEach-Object { Write-Host "- $_" }
} else {
	Write-Host "Lang files already up to date."
}
