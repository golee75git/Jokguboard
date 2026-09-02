/* JK_WIN_CHECK: 코어 규칙 회귀. 빌드 의존 없음 */
function isGameWin(b, w) {
  if (b === w) return false;
  var hi = Math.max(b, w);
  var lo = Math.min(b, w);
  if (hi >= 15 && lo <= 13) return true;
  if (b >= 14 && w >= 14 && hi - lo >= 2) return true;
  return false;
}
function isFutnetSetWon(my, opp) {
  if (my >= 15) return true;
  return my >= 11 && my - opp >= 2;
}
function isSetWon(sport, my, opp) {
  if (sport === "futnet") return isFutnetSetWon(my, opp);
  return isGameWin(my, opp) && my > opp;
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

expect("jokgu 15:13 set", isSetWon("jokgu", 15, 13), true);
expect("jokgu 13:15 set", isSetWon("jokgu", 13, 15), false);
expect("jokgu 15:14 open", isSetWon("jokgu", 15, 14), false);
expect("jokgu 16:14 set", isSetWon("jokgu", 16, 14), true);

expect("futnet 11:9 win", isSetWon("futnet", 11, 9), true);
expect("futnet 9:11 lose", isSetWon("futnet", 9, 11), false);
expect("futnet 10:10 open", isSetWon("futnet", 10, 10), false);
expect("futnet 11:10 open", isSetWon("futnet", 11, 10), false);
expect("futnet 12:10 win", isSetWon("futnet", 12, 10), true);
expect("futnet 14:14 open", isSetWon("futnet", 14, 14), false);
expect("futnet 15:14 cap", isSetWon("futnet", 15, 14), true);
expect("futnet 14:15 lose", isSetWon("futnet", 14, 15), false);
expect("futnet 15:13 cap", isSetWon("futnet", 15, 13), true);
expect("futnet 8:0 open", isSetWon("futnet", 8, 0), false);

function bumpIdx(idx, n) {
  if (n < 2) return idx;
  return (idx + 1) % n;
}
var sb = 0, sw = 0, n = 4;
expect("hold serve same idx", sb, 0);
sb = bumpIdx(sb, n);
expect("sideout first-serve team next", sb, 1);
expect("receive first serve idx", sw, 0);
sw = bumpIdx(sw, n);
expect("first-serve team back on 2", sb, 1);
expect("receive team next", sw, 1);
sb = bumpIdx(sb, n);
expect("wrap step 3", sb, 2);
sb = bumpIdx(sb, n);
expect("wrap step 4", sb, 3);
sb = bumpIdx(sb, n);
expect("wrap to 1", sb, 0);

if (fails.length) {
  console.error(fails.join("\n"));
  process.exit(1);
}
console.log("win-rules ok");
