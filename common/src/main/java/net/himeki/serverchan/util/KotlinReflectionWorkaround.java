package net.himeki.serverchan.util;

import net.himeki.serverchan.ServerChanCore;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * NeoForge jar-jar processing renames shaded Kotlin classes which prevents kotlin.jvm.internal.Reflection
 * from locating the real ReflectionFactory implementation. This helper reinstates the proper ReflectionFactory
 * so that jackson-module-kotlin can use Kotlin reflection without exploding.
 */
public final class KotlinReflectionWorkaround {
    private static final String[] SUFFIXES = new String[]{
            "",
            "_fabric",
            "_quilt",
            "_neoforge",
            "_forge"
    };

    private KotlinReflectionWorkaround() {
    }

    public static void ensureKotlinReflectionFactory() {
        ClassLoader loader = KotlinReflectionWorkaround.class.getClassLoader();
        Throwable lastError = null;

        for (String suffix : SUFFIXES) {
            String reflectionName = "kotlin.jvm.internal.Reflection" + suffix;
            String factoryName = "kotlin.reflect.jvm.internal.ReflectionFactoryImpl" + suffix;

            try {
                Class<?> reflectionClass = Class.forName(reflectionName, false, loader);
                Field factoryField = reflectionClass.getDeclaredField("factory");
                factoryField.setAccessible(true);
                Object currentFactory = factoryField.get(null);
                if (currentFactory != null && currentFactory.getClass().getName().contains("ReflectionFactoryImpl")) {
                    return;
                }

                Object replacement = instantiate(factoryName, loader);
                if (replacement == null && !suffix.isEmpty()) {
                    // Fall back to the unsuffixed implementation if the remapped one is absent
                    replacement = instantiate("kotlin.reflect.jvm.internal.ReflectionFactoryImpl", loader);
                }

                if (replacement == null) {
                    continue;
                }

                if (setFinalStatic(factoryField, replacement)) {
                    ServerChanCore.LOGGER.info("Patched Kotlin reflection factory to {}", replacement.getClass().getName());
                } else {
                    ServerChanCore.LOGGER.warn("Unable to patch Kotlin reflection factory via reflection; Kotlin integration may be limited.");
                }
                return;
            } catch (ClassNotFoundException e) {
                // Try next suffix if this namespace isn't present on the classpath
            } catch (Throwable t) {
                lastError = t;
                break;
            }
        }

        if (lastError != null) {
            ServerChanCore.LOGGER.error("Failed to initialize Kotlin reflection workaround", lastError);
        } else {
            ServerChanCore.LOGGER.warn("Unable to find Kotlin ReflectionFactory implementation; Kotlin integration will be limited.");
        }
    }

    private static Object instantiate(String className, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(className, false, loader);
            return clazz.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException ignored) {
            return null;
        } catch (Throwable t) {
            ServerChanCore.LOGGER.debug("Could not instantiate {}: {}", className, t.toString());
            return null;
        }
    }

    private static boolean setFinalStatic(Field field, Object value) {
        try {
            field.setAccessible(true);
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            try {
                modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
            } catch (IllegalAccessException ignored) {
                // If this fails we'll fall back to Unsafe later
            }
        } catch (NoSuchFieldException ignored) {
            // Field#modifiers was removed on newer JDKs, but Field#set still works after setAccessible(true)
        }

        try {
            field.set(null, value);
            return true;
        } catch (IllegalAccessException primary) {
            // Fall back to Unsafe for runtimes that still reject final static writes
            try {
                Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
                Field theUnsafeField = unsafeClass.getDeclaredField("theUnsafe");
                theUnsafeField.setAccessible(true);
                Object unsafe = theUnsafeField.get(null);

                Method staticFieldOffset = unsafeClass.getMethod("staticFieldOffset", Field.class);
                Method staticFieldBase = unsafeClass.getMethod("staticFieldBase", Field.class);
                Method putObjectVolatile = unsafeClass.getMethod("putObjectVolatile", Object.class, long.class, Object.class);

                Object base = staticFieldBase.invoke(unsafe, field);
                long offset = (long) staticFieldOffset.invoke(unsafe, field);
                putObjectVolatile.invoke(unsafe, base, offset, value);
                return true;
            } catch (Throwable unsafeFailure) {
                ServerChanCore.LOGGER.debug("Unsafe fallback failed while patching Kotlin reflection factory: {}", unsafeFailure.toString());
                return false;
            }
        } catch (Throwable t) {
            ServerChanCore.LOGGER.debug("Unexpected error while patching Kotlin reflection factory: {}", t.toString());
            return false;
        }
    }
}
