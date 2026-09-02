/* JK_WIN_CHECK: 코어 규칙 회귀. 빌드 의존 없음 */
function isGameWin(b, w) {
  if (b === w) return false;
  var hi = Math.max(b, w);
  var lo = Math.min(b, w);
  if (hi >= 15 && lo <= 13) return true;
  if (b >= 14 && w >= 14 && hi - lo >= 2) return true;
  return false;
}
function isMatchSeriesWon(sb, sw) {
  return sb >= 2 || sw >= 2;
}

var fails = [];
function expect(name, got, want) {
  if (got !== want) fails.push(name + " got=" + got + " want=" + want);
}

expect("14:13 open", isGameWin(14, 13), false);
expect("13:14 open", isGameWin(13, 14), false);
expect("15:13 win", isGameWin(15, 13), true);
expect("13:15 win", isGameWin(13, 15), true);
expect("15:14 deuce continue", isGameWin(15, 14), false);
expect("14:14 deuce", isGameWin(14, 14), false);
expect("16:14 win", isGameWin(16, 14), true);
expect("14:16 win", isGameWin(14, 16), true);
expect("15:0 win", isGameWin(15, 0), true);
expect("series 2-0", isMatchSeriesWon(2, 0), true);
expect("series 1-1", isMatchSeriesWon(1, 1), false);
expect("series 0-2", isMatchSeriesWon(0, 2), true);

if (fails.length) {
  console.error(fails.join("\n"));
  process.exit(1);
}
console.log("win-rules ok");
