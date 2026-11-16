# Microsoft Word(Windows) 로 문서마다 쪽 수를 잰다(Word 기준 점검, docs/formats/test-corpus.md).
#
#   powershell -ExecutionPolicy Bypass -File scripts/word-pages.ps1 -List <문서 경로 목록> -Out <결과.tsv> [-Timeout 90]
#
# 문서마다 Word 를 따로 띄워(읽기 전용, 매크로 끔, 경고 끔) ComputeStatistics 로 쪽 수를 적는다(열 수 없으면 error).
# 시간을 넘기면 이 문서용으로 띄운 Word(시작 뒤에 생긴 자동화 인스턴스)만 닫고 timeout 으로 적는다.
# 사용자가 직접 연 Word 는 건드리지 않는다. PDF 내보내기는 Word 가 멈추는 문서가 있어 하지 않는다.
param([string]$List, [string]$Out, [int]$Timeout = 90, [string]$One = "")

if ($One) {
  $pages = "error"
  $w = $null
  try {
    $w = New-Object -ComObject Word.Application
    $w.Visible = $false
    $w.DisplayAlerts = 0
    $w.AutomationSecurity = 3
    # 암호 문서는 틀린 암호로 바로 실패하게 한다(대화 상자를 띄우지 않는다).
    $d = $w.Documents.Open($One, $false, $true, $false, "x")
    $pages = $d.ComputeStatistics(2)
    $d.Close(0)
  } catch {
    $pages = "error"
  } finally {
    if ($w) { $w.Quit() }
  }
  Add-Content -Path $Out -Value "$One`t$pages" -Encoding utf8
  exit 0
}

foreach ($file in Get-Content $List) {
  if (-not $file) { continue }
  $start = Get-Date
  # Start-Process 는 인자를 따옴표 없이 잇는다: 빈칸이 든 경로는 감싸 준다(안 그러면 그 문서는 결과에서 빠진다).
  $arguments = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "`"$PSCommandPath`"", "-One", "`"$file`"", "-Out", "`"$Out`"")
  $process = Start-Process powershell -ArgumentList $arguments -PassThru -WindowStyle Hidden
  if (-not $process.WaitForExit($Timeout * 1000)) {
    Stop-Process -Id $process.Id -Force -Confirm:$false -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
    Get-CimInstance Win32_Process -Filter "Name='WINWORD.EXE'" |
      Where-Object { $_.CreationDate -ge $start -and $_.CommandLine -like "*/Automation -Embedding*" } |
      ForEach-Object { Stop-Process -Id $_.ProcessId -Force -Confirm:$false -ErrorAction SilentlyContinue }
    Add-Content -Path $Out -Value "$file`ttimeout" -Encoding utf8
  }
}
