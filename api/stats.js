/* VISIT_STATS_PV: 국내/국외·오늘/전체 PV. 환경변수 없는 공개 카운터. 되돌리: api/stats.js 삭제 */
var counterNs = "jokguboard-7k2m";

function todaySeoul() {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul" }).format(
    new Date()
  );
}

function num(v) {
  var n = parseInt(v, 10);
  return isNaN(n) || n < 0 ? 0 : n;
}

function readCount(key) {
  var url =
    "https://abacus.jasoncameron.dev/get/" +
    counterNs +
    "/" +
    encodeURIComponent(key);
  return fetch(url).then(function (response) {
    if (response.status === 404) return 0;
    if (!response.ok) throw new Error("store");
    return response.json().then(function (body) {
      return num(body && body.value);
    });
  });
}

module.exports = async function handler(req, res) {
  if (req.method !== "GET") {
    res.status(405).end();
    return;
  }
  res.setHeader("Cache-Control", "no-store");
  try {
    var day = todaySeoul();
    var vals = await Promise.all([
      readCount("kr-total"),
      readCount("intl-total"),
      readCount("kr-" + day),
      readCount("intl-" + day),
    ]);
    res.status(200).json({
      domestic: { today: vals[2], total: vals[0] },
      foreign: { today: vals[3], total: vals[1] },
    });
  } catch (e) {
    res.status(200).json({
      domestic: { today: 0, total: 0 },
      foreign: { today: 0, total: 0 },
    });
  }
};
