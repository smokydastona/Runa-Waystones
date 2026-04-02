[CmdletBinding()]
param(
	[switch]$Check,
	[string]$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
	[double]$EnglishFallbackThreshold = 0.90,
	[string]$LocaleManifestPath = (Join-Path $PSScriptRoot 'minecraft-1.20.1-locales.json')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

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
			$full -match '/src/main/resources/' -and $full -match '/lang/en_us\.json$'
		}
}

function Get-SupportedLocales {
	param([string]$ManifestPath)

	if (-not (Test-Path -LiteralPath $ManifestPath)) {
		throw "Locale manifest not found: ${ManifestPath}"
	}

	$manifest = Get-Content -LiteralPath $ManifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
	if (-not $manifest.locales) {
		throw "Locale manifest is missing the 'locales' array: ${ManifestPath}"
	}

	return @($manifest.locales | ForEach-Object { [string]$_ } | Sort-Object -Unique)
}

function Read-LangJsonOrdered {
	param([string]$Path)

	$text = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
	try {
		$doc = $text | ConvertFrom-Json
	} catch {
		throw "Invalid JSON in ${Path}: $($_.Exception.Message)"
	}

	$ordered = [ordered]@{}
	foreach ($prop in $doc.PSObject.Properties) {
		if ($null -eq $prop.Value -or $prop.Value -isnot [string]) {
			throw "Non-string value for key '$($prop.Name)' in $Path. Lang values must be strings."
		}
		$ordered[$prop.Name] = [string]$prop.Value
	}

	return $ordered
}

function Write-LangJsonOrdered {
	param(
		[Parameter(Mandatory=$true)][string]$Path,
		[Parameter(Mandatory=$true)][System.Collections.IDictionary]$Object
	)

	$json = ($Object | ConvertTo-Json -Depth 5) + "`n"
	[System.IO.File]::WriteAllText($Path, $json, (New-Object System.Text.UTF8Encoding($false)))
}

function Test-LangJsonMatchesOrdered {
	param(
		[System.Collections.IDictionary]$Existing,
		[System.Collections.IDictionary]$Expected
	)

	if ($null -eq $Existing) {
		return $false
	}

	$existingKeys = @($Existing.Keys)
	$expectedKeys = @($Expected.Keys)

	if ($existingKeys.Count -ne $expectedKeys.Count) {
		return $false
	}

	for ($index = 0; $index -lt $expectedKeys.Count; $index++) {
		$key = [string]$expectedKeys[$index]
		if ([string]$existingKeys[$index] -ne $key) {
			return $false
		}
		if (-not $Existing.Contains($key)) {
			return $false
		}
		if ([string]$Existing[$key] -ne [string]$Expected[$key]) {
			return $false
		}
	}

	return $true
}

function Test-TranslationGateExempt {
	param([string]$Locale)
	if ($Locale -match '^en_') { return $true }
	return $translationGateExempt.Contains($Locale)
}

function Get-LocaleFromFileName {
	param([System.IO.FileInfo]$File)
	return [System.IO.Path]::GetFileNameWithoutExtension($File.Name)
}

$supportedLocales = @(Get-SupportedLocales -ManifestPath $LocaleManifestPath)
if ($supportedLocales.Count -eq 0) {
	throw "No supported locales were loaded from $LocaleManifestPath"
}

$masters = @(Get-LangMasters -SearchRoot $Root)
if ($masters.Count -eq 0) {
	Write-Host "No lang/en_us.json masters found under '$Root'. Nothing to do."
	exit 0
}

$rewritesNeeded = New-Object System.Collections.Generic.List[string]
$translationGateFailures = New-Object System.Collections.Generic.List[string]
$unsupportedLocaleFiles = New-Object System.Collections.Generic.List[string]

foreach ($master in $masters) {
	$langDir = $master.Directory.FullName
	$enUs = Read-LangJsonOrdered -Path $master.FullName
	$enKeys = @($enUs.Keys)

	$localeFiles = @(Get-ChildItem -LiteralPath $langDir -File -Filter '*.json' | Sort-Object Name)
	$localeByCode = @{}
	foreach ($localeFile in $localeFiles) {
		$locale = Get-LocaleFromFileName -File $localeFile
		$localeByCode[$locale] = $localeFile
		if ($supportedLocales -notcontains $locale) {
			$unsupportedLocaleFiles.Add($localeFile.FullName) | Out-Null
		}
	}

	foreach ($locale in $supportedLocales) {
		$localeFile = $localeByCode[$locale]
		$existingMap = $null
		if ($null -eq $localeFile) {
			$localeFilePath = Join-Path $langDir ($locale + '.json')
			$localeMap = [ordered]@{}
			foreach ($key in $enKeys) {
				$localeMap[$key] = [string]$enUs[$key]
			}
		} else {
			$localeFilePath = $localeFile.FullName
			$localeMap = Read-LangJsonOrdered -Path $localeFilePath
			$existingMap = $localeMap
		}

		$newMap = [ordered]@{}
		foreach ($key in $enKeys) {
			if ($localeMap.Contains($key)) {
				$newMap[$key] = [string]$localeMap[$key]
			} else {
				$newMap[$key] = [string]$enUs[$key]
			}
		}

		if (-not (Test-LangJsonMatchesOrdered -Existing $existingMap -Expected $newMap)) {
			$rewritesNeeded.Add($localeFilePath) | Out-Null
			if (-not $Check) {
				Write-LangJsonOrdered -Path $localeFilePath -Object $newMap
			}
		}

		if ($locale -ne 'en_us' -and -not (Test-TranslationGateExempt -Locale $locale)) {
			$sameCount = 0
			foreach ($key in $enKeys) {
				$val = $null
				if ($newMap.Contains($key)) { $val = [string]$newMap[$key] }
				if ($null -eq $val -or $val -eq [string]$enUs[$key]) { $sameCount++ }
			}

			$ratio = if ($enKeys.Count -eq 0) { 0 } else { $sameCount / $enKeys.Count }
			if ($ratio -ge $EnglishFallbackThreshold) {
				$translationGateFailures.Add("$localeFilePath looks like English fallback ($([Math]::Round($ratio * 100, 2))% matches en_us)") | Out-Null
			}
		}
	}
}

if ($Check) {
	if ($unsupportedLocaleFiles.Count -gt 0) {
		Write-Error ("Unsupported locale files found. Remove them or add them to $LocaleManifestPath.`n- " + ($unsupportedLocaleFiles -join "`n- "))
	}
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