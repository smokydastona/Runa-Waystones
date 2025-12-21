# Copilot Instructions — WaystoneButtonInjector

You are a Minecraft Forge mod development expert working on a client-side mod for Minecraft 1.20.1.

## Workflow After Every Code Change

**After ANY code change, you MUST follow this complete workflow:**

1. **Scan all files first** - Run error checking across the entire codebase
Do not stop after fixing a single file. Treat the mod as a single system:

Scan all files first and list all errors and inconsistencies.

Fix all errors systematically.

Re-validate after each fix to ensure no new errors are introduced.

Only stop when all files are completely correct and the pack would load in Minecraft without any errors or warnings.

For every fix, explain what was wrong, what you changed, and why. Once the mod passes 100% validation, summarize all changes and confirm it is fully functional.

Only consider the job done when the entire mod works as a system, not when just one file is fixed.

2. **Fix all errors systematically** - Address every error found, not just one file
3. **Re-validate after each fix** - Ensure no new errors were introduced
4. **Explain every change** - What was wrong, what you changed, and why
5. **Push to GitHub Actions** - Commit and push ONLY (no tagging)
6. **Only stop when 100% validated** - Continue until all files are completely correct and compile without errors

## Compilation Workflow

**NEVER build locally.** Always use GitHub Actions:

```bash
git add -A
git commit -m "descriptive message"
git push
```

This triggers GitHub Actions to compile the mod. The workspace must stay clean - no `build/` or `.gradle/` directories.

## Personal Info Guardrail (Mandatory)

**Goal:** never commit/push anything that contains personal machine paths, usernames, launcher args, tokens, UUIDs, or runtime logs.

Before **every** `git commit`, you MUST do all of the following:

1) **Refuse to commit any logs or runtime artifacts**
- Never commit: `*.log`, `crash.log`, `debug_logs/`, `logs/`, `run/`, `decompiled_working/`, `temp_decompile/`, `working_extract/`, `*.class`, `cfr.jar`.

2) **Scan staged changes for personal strings**
Run these commands from repo root and ensure they return **no matches**:

```powershell
git diff --cached --name-only

# Common personal/path leaks
git diff --cached | Select-String -Pattern "C:\\Users\\" -AllMatches
git diff --cached | Select-String -Pattern "curseforge\\minecraft\\Instances" -AllMatches

# Common launcher arg leaks (tokens/ids/usernames)
git diff --cached | Select-String -Pattern "--username" -AllMatches
git diff --cached | Select-String -Pattern "--uuid" -AllMatches
git diff --cached | Select-String -Pattern "--accessToken" -AllMatches
git diff --cached | Select-String -Pattern "--clientId" -AllMatches
git diff --cached | Select-String -Pattern "--xuid" -AllMatches
```

If there are matches: stop, remove/sanitize the content, and re-run the scan.

3) **Recommended enforcement: enable the repo pre-commit hook**
This repo includes a pre-commit hook that blocks committing common personal-info leaks.

One-time setup (per machine):

```powershell
git config core.hooksPath .githooks
```

## Log Handling (When You Say “Read The Log”)

**Rule:** When the user says **“read the log”**, always interpret it as:

1. **Immediately use PowerShell** (via the terminal) to **find the newest real runtime** `latest.log`.
2. **Read that newest log** (tail first; then full/filtered if needed).
3. **Do not** rely on a previously-copied log inside the repo unless the user explicitly says to.

Preferred PowerShell approach:

```powershell
$instancesRoot = Join-Path $env:USERPROFILE "curseforge\minecraft\Instances"
$newest = Get-ChildItem $instancesRoot -Directory |
	ForEach-Object { Join-Path $_.FullName "logs\latest.log" } |
	Where-Object { Test-Path $_ } |
	Get-Item |
	Sort-Object LastWriteTime -Descending |
	Select-Object -First 1

$newest.FullName
Get-Content $newest.FullName -Tail 400
```

## Project Architecture

- **Client-side only** - No server installation required
- **Built-in death/sleep detection** - `DeathSleepEvents.java` handles client-side detection
- **Per-server redirect mappings** - Config uses `death:source->dest` and `sleep:source->dest` format
- **Direct server connections** - No packets, no OP permissions needed
- **Waystone menu buttons** - Manual redirects via visible buttons in GUI
- **Mixin compatibility bypass** - `MixinClientPacketListener` forces vanilla connection to bypass mod checks

## Critical Rules

- ✅ **Scan → Fix → Validate → Push** after every change
- ✅ Treat the entire codebase as a system, not individual files
- ✅ Always push to GitHub Actions for compilation
- ❌ NEVER auto-tag releases without explicit user request
- ❌ NEVER build locally (no `gradlew build`)
- ❌ NEVER skip error validation before pushing

## Version Management

Version format: `4.0.{commits-since-4.0.0-bump}`
- Auto-increments from git commit history since the 4.0.0 bump
- Tags trigger GitHub Actions release workflow
- User decides when to tag, not the AI
