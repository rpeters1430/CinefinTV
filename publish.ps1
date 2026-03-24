[CmdletBinding()]
param(
    [string]$NewVersionName
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$BuildFile = Join-Path $PSScriptRoot "app/build.gradle.kts"
$WorkflowFile = ".github/workflows/release.yml"
$GradleWrapper = Join-Path $PSScriptRoot "gradlew.bat"
$script:BackupFile = $null

function Restore-BuildFile {
    if ($script:BackupFile -and (Test-Path $script:BackupFile)) {
        Copy-Item $script:BackupFile $BuildFile -Force
        Remove-Item $script:BackupFile -Force
        $script:BackupFile = $null
    }
}

function Clear-Backup {
    if ($script:BackupFile -and (Test-Path $script:BackupFile)) {
        Remove-Item $script:BackupFile -Force
        $script:BackupFile = $null
    }
}

function Test-ReleaseSigning {
    return (
        -not [string]::IsNullOrWhiteSpace($env:CINEFIN_RELEASE_STORE_FILE) -and
        -not [string]::IsNullOrWhiteSpace($env:CINEFIN_RELEASE_STORE_PASSWORD) -and
        -not [string]::IsNullOrWhiteSpace($env:CINEFIN_RELEASE_KEY_ALIAS) -and
        -not [string]::IsNullOrWhiteSpace($env:CINEFIN_RELEASE_KEY_PASSWORD)
    )
}

function Invoke-Git {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    & git @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed with exit code $LASTEXITCODE."
    }
}

function Test-GitTagExists {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Tag
    )

    $tagMatch = (& git tag --list -- $Tag | Select-Object -First 1)
    return -not [string]::IsNullOrWhiteSpace($tagMatch)
}

function Assert-CleanWorktree {
    & git diff --quiet --ignore-submodules --
    if ($LASTEXITCODE -ne 0) {
        throw "Working tree has uncommitted changes. Commit or stash them before publishing."
    }

    & git diff --cached --quiet --ignore-submodules --
    if ($LASTEXITCODE -ne 0) {
        throw "Index has staged changes. Commit or stash them before publishing."
    }
}

function Assert-MainBranch {
    $branch = (& git rev-parse --abbrev-ref HEAD).Trim()
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to determine current git branch."
    }

    if ($branch -ne "main") {
        throw "Publish from main. Current branch: $branch"
    }
}

function Get-BuildFileText {
    return [System.IO.File]::ReadAllText($BuildFile)
}

function Set-BuildFileText {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Content
    )

    [System.IO.File]::WriteAllText($BuildFile, $Content, [System.Text.UTF8Encoding]::new($false))
}

function Get-VersionCode {
    $match = [regex]::Match((Get-BuildFileText), 'versionCode = (\d+)')
    if (-not $match.Success) {
        throw "Unable to locate versionCode in $BuildFile"
    }

    return [int]$match.Groups[1].Value
}

function Get-VersionName {
    $match = [regex]::Match((Get-BuildFileText), 'versionName = "([^"]+)"')
    if (-not $match.Success) {
        throw "Unable to locate versionName in $BuildFile"
    }

    return $match.Groups[1].Value
}

function Get-NextPatchVersionName {
    param(
        [Parameter(Mandatory = $true)]
        [string]$VersionName
    )

    $parts = $VersionName.Split(".")
    if ($parts.Length -lt 3) {
        return $VersionName
    }

    $lastIndex = $parts.Length - 1
    $patchValue = 0
    if (-not [int]::TryParse($parts[$lastIndex], [ref]$patchValue)) {
        return $VersionName
    }

    $parts[$lastIndex] = ($patchValue + 1).ToString()
    return ($parts -join ".")
}

function Update-BuildVersion {
    param(
        [Parameter(Mandatory = $true)]
        [int]$OldCode,
        [Parameter(Mandatory = $true)]
        [int]$NewCode,
        [Parameter(Mandatory = $true)]
        [string]$OldName,
        [Parameter(Mandatory = $true)]
        [string]$NewName
    )

    $content = Get-BuildFileText
    $content = $content -replace "versionCode = $([regex]::Escape($OldCode.ToString()))", "versionCode = $NewCode"
    $content = $content -replace "versionName = `"$([regex]::Escape($OldName))`"", "versionName = `"$NewName`""
    Set-BuildFileText -Content $content
}

try {
    Assert-CleanWorktree
    Assert-MainBranch

    $oldVersionCode = Get-VersionCode
    $oldVersionName = Get-VersionName
    $suggestedVersionName = Get-NextPatchVersionName -VersionName $oldVersionName

    Write-Host "Current version: $oldVersionName (code $oldVersionCode)"

    if ([string]::IsNullOrWhiteSpace($NewVersionName)) {
        $enteredVersionName = Read-Host "Enter new version name [current: $oldVersionName | next suggested: $suggestedVersionName | next build code: $($oldVersionCode + 1)]"
        if ([string]::IsNullOrWhiteSpace($enteredVersionName)) {
            $NewVersionName = $suggestedVersionName
            Write-Host "Using suggested version: $NewVersionName"
        } else {
            $NewVersionName = $enteredVersionName.Trim()
        }
    }

    if ([string]::IsNullOrWhiteSpace($NewVersionName)) {
        throw "Version name cannot be empty."
    }

    if ($NewVersionName -eq $oldVersionName) {
        throw "New version name must differ from the current version."
    }

    $newVersionCode = $oldVersionCode + 1
    $tag = "v$NewVersionName"

    if (Test-GitTagExists -Tag $tag) {
        throw "Tag $tag already exists."
    }

    $script:BackupFile = [System.IO.Path]::GetTempFileName()
    Copy-Item $BuildFile $script:BackupFile -Force

    Write-Host "Bumping to: $NewVersionName (code $newVersionCode)"
    Update-BuildVersion -OldCode $oldVersionCode -NewCode $newVersionCode -OldName $oldVersionName -NewName $NewVersionName

    if (Test-ReleaseSigning) {
        Write-Host "Building signed release APK locally"
        & $GradleWrapper :app:assembleRelease
    } else {
        Write-Host "Release signing variables not set locally; building debug APK for a compile check only"
        & $GradleWrapper :app:assembleDebug
    }

    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE."
    }

    Invoke-Git -Arguments @("add", $BuildFile)
    Invoke-Git -Arguments @("commit", "-m", "chore: bump version to $NewVersionName")
    Invoke-Git -Arguments @("tag", $tag)

    Clear-Backup

    Write-Host "Pushing main and tag $tag"
    Invoke-Git -Arguments @("push", "origin", "main")
    Invoke-Git -Arguments @("push", "origin", $tag)

    Write-Host "-------------------------------------------------------"
    Write-Host "Published $tag."
    Write-Host "GitHub Actions will build app-release.apk and update updates/version.json after the release job succeeds."
    Write-Host "Workflow: $WorkflowFile"
    Write-Host "-------------------------------------------------------"
}
catch {
    Restore-BuildFile
    throw
}
