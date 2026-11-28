import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';

const PORT = Number(process.env.PORT || 3000);
const ROOT = process.cwd();
const APK_CANDIDATES = [
  path.join(ROOT, '.build-outputs', 'app-debug.apk'),
  path.join(ROOT, 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk')
];

function findApk() {
  for (const candidate of APK_CANDIDATES) {
    try {
      const stat = fs.statSync(candidate);
      if (stat.isFile()) {
        return { path: candidate, size: stat.size, mtime: stat.mtime };
      }
    } catch {
      // Ignore missing candidates
    }
  }
  return null;
}

function formatBytes(bytes) {
  if (!bytes) return '0 B';
  const mb = bytes / (1024 * 1024);
  return `${mb.toFixed(2)} MB`;
}

function renderHtml(apkInfo) {
  const apkBadge = apkInfo
    ? `<span class="badge ok">APK Hazır · ${formatBytes(apkInfo.size)}</span>`
    : `<span class="badge warn">APK Henüz Derlenmedi</span>`;

  const downloadAction = apkInfo
    ? `<a class="btn primary" href="/app-debug.apk" download="hiz-debug.apk">Debug APK İndir (app-debug.apk)</a>`
    : `<span class="hint">Derlemek için <code>gradle assembleDebug</code> çalıştırın.</span>`;

  return `<!doctype html>
<html lang="tr">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>Hız — Native Android Ekran Yansıtma</title>
  <style>
    :root {
      --bg: #090c11;
      --surface: #11161f;
      --elevated: #171e2a;
      --border: #253042;
      --cyan: #00d4ff;
      --green: #22c55e;
      --amber: #f59e0b;
      --text: #f3f6fb;
      --muted: #93a1b8;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      min-height: 100vh;
      background: radial-gradient(circle at top, #0f1b2b 0%, var(--bg) 60%);
      color: var(--text);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 20px;
    }
    .card {
      width: 100%;
      max-width: 720px;
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 20px;
      padding: 28px;
      box-shadow: 0 24px 60px rgba(0, 0, 0, 0.45);
    }
    .header {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      margin-bottom: 18px;
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .bolt {
      width: 44px;
      height: 44px;
      border-radius: 12px;
      background: var(--elevated);
      border: 1px solid rgba(0, 212, 255, 0.45);
      display: grid;
      place-items: center;
      color: var(--cyan);
      font-size: 22px;
      font-weight: 800;
    }
    h1 { font-size: 22px; font-weight: 800; }
    .sub { color: var(--muted); font-size: 13px; margin-top: 2px; }
    .badge {
      font-size: 12px;
      font-weight: 700;
      padding: 6px 12px;
      border-radius: 999px;
      border: 1px solid transparent;
    }
    .badge.ok {
      color: var(--green);
      background: rgba(34, 197, 94, 0.14);
      border-color: rgba(34, 197, 94, 0.4);
    }
    .badge.warn {
      color: var(--amber);
      background: rgba(245, 158, 11, 0.14);
      border-color: rgba(245, 158, 11, 0.4);
    }
    p.lead {
      color: var(--muted);
      font-size: 14px;
      line-height: 1.6;
      margin-bottom: 20px;
    }
    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
      gap: 12px;
      margin-bottom: 22px;
    }
    .tile {
      background: var(--elevated);
      border: 1px solid var(--border);
      border-radius: 14px;
      padding: 14px;
    }
    .tile-title {
      font-size: 12px;
      color: var(--cyan);
      font-weight: 700;
      margin-bottom: 6px;
    }
    .tile-body {
      font-size: 13px;
      color: var(--text);
      line-height: 1.45;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 12px;
    }
    .btn {
      display: inline-block;
      text-decoration: none;
      padding: 12px 18px;
      border-radius: 12px;
      font-size: 14px;
      font-weight: 700;
    }
    .btn.primary {
      background: var(--cyan);
      color: #051018;
    }
    .hint {
      color: var(--muted);
      font-size: 12px;
    }
    code {
      font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
      background: var(--bg);
      padding: 2px 6px;
      border-radius: 6px;
      color: var(--cyan);
    }
  </style>
</head>
<body>
  <main class="card">
    <div class="header">
      <div class="brand">
        <div class="bolt">⚡</div>
        <div>
          <h1>Hız — Native Android Uygulaması</h1>
          <div class="sub">Paket: <code>com.hiz.mirror</code> · Kotlin + Jetpack Compose</div>
        </div>
      </div>
      ${apkBadge}
    </div>
    <p class="lead">
      Bu proje tarayıcı tabanlı bir web uygulaması değil, yerel ağ (LAN) üzerinden çalışan tam kapsamlı bir
      <strong>Native Android (Telefon Verici &amp; Android TV Alıcı)</strong> ekran yansıtma projesidir.
      Önizleme sunucusu ortam uyumluluğu için port <code>${PORT}</code> üzerinde aktiftir.
    </p>
    <div class="grid">
      <div class="tile">
        <div class="tile-title">Açılış ve Tema</div>
        <div class="tile-body">Tam ekran <code>splash_lightning.mp4</code>, yedek şimşek ekranı, D-Pad TV odağı ve koyu antrasit tema.</div>
      </div>
      <div class="tile">
        <div class="tile-title">Verici &amp; Alıcı (LAN)</div>
        <div class="tile-body">UDP broadcast otomatik keşif, manuel IP/Port bağlantısı, <code>MediaProjection</code> + TCP soket aktarımı.</div>
      </div>
      <div class="tile">
        <div class="tile-title">Duyarlı Arayüz</div>
        <div class="tile-body">Yatay TV ve dikey telefon görünümlerinde taşmasız uyarlanabilir Jetpack Compose düzeni.</div>
      </div>
    </div>
    <div class="actions">
      ${downloadAction}
      <span class="hint">Kaynak dizin: <code>app/src/main/java/com/hiz/mirror</code></span>
    </div>
  </main>
</body>
</html>`;
}

const server = http.createServer((req, res) => {
  const urlPath = (req.url || '/').split('?')[0];

  if (urlPath === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({ status: 'ok', app: 'com.hiz.mirror' }));
    return;
  }

  if (urlPath === '/app-debug.apk') {
    const apk = findApk();
    if (!apk) {
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
      res.end('app-debug.apk bulunamadı.');
      return;
    }
    res.writeHead(200, {
      'Content-Type': 'application/vnd.android.package-archive',
      'Content-Length': apk.size,
      'Content-Disposition': 'attachment; filename="hiz-debug.apk"'
    });
    fs.createReadStream(apk.path).pipe(res);
    return;
  }

  const apkInfo = findApk();
  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
  res.end(renderHtml(apkInfo));
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`Hiz Android preview bridge listening on http://0.0.0.0:${PORT}`);
});
