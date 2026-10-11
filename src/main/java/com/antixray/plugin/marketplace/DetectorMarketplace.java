package com.antixray.plugin.marketplace;

import com.antixray.plugin.AntiXrayPlugin;
import com.antixray.plugin.detection.Detector;
import com.antixray.plugin.detection.DetectionModule;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class DetectorMarketplace {
    private final AntiXrayPlugin plugin;
    private final List<Detector> externalDetectors = new ArrayList<>();

    public DetectorMarketplace(AntiXrayPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadExternalDetectors() {
        File detectorsDir = new File(plugin.getDataFolder(), "detectors");
        if (!detectorsDir.exists()) {
            detectorsDir.mkdirs();
            return;
        }

        File[] jars = detectorsDir.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jars == null) return;

        for (File jar : jars) {
            try {
                loadDetectorFromJar(jar);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load external detector: " + jar.getName() + " - " + e.getMessage());
            }
        }

        plugin.getLogger().info("Loaded " + externalDetectors.size() + " external detectors");
    }

    private void loadDetectorFromJar(File jar) throws Exception {
        try (JarFile jarFile = new JarFile(jar)) {
            URLClassLoader loader = new URLClassLoader(
                    new URL[]{jar.toURI().toURL()},
                    getClass().getClassLoader()
            );

            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                if (entry.getName().contains("module-info") || entry.getName().contains("package-info")) continue;

                String className = entry.getName()
                        .replace('/', '.')
                        .replace(".class", "");

                try {
                    Class<?> clazz = Class.forName(className, true, loader);
                    if (Detector.class.isAssignableFrom(clazz) && !clazz.equals(Detector.class)) {
                        Detector detector = (Detector) clazz.getDeclaredConstructor().newInstance();
                        externalDetectors.add(detector);
                        plugin.getLogger().info("  Loaded detector: " + detector.getName() + " from " + jar.getName());
                    }
                } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
                }
            }

            loader.close();
        }
    }

    public void registerAll(DetectionModule detectionModule) {
        for (Detector d : externalDetectors) {
            detectionModule.registerExternal(d);
        }
    }

    public List<Detector> getExternalDetectors() {
        return Collections.unmodifiableList(externalDetectors);
    }
}
