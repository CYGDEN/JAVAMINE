import static org.lwjgl.opengl.GL11.*;
import java.util.*;

public class World {
    public static final int CHUNK_SIZE = 16;
    public static final int WORLD_HEIGHT = 128;
    public static final int RENDER_DISTANCE = 4;

    private Map<Long, Chunk> chunks = new HashMap<>();

    public class Chunk {
        public int chunkX, chunkZ;
        public int[][][] blocks = new int[CHUNK_SIZE][WORLD_HEIGHT][CHUNK_SIZE];
        public boolean needsRebuild = true;
        public int displayList = -1;

        public Chunk(int cx, int cz) { chunkX = cx; chunkZ = cz; }
    }

    // ─── ключ чанка: long вместо String (быстрее) ───

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    // ─── доступ к блокам ───

    public int getBlock(int x, int y, int z) {
        if (y < 0 || y >= WORLD_HEIGHT) return Game.AIR;
        int cx = Math.floorDiv(x, CHUNK_SIZE);
        int cz = Math.floorDiv(z, CHUNK_SIZE);
        Chunk c = chunks.get(chunkKey(cx, cz));
        if (c == null) return Game.AIR;
        int lx = Math.floorMod(x, CHUNK_SIZE);
        int lz = Math.floorMod(z, CHUNK_SIZE);
        return c.blocks[lx][y][lz];
    }

    public void setBlock(int x, int y, int z, int type) {
        if (y < 0 || y >= WORLD_HEIGHT) return;
        int cx = Math.floorDiv(x, CHUNK_SIZE);
        int cz = Math.floorDiv(z, CHUNK_SIZE);
        Chunk c = chunks.get(chunkKey(cx, cz));
        if (c == null) return;
        int lx = Math.floorMod(x, CHUNK_SIZE);
        int lz = Math.floorMod(z, CHUNK_SIZE);
        c.blocks[lx][y][lz] = type;
        c.needsRebuild = true;

        // соседние чанки тоже нужно перестроить при изменении на границе
        if (lx == 0)              markRebuild(cx - 1, cz);
        if (lx == CHUNK_SIZE - 1) markRebuild(cx + 1, cz);
        if (lz == 0)              markRebuild(cx, cz - 1);
        if (lz == CHUNK_SIZE - 1) markRebuild(cx, cz + 1);
    }

    private void markRebuild(int cx, int cz) {
        Chunk c = chunks.get(chunkKey(cx, cz));
        if (c != null) c.needsRebuild = true;
    }

    // ─── генерация ───

    public void generateInitialChunks(int px, int pz) {
        int pcx = Math.floorDiv(px, CHUNK_SIZE);
        int pcz = Math.floorDiv(pz, CHUNK_SIZE);

        for (int cx = pcx - RENDER_DISTANCE; cx <= pcx + RENDER_DISTANCE; cx++) {
            for (int cz = pcz - RENDER_DISTANCE; cz <= pcz + RENDER_DISTANCE; cz++) {
                long key = chunkKey(cx, cz);
                if (!chunks.containsKey(key)) {
                    Chunk c = new Chunk(cx, cz);
                    generateChunk(c);
                    chunks.put(key, c);
                }
            }
        }
        System.out.println("Generated " + chunks.size() + " chunks");
    }

    public void updateChunks(int px, int pz) {
        int pcx = Math.floorDiv(px, CHUNK_SIZE);
        int pcz = Math.floorDiv(pz, CHUNK_SIZE);

        // генерируем новые чанки
        for (int cx = pcx - RENDER_DISTANCE; cx <= pcx + RENDER_DISTANCE; cx++) {
            for (int cz = pcz - RENDER_DISTANCE; cz <= pcz + RENDER_DISTANCE; cz++) {
                long key = chunkKey(cx, cz);
                if (!chunks.containsKey(key)) {
                    Chunk c = new Chunk(cx, cz);
                    generateChunk(c);
                    chunks.put(key, c);
                }
            }
        }

        // удаляем далёкие чанки
        int unloadDist = RENDER_DISTANCE + 2;
        chunks.entrySet().removeIf(entry -> {
            Chunk c = entry.getValue();
            if (Math.abs(c.chunkX - pcx) > unloadDist || Math.abs(c.chunkZ - pcz) > unloadDist) {
                if (c.displayList != -1) glDeleteLists(c.displayList, 1);
                return true;
            }
            return false;
        });
    }

