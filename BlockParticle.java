import static org.lwjgl.opengl.GL11.*;
import java.util.*;

public class BlockParticle {
    private static List<BlockParticle> particles = new ArrayList<>();
    private static Random rand = new Random();
    
    // Particle state
    private float x, y, z;
    private float vx, vy, vz;
    private float size;
    private int blockType;
    private float lifetime;
    private float maxLifetime;
    private float gravity = 0.0015f;
    private float groundY;
    private boolean onGround = false;
    private float r, g, b;
    private float rotation;
    private float rotationSpeed;
    
    // World reference for ground detection
    private World world;
    
    public BlockParticle(float x, float y, float z, int blockType, World world) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockType = blockType;
        this.world = world;
        
        // Random velocity - like Minecraft, spread outward and up
        float angle = rand.nextFloat() * (float)Math.PI * 2;
        float speed = 0.03f + rand.nextFloat() * 0.05f;
        this.vx = (float)Math.cos(angle) * speed;
        this.vz = (float)Math.sin(angle) * speed;
        this.vy = 0.05f + rand.nextFloat() * 0.08f;
        
        // Small size like Minecraft
        this.size = 0.02f + rand.nextFloat() * 0.03f;
        
        // Lifetime - particles disappear after landing
        this.maxLifetime = 40 + rand.nextInt(30); // ~0.7-1.2 seconds at 60fps
        this.lifetime = maxLifetime;
        
        // Find actual ground below particle
        this.groundY = findGround((int)Math.floor(x), (int)Math.floor(y), (int)Math.floor(z));
        
        // Random rotation
        this.rotation = rand.nextFloat() * 360;
        this.rotationSpeed = (rand.nextFloat() - 0.5f) * 10;
        
