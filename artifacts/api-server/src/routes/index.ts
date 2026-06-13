import { Router, type IRouter } from "express";
import healthRouter from "./health";
import worldhostRouter from "./worldhost";

const router: IRouter = Router();

router.use(healthRouter);
router.use(worldhostRouter);

export default router;
