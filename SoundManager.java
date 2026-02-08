import javax.sound.sampled.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class SoundManager {
    private static Map<String, Clip> sounds = new HashMap<>();
    private static boolean initialized = false;
    
    public static void init() {
        if (initialized) return;
        initialized = true;
        
        System.out.println("Loading sounds...");
        
        // Load all sounds with silent fail
        String[] names = {"stone", "dirt", "grass", "wood", "leaves"};
        String[] types = {"dig", "break", "place"};
        
        int count = 0;
        for (String name : names) {
            for (String type : types) {
                String key = name + "_" + type;
                String path = "sounds/" + key + ".wav";
                if (loadSound(key, path)) count++;
            }
        }
        
        System.out.println("Loaded " + count + " sounds");
    }
    
    private static boolean loadSound(String name, String path) {
        try {
            File file = new File(path);
            if (!file.exists()) return false;
            
            AudioInputStream audioIn = AudioSystem.getAudioInputStream(file);
            Clip clip = AudioSystem.getClip();
            clip.open(audioIn);
            sounds.put(name, clip);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private static void play(String name) {
        if (!initialized) init();
        
        Clip clip = sounds.get(name);
        if (clip == null) return;
        
        try {
            clip.stop();
            clip.setFramePosition(0);
            clip.start();
        } catch (Exception e) {}
    }
    
    public static void playDig(int blockType) {
        play(getBlockName(blockType) + "_dig");
    }
    
    public static void playBreak(int blockType) {
        String name = getBlockName(blockType) + "_break";
        if (!sounds.containsKey(name)) {
            name = getBlockName(blockType) + "_dig";
        }
        play(name);
    }
    
    public static void playPlace(int blockType) {
        String name = getBlockName(blockType) + "_place";
        if (!sounds.containsKey(name)) {
            name = getBlockName(blockType) + "_dig";
        }
        play(name);
    }
    
    private static String getBlockName(int blockType) {
        switch (blockType) {
            case Game.STONE: return "stone";
            case Game.DIRT: return "dirt";
            case Game.GRASS: return "grass";
            case Game.LOG: return "wood";
            case Game.LEAVES: return "leaves";
            default: return "dirt";
        }
    }
    
    public static void cleanup() {
        for (Clip clip : sounds.values()) {
            if (clip != null) {
                clip.stop();
                clip.close();
            }
        }
        sounds.clear();
    }
}