        // Set color based on block type
        setColor();
    }
    
    private float findGround(int bx, int startY, int bz) {
        // Search downward for solid block
        for (int y = startY; y >= 0; y--) {
            if (world.getBlock(bx, y, bz) != Game.AIR) {
                return y + 1.0f; // Top of the block
            }
        }
        return 0;
    }
    
    private void setColor() {
        switch(blockType) {
            case Game.STONE: 
                r = 0.45f + rand.nextFloat() * 0.15f;
                g = 0.45f + rand.nextFloat() * 0.15f;
                b = 0.45f + rand.nextFloat() * 0.15f;
                break;
            case Game.DIRT: 
                r = 0.45f + rand.nextFloat() * 0.15f;
                g = 0.28f + rand.nextFloat() * 0.1f;
                b = 0.12f + rand.nextFloat() * 0.08f;
                break;
            case Game.GRASS: 
                // Mix of green (top) and brown (sides)
                if (rand.nextFloat() < 0.3f) {
                    r = 0.25f + rand.nextFloat() * 0.1f;
                    g = 0.5f + rand.nextFloat() * 0.15f;
                    b = 0.15f + rand.nextFloat() * 0.1f;
                } else {
                    r = 0.45f + rand.nextFloat() * 0.15f;
                    g = 0.28f + rand.nextFloat() * 0.1f;
                    b = 0.12f + rand.nextFloat() * 0.08f;
                }
                break;
            case Game.LOG: 
                r = 0.4f + rand.nextFloat() * 0.15f;
                g = 0.28f + rand.nextFloat() * 0.1f;
                b = 0.15f + rand.nextFloat() * 0.08f;
                break;
            case Game.LEAVES: 
                r = 0.15f + rand.nextFloat() * 0.1f;
                g = 0.4f + rand.nextFloat() * 0.15f;
                b = 0.1f + rand.nextFloat() * 0.08f;
                break;
            default: 
                r = 0.5f; g = 0.5f; b = 0.5f;
        }
    }
    
    public static void spawnBreakParticles(int bx, int by, int bz, int blockType, World world) {
        // Spawn 20-30 particles like Minecraft
        int count = 20 + rand.nextInt(10);
        
        for (int i = 0; i < count; i++) {
            // Spawn from random position within the block
            float px = bx + 0.2f + rand.nextFloat() * 0.6f;
            float py = by + 0.2f + rand.nextFloat() * 0.6f;
            float pz = bz + 0.2f + rand.nextFloat() * 0.6f;
            
            particles.add(new BlockParticle(px, py, pz, blockType, world));
        }
    }
    
    public static void updateAll() {
        Iterator<BlockParticle> iter = particles.iterator();
        while (iter.hasNext()) {
            BlockParticle p = iter.next();
            p.update();
            if (p.isDead()) {
                iter.remove();
            }
        }
    }
    
    public static void renderAll(float playerX, float playerZ) {
        if (particles.isEmpty()) return;
        
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        for (BlockParticle p : particles) {
            float dx = p.x - playerX;
            float dz = p.z - playerZ;
            if (dx * dx + dz * dz < 400) {
                p.render();
            }
        }
        
        glDisable(GL_BLEND);
        glEnable(GL_CULL_FACE);
        glEnable(GL_TEXTURE_2D);
    }
    
    public void update() {
        // Update ground position (in case blocks changed)
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        groundY = findGround(bx, (int)y + 1, bz);
        
        if (!onGround) {
            // Apply velocity
            x += vx;
            y += vy;
            z += vz;
            
            // Apply gravity
            vy -= gravity;
            
            // Air resistance
            vx *= 0.98f;
            vz *= 0.98f;
            
            // Rotation
            rotation += rotationSpeed;
            
            // Ground collision
            if (y - size/2 <= groundY) {
                y = groundY + size/2;
                
                // Bounce (very small)
                if (Math.abs(vy) > 0.01f) {
                    vy = -vy * 0.2f;
                    vx *= 0.6f;
                    vz *= 0.6f;
                } else {
                    vy = 0;
                    onGround = true;
                    rotationSpeed = 0;
                }
            }
        } else {
            // On ground - just slide and slow down
            x += vx;
            z += vz;
            vx *= 0.85f;
            vz *= 0.85f;
            
            // Keep on ground
            y = groundY + size/2;
        }
        
        // Decrease lifetime
        lifetime--;
        
        // Faster fade when on ground
        if (onGround) {
            lifetime -= 0.5f;
        }
    }
    
    public void render() {
        // Fade out in last 10 frames
        float alpha = 1.0f;
        if (lifetime < 10) {
            alpha = lifetime / 10.0f;
        }
        
        // Particle size stays constant (like Minecraft)
        float s = size;
        
        glPushMatrix();
        glTranslatef(x, y, z);
        glRotatef(rotation, 0, 1, 0);
        
        glColor4f(r, g, b, alpha);
        
        // Draw small cube
        glBegin(GL_QUADS);
        
        // Front
        glVertex3f(-s, -s, s);
        glVertex3f(s, -s, s);
        glVertex3f(s, s, s);
        glVertex3f(-s, s, s);
        
        // Back
        glVertex3f(s, -s, -s);
        glVertex3f(-s, -s, -s);
        glVertex3f(-s, s, -s);
        glVertex3f(s, s, -s);
        
        // Top
        glVertex3f(-s, s, s);
        glVertex3f(s, s, s);
        glVertex3f(s, s, -s);
        glVertex3f(-s, s, -s);
        
        // Bottom
        glVertex3f(-s, -s, -s);
        glVertex3f(s, -s, -s);
        glVertex3f(s, -s, s);
        glVertex3f(-s, -s, s);
        
        // Right
        glVertex3f(s, -s, s);
        glVertex3f(s, -s, -s);
        glVertex3f(s, s, -s);
        glVertex3f(s, s, s);
        
        // Left
        glVertex3f(-s, -s, -s);
        glVertex3f(-s, -s, s);
        glVertex3f(-s, s, s);
        glVertex3f(-s, s, -s);
        
        glEnd();
        
        glPopMatrix();
    }
    
    public boolean isDead() {
        return lifetime <= 0;
    }
    
    public static int getParticleCount() {
        return particles.size();
    }
    
    public static void clear() {
        particles.clear();
    }
}