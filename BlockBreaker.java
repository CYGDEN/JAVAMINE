import static org.lwjgl.opengl.GL11.*;
import java.util.Random;

public class BlockBreaker {
    private World world;
    private Player player;
    
    // State
    private boolean breaking = false;
    private int targetX = Integer.MIN_VALUE;
    private int targetY = Integer.MIN_VALUE;
    private int targetZ = Integer.MIN_VALUE;
    private float progress = 0;
    private float breakTime = 30;
    
    // Crack pattern (random but consistent per block)
    private float[] crackOffsets = new float[20];
    private Random rand = new Random();
    
    public BlockBreaker(World world, Player player) {
        this.world = world;
        this.player = player;
        generateCrackPattern();
    }
    
    private void generateCrackPattern() {
        for (int i = 0; i < crackOffsets.length; i++) {
            crackOffsets[i] = rand.nextFloat();
        }
    }
    
    public void startBreaking() {
        breaking = true;
    }
    
    public void stopBreaking() {
        breaking = false;
        progress = 0;
    }
    
    public void update() {
        if (!breaking) {
            progress = 0;
            return;
        }
        
        int[] target = player.getLookingAt();
        
        if (target == null) {
            progress = 0;
            return;
        }
        
        int bx = target[0];
        int by = target[1];
        int bz = target[2];
        
        // Check if target changed
        if (bx != targetX || by != targetY || bz != targetZ) {
            targetX = bx;
            targetY = by;
            targetZ = bz;
            progress = 0;
            
            // New crack pattern for new block
            rand.setSeed(bx * 31L + by * 37L + bz * 41L);
            generateCrackPattern();
            
            int blockType = world.getBlock(bx, by, bz);
            breakTime = getBreakTime(blockType);
        }
        
        // Increase progress
        progress += 1;
        
        // Break block
        if (progress >= breakTime) {
            int blockType = world.getBlock(targetX, targetY, targetZ);
            
            if (blockType != Game.AIR) {
                BlockParticle.spawnBreakParticles(targetX, targetY, targetZ, blockType, world);
                SoundManager.playBreak(blockType);
                world.setBlock(targetX, targetY, targetZ, Game.AIR);
            }
            
            progress = 0;
            targetX = Integer.MIN_VALUE;
        }
    }
    
    private float getBreakTime(int type) {
        switch (type) {
            case Game.STONE: return 90;
            case Game.DIRT: return 30;
            case Game.GRASS: return 36;
            case Game.LOG: return 120;
            case Game.LEAVES: return 15;
            default: return 30;
        }
    }
    
    public void render() {
        if (!breaking || progress <= 0 || targetX == Integer.MIN_VALUE) return;
        
        int[] target = player.getLookingAt();
        if (target == null) return;
        
        float x = target[0];
        float y = target[1];
        float z = target[2];
        
        float progressPercent = progress / breakTime;
        int stage = (int)(progressPercent * 10); // 0-9 stages like Minecraft
        
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Draw darkening overlay
        drawBreakOverlay(x, y, z, progressPercent);
        
        // Draw crack lines
        if (stage > 0) {
            drawCracks(x, y, z, stage);
        }
        
        glDisable(GL_BLEND);
        glEnable(GL_CULL_FACE);
        glEnable(GL_TEXTURE_2D);
    }
    
    private void drawBreakOverlay(float x, float y, float z, float progress) {
        float s = 0.502f;
        float alpha = 0.1f + progress * 0.4f;
        
        glDepthMask(false);
        glColor4f(0, 0, 0, alpha);
        
        glBegin(GL_QUADS);
        // Front
        glVertex3f(x - s, y - s, z + s);
        glVertex3f(x + s, y - s, z + s);
        glVertex3f(x + s, y + s, z + s);
        glVertex3f(x - s, y + s, z + s);
        // Back
        glVertex3f(x + s, y - s, z - s);
        glVertex3f(x - s, y - s, z - s);
        glVertex3f(x - s, y + s, z - s);
        glVertex3f(x + s, y + s, z - s);
        // Top
        glVertex3f(x - s, y + s, z + s);
        glVertex3f(x + s, y + s, z + s);
        glVertex3f(x + s, y + s, z - s);
        glVertex3f(x - s, y + s, z - s);
        // Bottom
        glVertex3f(x - s, y - s, z - s);
        glVertex3f(x + s, y - s, z - s);
        glVertex3f(x + s, y - s, z + s);
        glVertex3f(x - s, y - s, z + s);
        // Right
        glVertex3f(x + s, y - s, z + s);
        glVertex3f(x + s, y - s, z - s);
        glVertex3f(x + s, y + s, z - s);
        glVertex3f(x + s, y + s, z + s);
        // Left
        glVertex3f(x - s, y - s, z - s);
        glVertex3f(x - s, y - s, z + s);
        glVertex3f(x - s, y + s, z + s);
        glVertex3f(x - s, y + s, z - s);
        glEnd();
        
        glDepthMask(true);
    }
    