    private void generateChunk(Chunk c) {
        Random rand = new Random(c.chunkX * 341873128712L + c.chunkZ * 132897987541L);

        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                int wx = c.chunkX * CHUNK_SIZE + x;
                int wz = c.chunkZ * CHUNK_SIZE + z;

                // простой шум для высоты
                int h = 64 + (int)(
                    Math.sin(wx * 0.02) * 8 +
                    Math.cos(wz * 0.02) * 8 +
                    Math.sin((wx + wz) * 0.05) * 4
                );
                h = Math.max(10, Math.min(h, 100));

                for (int y = 0; y < h; y++) {
                    if (y == h - 1)      c.blocks[x][y][z] = Game.GRASS;
                    else if (y >= h - 4) c.blocks[x][y][z] = Game.DIRT;
                    else                 c.blocks[x][y][z] = Game.STONE;
                }

                // деревья
                if (rand.nextFloat() < 0.005f && h < 90) {
                    int trunkHeight = 4 + rand.nextInt(2);

                    // ствол
                    for (int ty = 0; ty < trunkHeight; ty++) {
                        int by = h + ty;
                        if (by < WORLD_HEIGHT) {
                            c.blocks[x][by][z] = Game.LOG;
                        }
                    }

                    // крона
                    for (int lx = -2; lx <= 2; lx++) {
                        for (int lz = -2; lz <= 2; lz++) {
                            for (int ly = trunkHeight - 2; ly <= trunkHeight + 1; ly++) {
                                // убираем углы для натуральности
                                if (Math.abs(lx) == 2 && Math.abs(lz) == 2) continue;

                                int bx = x + lx;
                                int bz = z + lz;
                                int by = h + ly;

                                if (bx >= 0 && bx < CHUNK_SIZE &&
                                    bz >= 0 && bz < CHUNK_SIZE &&
                                    by >= 0 && by < WORLD_HEIGHT) {
                                    if (c.blocks[bx][by][bz] == Game.AIR) {
                                        c.blocks[bx][by][bz] = Game.LEAVES;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ─── рендер ───

    public void render() {
        for (Chunk c : chunks.values()) {
            if (c.needsRebuild) buildChunk(c);
            if (c.displayList != -1) glCallList(c.displayList);
        }
    }

    private void buildChunk(Chunk c) {
        if (c.displayList != -1) glDeleteLists(c.displayList, 1);
        c.displayList = glGenLists(1);
        glNewList(c.displayList, GL_COMPILE);

        // рисуем по типу блока (чтобы реже переключать текстуру)
        for (int type = 1; type <= 5; type++) {
            bindBlockTexture(type);

            glBegin(GL_QUADS);
            for (int x = 0; x < CHUNK_SIZE; x++) {
                for (int y = 0; y < WORLD_HEIGHT; y++) {
                    for (int z = 0; z < CHUNK_SIZE; z++) {
                        if (c.blocks[x][y][z] != type) continue;

                        int wx = c.chunkX * CHUNK_SIZE + x;
                        int wz = c.chunkZ * CHUNK_SIZE + z;

                        boolean fZpos = getBlock(wx, y, wz + 1) == Game.AIR;
                        boolean fZneg = getBlock(wx, y, wz - 1) == Game.AIR;
                        boolean fYpos = getBlock(wx, y + 1, wz) == Game.AIR;
                        boolean fYneg = y == 0 || getBlock(wx, y - 1, wz) == Game.AIR;
                        boolean fXpos = getBlock(wx + 1, y, wz) == Game.AIR;
                        boolean fXneg = getBlock(wx - 1, y, wz) == Game.AIR;

                        if (fZpos || fZneg || fYpos || fYneg || fXpos || fXneg) {
                            drawBlock(wx, y, wz, type,
                                fZpos, fZneg, fYpos, fYneg, fXpos, fXneg);
                        }
                    }
                }
            }
            glEnd();
        }

        // верх травы — отдельная текстура
        if (Game.texturesLoaded) {
            glBindTexture(GL_TEXTURE_2D, Game.texGrassTop);
            glColor3f(1, 1, 1);
            glBegin(GL_QUADS);
            for (int x = 0; x < CHUNK_SIZE; x++) {
                for (int y = 0; y < WORLD_HEIGHT; y++) {
                    for (int z = 0; z < CHUNK_SIZE; z++) {
                        if (c.blocks[x][y][z] != Game.GRASS) continue;
                        int wx = c.chunkX * CHUNK_SIZE + x;
                        int wz = c.chunkZ * CHUNK_SIZE + z;
                        if (getBlock(wx, y + 1, wz) == Game.AIR) {
                            // блок [wx .. wx+1, y+1, wz .. wz+1]
                            float x0 = wx, x1 = wx + 1;
                            float z0 = wz, z1 = wz + 1;
                            float top = y + 1;
                            glTexCoord2f(0, 0); glVertex3f(x0, top, z1);
                            glTexCoord2f(1, 0); glVertex3f(x1, top, z1);
                            glTexCoord2f(1, 1); glVertex3f(x1, top, z0);
                            glTexCoord2f(0, 1); glVertex3f(x0, top, z0);
                        }
                    }
                }
            }
            glEnd();
        }

        glEndList();
        c.needsRebuild = false;
    }

    private void bindBlockTexture(int type) {
        if (Game.texturesLoaded) {
            glEnable(GL_TEXTURE_2D);
            glColor3f(1, 1, 1);
            switch (type) {
                case Game.STONE:  glBindTexture(GL_TEXTURE_2D, Game.texStone);  break;
                case Game.DIRT:
                case Game.GRASS:  glBindTexture(GL_TEXTURE_2D, Game.texDirt);   break;
                case Game.LOG:    glBindTexture(GL_TEXTURE_2D, Game.texLog);    break;
                case Game.LEAVES: glBindTexture(GL_TEXTURE_2D, Game.texLeaves); break;
            }
        } else {
            glDisable(GL_TEXTURE_2D);
        }
    }

    /**
     * ИСПРАВЛЕНО: блок рисуется от [x, y, z] до [x+1, y+1, z+1]
     * Было: от центра ± 0.5 (т.е. [x-0.5 .. x+0.5]) — НЕПРАВИЛЬНО
     */
    private void drawBlock(int x, int y, int z, int type,
                           boolean fZpos, boolean fZneg,
                           boolean fYpos, boolean fYneg,
                           boolean fXpos, boolean fXneg) {
        float x0 = x, x1 = x + 1;
        float y0 = y, y1 = y + 1;
        float z0 = z, z1 = z + 1;

        if (!Game.texturesLoaded) setColor(type);

        // Front Z+ face
        if (fZpos) {
            if (!Game.texturesLoaded) shade(0.9f, type);
            glTexCoord2f(0, 1); glVertex3f(x0, y0, z1);
            glTexCoord2f(1, 1); glVertex3f(x1, y0, z1);
            glTexCoord2f(1, 0); glVertex3f(x1, y1, z1);
            glTexCoord2f(0, 0); glVertex3f(x0, y1, z1);
        }
        // Back Z- face
        if (fZneg) {
            if (!Game.texturesLoaded) shade(0.7f, type);
            glTexCoord2f(0, 1); glVertex3f(x1, y0, z0);
            glTexCoord2f(1, 1); glVertex3f(x0, y0, z0);
            glTexCoord2f(1, 0); glVertex3f(x0, y1, z0);
            glTexCoord2f(0, 0); glVertex3f(x1, y1, z0);
        }
        // Top Y+ face (skip for grass — drawn separately with grass_top texture)
        if (fYpos && type != Game.GRASS) {
            if (!Game.texturesLoaded) shade(1.0f, type);
            glTexCoord2f(0, 0); glVertex3f(x0, y1, z1);
            glTexCoord2f(1, 0); glVertex3f(x1, y1, z1);
            glTexCoord2f(1, 1); glVertex3f(x1, y1, z0);
            glTexCoord2f(0, 1); glVertex3f(x0, y1, z0);
        }
        // Bottom Y- face
        if (fYneg) {
            if (!Game.texturesLoaded) shade(0.5f, type);
            glTexCoord2f(0, 0); glVertex3f(x0, y0, z0);
            glTexCoord2f(1, 0); glVertex3f(x1, y0, z0);
            glTexCoord2f(1, 1); glVertex3f(x1, y0, z1);
            glTexCoord2f(0, 1); glVertex3f(x0, y0, z1);
        }
        // Right X+ face
        if (fXpos) {
            if (!Game.texturesLoaded) shade(0.8f, type);
            glTexCoord2f(0, 1); glVertex3f(x1, y0, z1);
            glTexCoord2f(1, 1); glVertex3f(x1, y0, z0);
            glTexCoord2f(1, 0); glVertex3f(x1, y1, z0);
            glTexCoord2f(0, 0); glVertex3f(x1, y1, z1);
        }
        // Left X- face
        if (fXneg) {
            if (!Game.texturesLoaded) shade(0.8f, type);
            glTexCoord2f(0, 1); glVertex3f(x0, y0, z0);
            glTexCoord2f(1, 1); glVertex3f(x0, y0, z1);
            glTexCoord2f(1, 0); glVertex3f(x0, y1, z1);
            glTexCoord2f(0, 0); glVertex3f(x0, y1, z0);
        }
    }

    /**
     * Применяет оттенок для directional shading без текстур
     */
    private void shade(float brightness, int type) {
        float[] base = getBaseColor(type);
        glColor3f(base[0] * brightness, base[1] * brightness, base[2] * brightness);
    }

    private float[] getBaseColor(int type) {
        switch (type) {
            case Game.STONE:  return new float[]{0.6f, 0.6f, 0.6f};
            case Game.DIRT:   return new float[]{0.55f, 0.35f, 0.15f};
            case Game.GRASS:  return new float[]{0.3f, 0.7f, 0.3f};
            case Game.LOG:    return new float[]{0.5f, 0.35f, 0.2f};
            case Game.LEAVES: return new float[]{0.2f, 0.6f, 0.2f};
            default:          return new float[]{1, 1, 1};
        }
    }

    private void setColor(int type) {
        float[] c = getBaseColor(type);
        glColor3f(c[0], c[1], c[2]);
    }

    public void cleanup() {
        for (Chunk c : chunks.values()) {
            if (c.displayList != -1) glDeleteLists(c.displayList, 1);
        }
        chunks.clear();
    }
}