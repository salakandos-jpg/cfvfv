const express = require("express");
const { randomUUID } = require("crypto");

const app = express();
app.use(express.json());

app.use((_req, res, next) => {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET,POST,DELETE,OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");
  next();
});

const worlds = new Map();
const WORLD_TTL_MS = 90_000;

function cleanStale() {
  const now = Date.now();
  for (const [id, w] of worlds.entries()) {
    if (now - w.lastHeartbeat > WORLD_TTL_MS) worlds.delete(id);
  }
}
setInterval(cleanStale, 30_000);

// GET  /api/worlds        — список публичных миров
app.get("/api/worlds", (_req, res) => {
  cleanStale();
  res.json(Array.from(worlds.values()).map((w) => ({
    id: w.id, name: w.name, host: w.host, ip: w.ip, port: w.port,
    playerCount: w.playerCount, maxPlayers: w.maxPlayers,
    hasPassword: !!(w.password && w.password.length > 0),
    createdAt: w.createdAt,
  })));
});

// POST /api/worlds        — зарегистрировать мир
app.post("/api/worlds", (req, res) => {
  const b = req.body;
  if (!b.ip || !b.port) {
    return res.status(400).json({ error: "ip и port обязательны" });
  }
  const now = Date.now();
  const id = randomUUID();
  const entry = {
    id, name: b.name || `${b.host || "Игрок"}'s World`,
    host: b.host || "Unknown", ip: b.ip, port: Number(b.port),
    playerCount: b.playerCount ?? 1, maxPlayers: b.maxPlayers ?? 20,
    password: b.password || "", createdAt: now, lastHeartbeat: now,
  };
  worlds.set(id, entry);
  res.status(201).json({ id, ...entry });
});

// POST /api/worlds/:id/heartbeat — обновить онлайн
app.post("/api/worlds/:id/heartbeat", (req, res) => {
  const w = worlds.get(req.params.id);
  if (!w) return res.status(404).json({ error: "Мир не найден" });
  if (typeof req.body.playerCount === "number") w.playerCount = req.body.playerCount;
  w.lastHeartbeat = Date.now();
  res.json({ ok: true });
});

// DELETE /api/worlds/:id  — удалить мир
app.delete("/api/worlds/:id", (req, res) => {
  res.json({ ok: worlds.delete(req.params.id) });
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`WorldShare backend on port ${PORT}`));
