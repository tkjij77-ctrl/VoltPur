package org.purpurmc.purpur.plus;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * Isolated ClassLoader for VoltPur Plus Modules (§6.2, §6.4).
 *
 * Implements child-first delegation for module classes, isolating third-party dependencies
 * while sharing standard Minecraft/VoltPur server APIs from parent.
 */
public class PlusClassLoader extends URLClassLoader {

    public PlusClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            // 1. Check if class is already loaded
            Class<?> loadedClass = findLoadedClass(name);
            if (loadedClass != null) {
                if (resolve) resolveClass(loadedClass);
                return loadedClass;
            }

            // 2. Delegate fundamental platform/API packages directly to parent
            if (name.startsWith("java.") || name.startsWith("javax.") ||
                name.startsWith("org.bukkit.") || name.startsWith("io.papermc.paper.") ||
                name.startsWith("net.minecraft.") || name.startsWith("org.purpurmc.purpur.plus.VoltPurPlusModule") ||
                name.startsWith("org.purpurmc.purpur.plus.PlusContext")) {
                return super.loadClass(name, resolve);
            }

            // 3. Child-first: try loading from this module's JAR first
            try {
                Class<?> c = findClass(name);
                if (resolve) resolveClass(c);
                return c;
            } catch (ClassNotFoundException ignored) {
                // Not found locally in jar; fall back to parent
            }

            // 4. Delegate to parent class loader
            return super.loadClass(name, resolve);
        }
    }
}
