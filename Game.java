import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;

public class Game {
    private long window;
    private int windowWidth = 1280, windowHeight = 720;

    private World world;
    private Player player;

    // Mouse
    private boolean mouseLocked = false;
    private double lastMouseX = 0, lastMouseY = 0;
    private boolean firstMouse = true;
    private boolean mouseLeft = false;

    // Textures
    public static int texStone, texDirt, texGrassTop, texLog, texLeaves;
    public static boolean texturesLoaded = false;

    // Block types
    public static final int AIR = 0;
    public static final int STONE = 1;
    public static final int DIRT = 2;
    public static final int GRASS = 3;
    public static final int LOG = 4;
    public static final int LEAVES = 5;

    // FPS
    private int fps = 0;
    private int frameCount = 0;
    private long lastFpsTime = 0;

    public void run() {
        init();
        loop();
        cleanup();
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("GLFW init failed");

        window = glfwCreateWindow(windowWidth, windowHeight, "Minecraft Alpha", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Window creation failed");

        GLFWVidMode vid = glfwGetVideoMode(glfwGetPrimaryMonitor());
        glfwSetWindowPos(window, (vid.width() - windowWidth) / 2, (vid.height() - windowHeight) / 2);

        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        glfwSwapInterval(1);
        glfwShowWindow(window);

        setupInput();
        initGL();
        loadTextures();

        // мир
        world = new World();

        // спавн: центр блока (32, ?, 32)
        int spawnBlockX = 32;
        int spawnBlockZ = 32;

        world.generateInitialChunks(spawnBlockX, spawnBlockZ);

        // ищем высоту спавна
        int spawnY = 80;
        for (int y = World.WORLD_HEIGHT - 1; y >= 0; y--) {
            if (world.getBlock(spawnBlockX, y, spawnBlockZ) != AIR) {
                spawnY = y + 1;
                break;
            }
        }

        // центр блока = blockX + 0.5
        player = new Player(spawnBlockX + 0.5f, spawnY, spawnBlockZ + 0.5f, world);

        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        mouseLocked = true;

        SoundManager.init();

        System.out.println("Game started! Spawn at block (" +
            spawnBlockX + ", " + spawnY + ", " + spawnBlockZ + ")");
        System.out.println("Player pos: " + player.x + ", " + player.y + ", " + player.z);
        System.out.println("WASD=Move, Space=Jump, Mouse=Look, LMB=Break, RMB=Place, 1-5=Select, F3=Debug, ESC=Exit");

        lastFpsTime = System.currentTimeMillis();
    }

    private void setupInput() {
        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            if (!mouseLocked) return;

            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                mouseLeft = (action == GLFW_PRESS);
            }
            if (button == GLFW_MOUSE_BUTTON_RIGHT && action == GLFW_PRESS) {
                player.placeBlock();
            }
        });

        glfwSetCursorPosCallback(window, (w, xpos, ypos) -> {
            if (!mouseLocked) return;

            if (firstMouse) {
                lastMouseX = xpos;
                lastMouseY = ypos;
                firstMouse = false;
            }

            float dx = (float) (xpos - lastMouseX);
            float dy = (float) (ypos - lastMouseY);
            lastMouseX = xpos;
            lastMouseY = ypos;

            player.rotate(dx * 0.15f, dy * 0.15f);
        });

        glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                if (mouseLocked) {
                    glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
                    mouseLocked = false;
                } else {
                    glfwSetWindowShouldClose(window, true);
                }
            }

            if (key == GLFW_KEY_TAB && action == GLFW_PRESS) {
                mouseLocked = !mouseLocked;
                glfwSetInputMode(window, GLFW_CURSOR,
                    mouseLocked ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
                firstMouse = true;
            }

            if (key == GLFW_KEY_F3 && action == GLFW_PRESS) {
                Player.showDebug = !Player.showDebug;
            }

            if (action == GLFW_PRESS && key >= GLFW_KEY_1 && key <= GLFW_KEY_5) {
                player.selectedBlock = key - GLFW_KEY_1 + 1;
            }

            boolean pressed = (action != GLFW_RELEASE);
            if (key == GLFW_KEY_W) player.keyW = pressed;
            if (key == GLFW_KEY_S) player.keyS = pressed;
            if (key == GLFW_KEY_A) player.keyA = pressed;
            if (key == GLFW_KEY_D) player.keyD = pressed;
            if (key == GLFW_KEY_SPACE) player.keySpace = pressed;
            if (key == GLFW_KEY_LEFT_SHIFT) player.keyShift = pressed;
        });

        glfwSetFramebufferSizeCallback(window, (w, width, height) -> {
            windowWidth = width;
            windowHeight = height;
            glViewport(0, 0, width, height);
        });
    }

    private void initGL() {
        glClearColor(0.5f, 0.75f, 1.0f, 1.0f);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glEnable(GL_FOG);
        glFogi(GL_FOG_MODE, GL_LINEAR);
        glFogf(GL_FOG_START, 40f);
        glFogf(GL_FOG_END, 70f);
        glFogfv(GL_FOG_COLOR, new float[]{0.5f, 0.75f, 1.0f, 1.0f});
    }

    private void loadTextures() {
        try {
            texStone = loadTexture("textures/stone.png");
            texDirt = loadTexture("textures/dirt.png");
            texGrassTop = loadTexture("textures/grass_top.png");
            texLog = loadTexture("textures/log.png");
            texLeaves = loadTexture("textures/leaves.png");
            texturesLoaded = true;
            System.out.println("Textures loaded!");
        } catch (Exception e) {
            System.out.println("No textures found, using solid colors");
            texturesLoaded = false;
        }
    }

    private int loadTexture(String path) throws Exception {
        BufferedImage img = ImageIO.read(new File(path));
        int w = img.getWidth(), h = img.getHeight();
        int[] pixels = img.getRGB(0, 0, w, h, null, 0, w);

        ByteBuffer buf = ByteBuffer.allocateDirect(w * h * 4);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = pixels[y * w + x];
                buf.put((byte) ((p >> 16) & 0xFF));
                buf.put((byte) ((p >> 8) & 0xFF));
                buf.put((byte) (p & 0xFF));
                buf.put((byte) ((p >> 24) & 0xFF));
            }
        }
        buf.flip();

        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        return id;
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            // FPS
            frameCount++;
            long now = System.currentTimeMillis();
            if (now - lastFpsTime >= 1000) {
                fps = frameCount;
                frameCount = 0;
                lastFpsTime = now;
                glfwSetWindowTitle(window, "Minecraft Alpha - FPS: " + fps +
                    " | Pos: " + String.format("%.2f, %.2f, %.2f", player.x, player.y, player.z) +
                    " | Block: " + (int) Math.floor(player.x) + ", " +
                    (int) Math.floor(player.y) + ", " + (int) Math.floor(player.z));
            }

            // Update
            player.update(mouseLeft);
            world.updateChunks((int) player.x, (int) player.z);
            BlockParticle.updateAll();

            // Render
            render();

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void render() {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // Projection
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        float aspect = (float) windowWidth / windowHeight;
        float fov = 70f, near = 0.05f, far = 200f;
        float top = (float) (near * Math.tan(Math.toRadians(fov / 2)));
        glFrustum(-top * aspect, top * aspect, -top, top, near, far);

        // Camera
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        player.applyCamera();

        // World
        world.render();

        // Highlights & debug
        player.renderBlockHighlight();

        // Particles
        BlockParticle.renderAll(player.x, player.z);

        // HUD
        renderHUD();
    }

    private void renderHUD() {
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, windowWidth, windowHeight, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glDisable(GL_TEXTURE_2D);
        glDisable(GL_DEPTH_TEST);

        // Crosshair
        int cx = windowWidth / 2, cy = windowHeight / 2;
        glLineWidth(2);
        glColor3f(1, 1, 1);
        glBegin(GL_LINES);
        glVertex2f(cx - 10, cy); glVertex2f(cx + 10, cy);
        glVertex2f(cx, cy - 10); glVertex2f(cx, cy + 10);
        glEnd();

        // Selected block indicator
        float size = 40;
        setBlockColor(player.selectedBlock);
        glBegin(GL_QUADS);
        glVertex2f(10, 10);
        glVertex2f(10 + size, 10);
        glVertex2f(10 + size, 10 + size);
        glVertex2f(10, 10 + size);
        glEnd();

        glColor3f(1, 1, 1);
        glBegin(GL_LINE_LOOP);
        glVertex2f(10, 10);
        glVertex2f(10 + size, 10);
        glVertex2f(10 + size, 10 + size);
        glVertex2f(10, 10 + size);
        glEnd();

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_TEXTURE_2D);
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }

    private void setBlockColor(int type) {
        switch (type) {
            case STONE:  glColor3f(0.6f, 0.6f, 0.6f); break;
            case DIRT:   glColor3f(0.55f, 0.35f, 0.15f); break;
            case GRASS:  glColor3f(0.3f, 0.7f, 0.3f); break;
            case LOG:    glColor3f(0.5f, 0.35f, 0.2f); break;
            case LEAVES: glColor3f(0.2f, 0.6f, 0.2f); break;
        }
    }

    private void cleanup() {
        SoundManager.cleanup();
        world.cleanup();
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    public static void main(String[] args) {
        new Game().run();
    }
}