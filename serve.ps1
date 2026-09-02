# JK_SERVE: 로컬 정적 미리보기. 되돌리: 이 파일 삭제
param(
  [int] $Port = 8080
)
Set-Location $PSScriptRoot
$url = "http://127.0.0.1:$Port/jokgu_scoreboard.html"
Write-Host ""
Write-Host "  Jokguboard — local preview"
Write-Host "  URL:  $url"
Write-Host "  Stop: Ctrl+C"
Write-Host ""

if (Get-Command py -ErrorAction SilentlyContinue) {
  & py -3 -m http.server $Port
  exit $LASTEXITCODE
}
if (Get-Command python -ErrorAction SilentlyContinue) {
  & python -m http.server $Port
  exit $LASTEXITCODE
}

Write-Host "[오류] Python 3가 필요합니다."
exit 1
