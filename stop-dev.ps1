$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

foreach ($name in @('backend', 'frontend')) {
    $pidFile = Join-Path $root ".$name.pid"
    if (-not (Test-Path -LiteralPath $pidFile)) {
        Write-Host "$name is not running (PID file not found)"
        continue
    }

    $rootPid = [int](Get-Content -LiteralPath $pidFile -Raw)
    $processes = Get-CimInstance Win32_Process
    $ids = [System.Collections.Generic.List[int]]::new()
    $ids.Add($rootPid)
    $index = 0
    while ($index -lt $ids.Count) {
        $parentId = $ids[$index]
        foreach ($child in $processes | Where-Object { [int]$_.ParentProcessId -eq $parentId }) {
            $childId = [int]$child.ProcessId
            if (-not $ids.Contains($childId)) { $ids.Add($childId) }
        }
        $index++
    }

    foreach ($id in ($ids | Sort-Object -Descending)) {
        Stop-Process -Id $id -Force -ErrorAction SilentlyContinue
    }
    Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
    Write-Host "$name stopped"
}
