const http = require("http");
const express = require("express");
const { WebSocketServer, WebSocket } = require("ws");
const { randomUUID } = require("crypto");

const app = express();
app.use(express.json());

app.use((_req, res, next) => {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET,POST,DELETE,OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");
  if (_req.method === "OPTIONS") return res.sendStatus(204);
  next();
});

// ── World registry ──────────────────────────────────────────────────────────
const worlds = new Map();
const WORLD_TTL_MS = 90_000;

function cleanStale() {
  const now = Date.now();
  for (const [id, w] of worlds.entries()) {
    if (now - w.lastHeartbeat > WORLD_TTL_MS) worlds.delete(id);
  }
}
setInterval(cleanStale, 30_000);

// ── Relay state ─────────────────────────────────────────────────────────────
// worldId → WebSocket (host control channel)
const hostControls = new Map();
// sessionId → { clientWs?, hostWs?, bridged }
const sessions = new Map();

// ── HTTP server (needed so WS can share the same port) ──────────────────────
const server = http.createServer(app);
const wss = new WebSocketServer({ server, path: undefined });

wss.on("connection", (ws, req) => {
  const url = new URL(req.url, "http://localhost");
  const path = url.pathname;

  // ── HOST control channel ─────────────────────────────────────────────────
  if (path === "/ws/host-register") {
    const worldId = url.searchParams.get("worldId");
    if (!worldId) return ws.close(1008, "Missing worldId");

    console.log(`[relay] host connected worldId=${worldId}`);
    hostControls.set(worldId, ws);
    ws.isAlive = true;

    ws.on("pong", () => { ws.isAlive = true; });
    ws.on("close", () => {
      if (hostControls.get(worldId) === ws) hostControls.delete(worldId);
      console.log(`[relay] host disconnected worldId=${worldId}`);
    });
    ws.on("error", (e) => console.error(`[relay] host error: ${e.message}`));
    ws.send(JSON.stringify({ type: "registered", worldId }));
    return;
  }

  // ── HOST data stream for a session ──────────────────────────────────────
  if (path === "/ws/host-stream") {
    const sessionId = url.searchParams.get("sessionId");
    if (!sessionId) return ws.close(1008, "Missing sessionId");

    let sess = sessions.get(sessionId);
    if (!sess) { sess = { bridged: false }; sessions.set(sessionId, sess); }
    sess.hostWs = ws;
    ws.on("close", () => closeSession(sessionId, "host"));
    ws.on("error", () => closeSession(sessionId, "host"));
    tryBridge(sessionId);
    return;
  }

  // ── CLIENT data stream for a session ────────────────────────────────────
  if (path === "/ws/client-stream") {
    const sessionId = url.searchParams.get("sessionId");
    if (!sessionId) return ws.close(1008, "Missing sessionId");

    let sess = sessions.get(sessionId);
    if (!sess) { sess = { bridged: false }; sessions.set(sessionId, sess); }
    sess.clientWs = ws;
    ws.on("close", () => closeSession(sessionId, "client"));
    ws.on("error", () => closeSession(sessionId, "client"));
    tryBridge(sessionId);
    return;
  }

  ws.close(1008, "Unknown path");
});

function tryBridge(sessionId) {
  const sess = sessions.get(sessionId);
  if (!sess || sess.bridged || !sess.hostWs || !sess.clientWs) return;
  sess.bridged = true;
  console.log(`[relay] bridged sessionId=${sessionId}`);

  // Forward binary and text frames in both directions
  sess.clientWs.on("message", (data, isBinary) => {
    if (sess.hostWs.readyState === WebSocket.OPEN)
      sess.hostWs.send(data, { binary: isBinary });
  });
  sess.hostWs.on("message", (data, isBinary) => {
    if (sess.clientWs.readyState === WebSocket.OPEN)
      sess.clientWs.send(data, { binary: isBinary });
  });
}

function closeSession(sessionId, side) {
  const sess = sessions.get(sessionId);
  if (!sess) return;
  sessions.delete(sessionId);
  console.log(`[relay] session closed (${side}) sessionId=${sessionId}`);
  try { sess.hostWs?.close(); } catch (_) {}
  try { sess.clientWs?.close(); } catch (_) {}
}

// Keep host control channels alive with periodic ping
setInterval(() => {
  for (const [worldId, ws] of hostControls) {
    if (!ws.isAlive) {
      console.log(`[relay] host timed out worldId=${worldId}`);
      ws.terminate();
      hostControls.delete(worldId);
    } else {
      ws.isAlive = false;
      ws.ping();
    }
  }
}, 25_000);

// ── REST API ─────────────────────────────────────────────────────────────────

app.get("/api/worlds", (_req, res) => {
  cleanStale();
  res.json(Array.from(worlds.values()).map((w) => ({
    id: w.id, name: w.name, host: w.host, ip: w.ip, port: w.port,
    playerCount: w.playerCount, maxPlayers: w.maxPlayers,
    hasPassword: !!(w.password && w.password.length > 0),
    createdAt: w.createdAt,
  })));
});

app.post("/api/worlds", (req, res) => {
  const b = req.body;
  const now = Date.now();
  const id = randomUUID();
  const entry = {
    id, name: b.name || `${b.host || "Игрок"}'s World`,
    host: b.host || "Unknown",
    ip: b.ip || "relay",
    port: Number(b.port) || 25565,
    playerCount: b.playerCount ?? 1, maxPlayers: b.maxPlayers ?? 20,
    password: b.password || "", createdAt: now, lastHeartbeat: now,
  };
  worlds.set(id, entry);
  res.status(201).json({ id, ...entry });
});

app.post("/api/worlds/:id/heartbeat", (req, res) => {
  const w = worlds.get(req.params.id);
  if (!w) return res.status(404).json({ error: "Мир не найден" });
  if (typeof req.body.playerCount === "number") w.playerCount = req.body.playerCount;
  w.lastHeartbeat = Date.now();
  res.json({ ok: true });
});

app.delete("/api/worlds/:id", (req, res) => {
  res.json({ ok: worlds.delete(req.params.id) });
});

// POST /api/connect — client requests a relay session with a host
app.post("/api/connect", (req, res) => {
  const { worldId } = req.body;
  if (!worldId) return res.status(400).json({ error: "worldId обязателен" });

  const hostWs = hostControls.get(worldId);
  if (!hostWs || hostWs.readyState !== WebSocket.OPEN) {
    return res.status(503).json({ error: "Хост не подключён к ретранслятору. Убедитесь, что хост запустил мир через WorldShare." });
  }

  const sessionId = randomUUID();
  sessions.set(sessionId, { bridged: false });

  hostWs.send(JSON.stringify({ type: "new_connection", sessionId }));
  console.log(`[relay] session created worldId=${worldId} sessionId=${sessionId}`);

  res.json({ sessionId });
});

// GET /api/relay-status/:worldId — check if host relay is connected
app.get("/api/relay-status/:worldId", (req, res) => {
  const ws = hostControls.get(req.params.worldId);
  res.json({ connected: !!(ws && ws.readyState === WebSocket.OPEN) });
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => console.log(`WorldShare backend+relay on port ${PORT}`));
