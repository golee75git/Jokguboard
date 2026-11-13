/* VISIT_STATS_PV: 조회(PV) +1. 환경변수 없는 공개 카운터. 되돌리: api/visit.js 삭제 */
var counterNs = "jokguboard-7k2m";

function todaySeoul() {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul" }).format(
    new Date()
  );
}

function hit(key) {
  var url =
    "https://abacus.jasoncameron.dev/hit/" +
    counterNs +
    "/" +
    encodeURIComponent(key);
  return fetch(url).then(function (response) {
    if (!response.ok) throw new Error("store");
    return response.json();
  });
}

module.exports = async function handler(req, res) {
  if (req.method !== "POST" && req.method !== "GET") {
    res.status(405).end();
    return;
  }
  try {
    var country = req.headers["x-vercel-ip-country"] || "XX";
    var bucket = country === "KR" ? "kr" : "intl";
    var day = todaySeoul();
    await Promise.all([hit(bucket + "-total"), hit(bucket + "-" + day)]);
    res.status(204).end();
  } catch (e) {
    res.status(503).end();
  }
};