    private void drawCracks(float x, float y, float z, int stage) {
        float s = 0.503f;
        
        glLineWidth(2.0f);
        glColor4f(0, 0, 0, 0.7f);
        
        // Number of cracks based on stage
        int numCracks = Math.min(stage * 2, 10);
        
        glBegin(GL_LINES);
        
        for (int i = 0; i < numCracks; i++) {
            // Use precomputed random offsets for consistent cracks
            float ox1 = (crackOffsets[i % 20] - 0.5f) * 0.8f;
            float oy1 = (crackOffsets[(i + 3) % 20] - 0.5f) * 0.8f;
            float ox2 = (crackOffsets[(i + 7) % 20] - 0.5f) * 0.8f;
            float oy2 = (crackOffsets[(i + 11) % 20] - 0.5f) * 0.8f;
            
            // Draw on different faces based on crack index
            int face = i % 6;
            float offset = 0.001f * (i + 1); // Prevent z-fighting between cracks
            
            switch (face) {
                case 0: // Front (Z+)
                    glVertex3f(x + ox1 * 0.5f, y + oy1 * 0.5f, z + s + offset);
                    glVertex3f(x + ox2 * 0.5f, y + oy2 * 0.5f, z + s + offset);
                    // Branch crack
                    if (stage > 3) {
                        glVertex3f(x + ox2 * 0.5f, y + oy2 * 0.5f, z + s + offset);
                        glVertex3f(x + ox2 * 0.3f, y - 0.3f, z + s + offset);
                    }
                    break;
                case 1: // Back (Z-)
                    glVertex3f(x + ox1 * 0.5f, y + oy1 * 0.5f, z - s - offset);
                    glVertex3f(x + ox2 * 0.5f, y + oy2 * 0.5f, z - s - offset);
                    break;
                case 2: // Top (Y+)
                    glVertex3f(x + ox1 * 0.5f, y + s + offset, z + oy1 * 0.5f);
                    glVertex3f(x + ox2 * 0.5f, y + s + offset, z + oy2 * 0.5f);
                    break;
                case 3: // Bottom (Y-)
                    glVertex3f(x + ox1 * 0.5f, y - s - offset, z + oy1 * 0.5f);
                    glVertex3f(x + ox2 * 0.5f, y - s - offset, z + oy2 * 0.5f);
                    break;
                case 4: // Right (X+)
                    glVertex3f(x + s + offset, y + oy1 * 0.5f, z + ox1 * 0.5f);
                    glVertex3f(x + s + offset, y + oy2 * 0.5f, z + ox2 * 0.5f);
                    break;
                case 5: // Left (X-)
                    glVertex3f(x - s - offset, y + oy1 * 0.5f, z + ox1 * 0.5f);
                    glVertex3f(x - s - offset, y + oy2 * 0.5f, z + ox2 * 0.5f);
                    break;
            }
        }
        
        // Add more complex cracks at higher stages
        if (stage > 5) {
            float crackDepth = (stage - 5) * 0.05f;
            
            // Central star crack pattern on front
            glVertex3f(x, y, z + s + 0.001f);
            glVertex3f(x + 0.3f, y + 0.2f, z + s + 0.001f);
            glVertex3f(x, y, z + s + 0.001f);
            glVertex3f(x - 0.2f, y + 0.35f, z + s + 0.001f);
            glVertex3f(x, y, z + s + 0.001f);
            glVertex3f(x - 0.3f, y - 0.15f, z + s + 0.001f);
            glVertex3f(x, y, z + s + 0.001f);
            glVertex3f(x + 0.15f, y - 0.3f, z + s + 0.001f);
        }
        
        glEnd();
    }
    
    public void renderHighlight() {
        int[] target = player.getLookingAt();
        if (target == null) return;
        
        float x = target[0];
        float y = target[1];
        float z = target[2];
        float s = 0.505f;
        
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_CULL_FACE);
        glLineWidth(2.0f);
        glColor4f(0, 0, 0, 0.4f);
        
        // Front face outline
        glBegin(GL_LINE_LOOP);
        glVertex3f(x - s, y - s, z + s);
        glVertex3f(x + s, y - s, z + s);
        glVertex3f(x + s, y + s, z + s);
        glVertex3f(x - s, y + s, z + s);
        glEnd();
        
        // Back face outline
        glBegin(GL_LINE_LOOP);
        glVertex3f(x - s, y - s, z - s);
        glVertex3f(x + s, y - s, z - s);
        glVertex3f(x + s, y + s, z - s);
        glVertex3f(x - s, y + s, z - s);
        glEnd();
        
        // Connecting lines
        glBegin(GL_LINES);
        glVertex3f(x - s, y - s, z - s); glVertex3f(x - s, y - s, z + s);
        glVertex3f(x + s, y - s, z - s); glVertex3f(x + s, y - s, z + s);
        glVertex3f(x + s, y + s, z - s); glVertex3f(x + s, y + s, z + s);
        glVertex3f(x - s, y + s, z - s); glVertex3f(x - s, y + s, z + s);
        glEnd();
        
        glEnable(GL_CULL_FACE);
        glEnable(GL_TEXTURE_2D);
    }
    
    public float getProgress() {
        return progress / breakTime;
    }
}