import { Router } from "express";
import { randomBytes } from "crypto";

const router = Router();

interface WorldEntry {
  code: string;
  ownerName: string;
  motd: string;
  address: string;
  port: number;
  maxPlayers: number;
  onlinePlayers: number;
  gameMode: string;
  allowCheats: boolean;
  registeredAt: number;
  lastHeartbeat: number;
}

const worlds = new Map<string, WorldEntry>();

const STALE_MS = 60_000;

function pruneStale() {
  const now = Date.now();
  for (const [code, w] of worlds) {
    if (now - w.lastHeartbeat > STALE_MS) worlds.delete(code);
  }
}

setInterval(pruneStale, 15_000);

router.post("/worldhost/register", (req, res) => {
  const { ownerName, motd, address, port, maxPlayers, onlinePlayers, gameMode, allowCheats } = req.body;

  if (!ownerName || !address || !port) {
    res.status(400).json({ error: "ownerName, address and port are required" });
    return;
  }

  const existing = [...worlds.values()].find(
    w => w.address === address && w.port === port
  );
  if (existing) {
    existing.motd = motd ?? existing.motd;
    existing.maxPlayers = maxPlayers ?? existing.maxPlayers;
    existing.onlinePlayers = onlinePlayers ?? existing.onlinePlayers;
    existing.lastHeartbeat = Date.now();
    res.json({ code: existing.code });
    return;
  }

  const code = randomBytes(3).toString("hex").toUpperCase();
  const entry: WorldEntry = {
    code,
    ownerName: String(ownerName),
    motd: String(motd ?? "Добро пожаловать!"),
    address: String(address),
    port: Number(port),
    maxPlayers: Number(maxPlayers ?? 20),
    onlinePlayers: Number(onlinePlayers ?? 1),
    gameMode: String(gameMode ?? "SURVIVAL"),
    allowCheats: Boolean(allowCheats ?? false),
    registeredAt: Date.now(),
    lastHeartbeat: Date.now(),
  };
  worlds.set(code, entry);
  res.json({ code });
});

router.post("/worldhost/heartbeat/:code", (req, res) => {
  const w = worlds.get(req.params.code);
  if (!w) { res.status(404).json({ error: "Not found" }); return; }
  w.lastHeartbeat = Date.now();
  w.onlinePlayers = req.body.onlinePlayers ?? w.onlinePlayers;
  res.json({ ok: true });
});

router.delete("/worldhost/close/:code", (req, res) => {
  worlds.delete(req.params.code);
  res.json({ ok: true });
});

router.get("/worldhost/list", (_req, res) => {
  pruneStale();
  const list = [...worlds.values()].map(w => ({
    code: w.code,
    ownerName: w.ownerName,
    motd: w.motd,
    address: w.address,
    port: w.port,
    maxPlayers: w.maxPlayers,
    onlinePlayers: w.onlinePlayers,
    gameMode: w.gameMode,
    allowCheats: w.allowCheats,
    registeredAt: w.registeredAt,
  }));
  res.json({ worlds: list });
});

router.get("/worldhost/world/:code", (req, res) => {
  const w = worlds.get(req.params.code);
  if (!w) { res.status(404).json({ error: "Not found" }); return; }
  res.json(w);
});

export default router;
