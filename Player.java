import static org.lwjgl.opengl.GL11.*;

public class Player {
    public float x, y, z;
    public float yaw = 0, pitch = 0;

    private float vy = 0;
    private boolean onGround = false;

    private static final float EYE_HEIGHT = 1.62f;
    private static final float FULL_HEIGHT = 1.8f;
    private static final float HALF_WIDTH = 0.3f;
    private static final float GRAVITY = 0.008f;
    private static final float JUMP_VELOCITY = 0.15f;
    private static final float TERMINAL_VELOCITY = -0.5f;
    private static final float WALK_SPEED = 0.07f;
    private static final float SNEAK_SPEED = 0.04f;
    private static final float REACH_DISTANCE = 5.0f;
    private static final float RAY_STEP = 0.02f;

    public boolean keyW, keyS, keyA, keyD, keySpace, keyShift;
    public int selectedBlock = 1;

    private float breakProgress = 0;
    private int breakX = Integer.MIN_VALUE;
    private int breakY = Integer.MIN_VALUE;
    private int breakZ = Integer.MIN_VALUE;

    public static boolean showDebug = false;

    private final World world;

    public Player(float x, float y, float z, World world) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.world = world;
    }

    public void rotate(float dyaw, float dpitch) {
        yaw += dyaw;
        pitch = Math.max(-89, Math.min(89, pitch + dpitch));
    }

    // ──────────────────────────────────────────────
    //  UPDATE
    // ──────────────────────────────────────────────

    public void update(boolean breaking) {
        float speed = keyShift ? SNEAK_SPEED : WALK_SPEED;

        // --- движение по направлению взгляда ---
        float sin = (float) Math.sin(Math.toRadians(yaw));
        float cos = (float) Math.cos(Math.toRadians(yaw));

        float dx = 0, dz = 0;
        if (keyW) { dx += sin; dz -= cos; }
        if (keyS) { dx -= sin; dz += cos; }
        if (keyA) { dx -= cos; dz -= sin; }
        if (keyD) { dx += cos; dz += sin; }

        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len > 0) {
            dx = dx / len * speed;
            dz = dz / len * speed;
        }

        // --- горизонтальное движение (каждая ось отдельно) ---
        if (dx != 0) {
            float newX = x + dx;
            if (!collidesAt(newX, y, z)) {
                x = newX;
            } else {
                // скольжение: прижимаемся к стене
                x = slideX(x, dx, y, z);
            }
        }
        if (dz != 0) {
            float newZ = z + dz;
            if (!collidesAt(x, y, newZ)) {
                z = newZ;
            } else {
                z = slideZ(x, z, dz, y);
            }
        }

        // --- прыжок ---
        if (keySpace && onGround) {
            vy = JUMP_VELOCITY;
            onGround = false;
        }

        // --- гравитация ---
        vy -= GRAVITY;
        if (vy < TERMINAL_VELOCITY) vy = TERMINAL_VELOCITY;

        // --- вертикальное движение ---
        float newY = y + vy;

        if (vy <= 0) {
            // падаем — ищем пол
            float floor = findFloor(x, z, y, newY);
            if (floor > newY) {
                y = floor;
                vy = 0;
                onGround = true;
            } else {
                y = newY;
                onGround = false;
            }
        } else {
            // летим вверх — ищем потолок
            if (collidesAt(x, newY, z)) {
                vy = 0;
                // прижимаемся к потолку
                y = snapCeiling(x, y, z);
            } else {
                y = newY;
                onGround = false;
            }
        }

        // --- void ---
        if (y < -20) respawn();

        // --- ломание блоков ---
        updateBreaking(breaking);
    }

    // ──────────────────────────────────────────────
    //  AABB COLLISION
    // ──────────────────────────────────────────────

    /**
     * Проверяет, пересекает ли AABB игрока хотя бы один твёрдый блок.
     * AABB: [px ± HALF_WIDTH,  py .. py+FULL_HEIGHT,  pz ± HALF_WIDTH]
     */
    private boolean collidesAt(float px, float py, float pz) {
        int minBX = (int) Math.floor(px - HALF_WIDTH);
        int maxBX = (int) Math.floor(px + HALF_WIDTH);
        int minBY = (int) Math.floor(py);
        int maxBY = (int) Math.floor(py + FULL_HEIGHT - 0.001f);
        int minBZ = (int) Math.floor(pz - HALF_WIDTH);
        int maxBZ = (int) Math.floor(pz + HALF_WIDTH);

        for (int bx = minBX; bx <= maxBX; bx++)
            for (int by = minBY; by <= maxBY; by++)
                for (int bz = minBZ; bz <= maxBZ; bz++)
                    if (world.getBlock(bx, by, bz) != Game.AIR)
                        return true;
        return false;
    }

    /**
     * Находит верх самого высокого твёрдого блока, в который
     * входит хитбокс при падении с oldY до newY.
     * Возвращает newY если пола нет.
     */
    private float findFloor(float px, float pz, float oldY, float newY) {
        int minBX = (int) Math.floor(px - HALF_WIDTH);
        int maxBX = (int) Math.floor(px + HALF_WIDTH);
        int minBZ = (int) Math.floor(pz - HALF_WIDTH);
        int maxBZ = (int) Math.floor(pz + HALF_WIDTH);

        // диапазон Y-блоков, которые могут оказаться под ногами
        int checkTop = (int) Math.floor(oldY) - 1;
        int checkBot = (int) Math.floor(newY);

        float best = newY;

        for (int bx = minBX; bx <= maxBX; bx++)
            for (int bz = minBZ; bz <= maxBZ; bz++)
                for (int by = checkTop; by >= Math.max(0, checkBot); by--)
                    if (world.getBlock(bx, by, bz) != Game.AIR) {
                        float top = by + 1;
                        if (top > best && top <= oldY + 0.001f)
                            best = top;
                    }

        return best;
    }

    /**
     * Прижимает игрока к потолку при движении вверх.
     */
    private float snapCeiling(float px, float py, float pz) {
        int headBlock = (int) Math.floor(py + FULL_HEIGHT);
        return headBlock - FULL_HEIGHT;
    }

    // --- скольжение вдоль стены ---

    private float slideX(float oldX, float dx, float py, float pz) {
        // пробуем вплотную к блоку
        if (dx > 0) {
            int wallBX = (int) Math.floor(oldX + HALF_WIDTH + dx);
            return wallBX - HALF_WIDTH - 0.001f;
        } else {
            int wallBX = (int) Math.floor(oldX - HALF_WIDTH + dx);
            return wallBX + 1 + HALF_WIDTH + 0.001f;
        }
    }

    private float slideZ(float px, float oldZ, float dz, float py) {
        if (dz > 0) {
            int wallBZ = (int) Math.floor(oldZ + HALF_WIDTH + dz);
            return wallBZ - HALF_WIDTH - 0.001f;
        } else {
            int wallBZ = (int) Math.floor(oldZ - HALF_WIDTH + dz);
            return wallBZ + 1 + HALF_WIDTH + 0.001f;
        }
    }

    // ──────────────────────────────────────────────
    //  RESPAWN
    // ──────────────────────────────────────────────

    private void respawn() {
        x = 32.5f;
        z = 32.5f;
        for (int ty = World.WORLD_HEIGHT - 1; ty >= 0; ty--) {
            if (world.getBlock(32, ty, 32) != Game.AIR) {
                y = ty + 1;
                vy = 0;
                return;
            }
        }
        y = 10;
        vy = 0;
    }

    // ──────────────────────────────────────────────
    //  BLOCK INTERACTION
    // ──────────────────────────────────────────────

    private void updateBreaking(boolean breaking) {
        int[] target = getLookingAt();

        if (!breaking || target == null) {
            breakProgress = 0;
            breakX = breakY = breakZ = Integer.MIN_VALUE;
            return;
        }

        int bx = target[0], by = target[1], bz = target[2];

        if (bx != breakX || by != breakY || bz != breakZ) {
            breakX = bx; breakY = by; breakZ = bz;
            breakProgress = 0;
        }

        int type = world.getBlock(bx, by, bz);
        float breakTime = getBreakTime(type);

        breakProgress++;

        if (breakProgress >= breakTime) {
            BlockParticle.spawnBreakParticles(bx, by, bz, type, world);
            SoundManager.playBreak(type);
            world.setBlock(bx, by, bz, Game.AIR);
            breakProgress = 0;
        }
    }

    private float getBreakTime(int type) {
        switch (type) {
            case Game.STONE:  return 90;
            case Game.DIRT:   return 30;
            case Game.GRASS:  return 35;
            case Game.LOG:    return 100;
            case Game.LEAVES: return 15;
            default:          return 30;
        }
    }

    // ──────────────────────────────────────────────
    //  RAYCAST
    // ──────────────────────────────────────────────

    private void computeDirection(float[] out) {
        float pr = (float) Math.toRadians(pitch);
        float yr = (float) Math.toRadians(yaw);
        out[0] = (float) (Math.sin(yr) * Math.cos(pr));
        out[1] = (float) (-Math.sin(pr));
        out[2] = (float) (-Math.cos(yr) * Math.cos(pr));
    }

    public int[] getLookingAt() {
        float eyeX = x, eyeY = y + EYE_HEIGHT, eyeZ = z;
        float[] dir = new float[3];
        computeDirection(dir);

        int prevBX = Integer.MIN_VALUE, prevBY = Integer.MIN_VALUE, prevBZ = Integer.MIN_VALUE;

        for (float t = 0; t < REACH_DISTANCE; t += RAY_STEP) {
            int bx = (int) Math.floor(eyeX + dir[0] * t);
            int by = (int) Math.floor(eyeY + dir[1] * t);
            int bz = (int) Math.floor(eyeZ + dir[2] * t);

            if (bx == prevBX && by == prevBY && bz == prevBZ) continue;
            prevBX = bx; prevBY = by; prevBZ = bz;

            if (world.getBlock(bx, by, bz) != Game.AIR) {
                return new int[]{bx, by, bz};
            }
        }
        return null;
    }

    public void placeBlock() {
        float eyeX = x, eyeY = y + EYE_HEIGHT, eyeZ = z;
        float[] dir = new float[3];
        computeDirection(dir);

        int lastX = Integer.MIN_VALUE, lastY = Integer.MIN_VALUE, lastZ = Integer.MIN_VALUE;
        int prevBX = Integer.MIN_VALUE, prevBY = Integer.MIN_VALUE, prevBZ = Integer.MIN_VALUE;

        for (float t = 0; t < REACH_DISTANCE; t += RAY_STEP) {
            int bx = (int) Math.floor(eyeX + dir[0] * t);
            int by = (int) Math.floor(eyeY + dir[1] * t);
            int bz = (int) Math.floor(eyeZ + dir[2] * t);

            if (bx == prevBX && by == prevBY && bz == prevBZ) continue;
            prevBX = bx; prevBY = by; prevBZ = bz;

            if (world.getBlock(bx, by, bz) != Game.AIR) {
                if (lastX != Integer.MIN_VALUE && !wouldCollide(lastX, lastY, lastZ)) {
                    world.setBlock(lastX, lastY, lastZ, selectedBlock);
                    SoundManager.playPlace(selectedBlock);
                }
                return;
            }
            lastX = bx; lastY = by; lastZ = bz;
        }
    }

    /**
     * Проверяет пересечение AABB игрока с блоком [bx..bx+1, by..by+1, bz..bz+1]
     */
    private boolean wouldCollide(int bx, int by, int bz) {
        return (x - HALF_WIDTH) < (bx + 1) && (x + HALF_WIDTH) > bx &&
               y              < (by + 1) && (y + FULL_HEIGHT)  > by &&
               (z - HALF_WIDTH) < (bz + 1) && (z + HALF_WIDTH) > bz;
    }

    // ──────────────────────────────────────────────
    //  CAMERA
    // ──────────────────────────────────────────────

    public void applyCamera() {
        glRotatef(pitch, 1, 0, 0);
        glRotatef(yaw, 0, 1, 0);
        glTranslatef(-x, -(y + EYE_HEIGHT), -z);
    }

    // ──────────────────────────────────────────────
    //  RENDERING (highlight, debug)
    // ──────────────────────────────────────────────

    public void renderBlockHighlight() {
        int[] target = getLookingAt();
        if (target != null) {
            int bx = target[0], by = target[1], bz = target[2];

            glDisable(GL_TEXTURE_2D);
            glDisable(GL_CULL_FACE);

            // wireframe
            glLineWidth(2);
            glColor4f(0, 0, 0, 0.4f);
            drawWireBlock(bx, by, bz);

            // break overlay
            if (breakProgress > 0 && breakX == bx && breakY == by && breakZ == bz) {
                int type = world.getBlock(bx, by, bz);
                float progress = breakProgress / getBreakTime(type);

                glEnable(GL_BLEND);
                glDepthMask(false);
                glColor4f(0, 0, 0, 0.15f + progress * 0.5f);
                drawSolidBlock(bx, by, bz, 0.002f);
                glDepthMask(true);
                glDisable(GL_BLEND);
            }

            glEnable(GL_CULL_FACE);
            glEnable(GL_TEXTURE_2D);
        }

        if (showDebug) renderDebugHitbox();
    }

    /**
     * Wireframe вокруг блока. Блок занимает [bx, by, bz] — [bx+1, by+1, bz+1].
     * Рамка чуть больше на grow, чтобы не z-fight.
     */
    private void drawWireBlock(int bx, int by, int bz) {
        float g = 0.005f;
        float x0 = bx - g, x1 = bx + 1 + g;
        float y0 = by - g, y1 = by + 1 + g;
        float z0 = bz - g, z1 = bz + 1 + g;

        glBegin(GL_LINE_LOOP);
        glVertex3f(x0, y0, z1); glVertex3f(x1, y0, z1);
        glVertex3f(x1, y1, z1); glVertex3f(x0, y1, z1);
        glEnd();

        glBegin(GL_LINE_LOOP);
        glVertex3f(x0, y0, z0); glVertex3f(x1, y0, z0);
        glVertex3f(x1, y1, z0); glVertex3f(x0, y1, z0);
        glEnd();

        glBegin(GL_LINES);
        glVertex3f(x0, y0, z0); glVertex3f(x0, y0, z1);
        glVertex3f(x1, y0, z0); glVertex3f(x1, y0, z1);
        glVertex3f(x1, y1, z0); glVertex3f(x1, y1, z1);
        glVertex3f(x0, y1, z0); glVertex3f(x0, y1, z1);
        glEnd();
    }

    private void drawSolidBlock(int bx, int by, int bz, float grow) {
        float x0 = bx - grow, x1 = bx + 1 + grow;
        float y0 = by - grow, y1 = by + 1 + grow;
        float z0 = bz - grow, z1 = bz + 1 + grow;

        glBegin(GL_QUADS);
        // +Z
        glVertex3f(x0, y0, z1); glVertex3f(x1, y0, z1);
        glVertex3f(x1, y1, z1); glVertex3f(x0, y1, z1);
        // -Z
        glVertex3f(x1, y0, z0); glVertex3f(x0, y0, z0);
        glVertex3f(x0, y1, z0); glVertex3f(x1, y1, z0);
        // +Y
        glVertex3f(x0, y1, z1); glVertex3f(x1, y1, z1);
        glVertex3f(x1, y1, z0); glVertex3f(x0, y1, z0);
        // -Y
        glVertex3f(x0, y0, z0); glVertex3f(x1, y0, z0);
        glVertex3f(x1, y0, z1); glVertex3f(x0, y0, z1);
        // +X
        glVertex3f(x1, y0, z1); glVertex3f(x1, y0, z0);
        glVertex3f(x1, y1, z0); glVertex3f(x1, y1, z1);
        // -X
        glVertex3f(x0, y0, z0); glVertex3f(x0, y0, z1);
        glVertex3f(x0, y1, z1); glVertex3f(x0, y1, z0);
        glEnd();
    }

    private void renderDebugHitbox() {
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_CULL_FACE);
        glDisable(GL_DEPTH_TEST);

        float minX = x - HALF_WIDTH, maxX = x + HALF_WIDTH;
        float minY = y,              maxY = y + FULL_HEIGHT;
        float minZ = z - HALF_WIDTH, maxZ = z + HALF_WIDTH;

        glLineWidth(2);
        glColor4f(0, 1, 0, 1);

        glBegin(GL_LINE_LOOP);
        glVertex3f(minX, minY, minZ); glVertex3f(maxX, minY, minZ);
        glVertex3f(maxX, minY, maxZ); glVertex3f(minX, minY, maxZ);
        glEnd();

        glBegin(GL_LINE_LOOP);
        glVertex3f(minX, maxY, minZ); glVertex3f(maxX, maxY, minZ);
        glVertex3f(maxX, maxY, maxZ); glVertex3f(minX, maxY, maxZ);
        glEnd();

        glBegin(GL_LINES);
        glVertex3f(minX, minY, minZ); glVertex3f(minX, maxY, minZ);
        glVertex3f(maxX, minY, minZ); glVertex3f(maxX, maxY, minZ);
        glVertex3f(maxX, minY, maxZ); glVertex3f(maxX, maxY, maxZ);
        glVertex3f(minX, minY, maxZ); glVertex3f(minX, maxY, maxZ);
        glEnd();

        // feet center (red), eye position (yellow)
        glPointSize(10);
        glColor4f(1, 0, 0, 1);
        glBegin(GL_POINTS); glVertex3f(x, y + 0.02f, z); glEnd();

        glColor4f(1, 1, 0, 1);
        glBegin(GL_POINTS); glVertex3f(x, y + EYE_HEIGHT, z); glEnd();

        // view direction
        float[] dir = new float[3];
        computeDirection(dir);
        float ey = y + EYE_HEIGHT;
        glColor4f(0, 1, 1, 1);
        glLineWidth(2);
        glBegin(GL_LINES);
        glVertex3f(x, ey, z);
        glVertex3f(x + dir[0] * 3, ey + dir[1] * 3, z + dir[2] * 3);
        glEnd();

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glEnable(GL_TEXTURE_2D);
    }
}