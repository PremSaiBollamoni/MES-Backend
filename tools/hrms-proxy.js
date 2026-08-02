// Reverse proxy for the HRMS biometric attendance portal (hrms.thegttech.com).
//
// Why this exists: the deployed backend (Render) cannot reach hrms.thegttech.com
// directly - the connection times out at the TCP level, most likely because that
// server only accepts connections from known/office IPs. This machine CAN reach it.
// Running this proxy here and tunneling it publicly (see README) lets the deployed
// backend reach HRMS indirectly, by pointing its HRMS_BASE_URL env var at the
// tunnel's public URL instead of hrms.thegttech.com directly.
//
// Usage: node hrms-proxy.js [port]   (defaults to 4000)
const http = require('http');
const https = require('https');

const TARGET_HOST = 'hrms.thegttech.com';
const PORT = process.argv[2] || process.env.PORT || 4000;

http.createServer((req, res) => {
  const options = {
    hostname: TARGET_HOST,
    port: 443,
    path: req.url,
    method: req.method,
    headers: { ...req.headers, host: TARGET_HOST },
  };

  const proxyReq = https.request(options, (proxyRes) => {
    res.writeHead(proxyRes.statusCode, proxyRes.headers);
    proxyRes.pipe(res, { end: true });
  });

  proxyReq.on('error', (err) => {
    res.writeHead(502, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'HRMS proxy error: ' + err.message }));
  });

  req.pipe(proxyReq, { end: true });
}).listen(PORT, () => {
  console.log(`HRMS proxy listening on http://localhost:${PORT}, forwarding to https://${TARGET_HOST}`);
});
