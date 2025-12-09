package net.himeki.serverchan.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.UUID;

/**
 * Resolves a player's permission level across Forge/NeoForge versions where
 * the underlying MinecraftServer API keeps changing its parameter types.
 */
public final class PermissionUtil {

    private static final Class<?> GAME_PROFILE_CLASS = findClass("com.mojang.authlib.GameProfile");
    private static final Class<?> MINECRAFT_SERVER_CLASS = findClass("net.minecraft.server.MinecraftServer");

    private static final Method GET_PROFILE_PERMISSIONS =
            findServerMethod("getProfilePermissions", GAME_PROFILE_CLASS);
    private static final Method GET_PERMISSION_LEVEL =
            findServerMethod("getPermissionLevel", GAME_PROFILE_CLASS);

    private static final Class<?> NAME_AND_ID_CLASS;
    private static final Method NAME_AND_ID_PERMISSIONS_METHOD;
    private static final Constructor<?> NAME_AND_ID_PROFILE_CTOR;
    private static final Constructor<?> NAME_AND_ID_UUID_CTOR;
    private static final Method NAME_AND_ID_FACTORY;

    static {
        Class<?> nameAndIdClass = null;
        Method permissionsMethod = null;
        Constructor<?> profileCtor = null;
        Constructor<?> uuidCtor = null;
        Method factory = null;

        try {
            nameAndIdClass = Class.forName("net.minecraft.server.players.NameAndId");
            permissionsMethod = findServerMethod("getProfilePermissions", nameAndIdClass);
            if (GAME_PROFILE_CLASS != null) {
                try {
                    profileCtor = nameAndIdClass.getDeclaredConstructor(GAME_PROFILE_CLASS);
                    profileCtor.setAccessible(true);
                } catch (ReflectiveOperationException ignored) {
                }
            }
            try {
                uuidCtor = nameAndIdClass.getDeclaredConstructor(UUID.class, String.class);
                uuidCtor.setAccessible(true);
            } catch (ReflectiveOperationException ignored) {
            }
            factory = findStaticFactory(nameAndIdClass);
        } catch (ClassNotFoundException ignored) {
            // Older versions do not provide NameAndId, fall back to legacy APIs
        }

        NAME_AND_ID_CLASS = nameAndIdClass;
        NAME_AND_ID_PERMISSIONS_METHOD = permissionsMethod;
        NAME_AND_ID_PROFILE_CTOR = profileCtor;
        NAME_AND_ID_UUID_CTOR = uuidCtor;
        NAME_AND_ID_FACTORY = factory;
    }

    private PermissionUtil() {
    }

    /**
     * Attempts to resolve the permission level for the given profile via whatever
     * MinecraftServer API is available on the running version.
     */
    public static int getPermissionLevel(Object server, Object profile) {
        if (server == null || profile == null) {
            return 0;
        }

        Integer value = invoke(server, GET_PROFILE_PERMISSIONS, profile);
        if (value != null) {
            return value;
        }

        value = invoke(server, GET_PERMISSION_LEVEL, profile);
        if (value != null) {
            return value;
        }

        Object nameAndId = createNameAndId(profile);
        if (nameAndId != null) {
            value = invoke(server, NAME_AND_ID_PERMISSIONS_METHOD, nameAndId);
            if (value != null) {
                return value;
            }
        }

        return 0;
    }

    private static Integer invoke(Object server, Method method, Object argument) {
        if (method == null || server == null) {
            return null;
        }
        try {
            Object result = method.invoke(server, argument);
            return (result instanceof Integer i) ? i : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object createNameAndId(Object profile) {
        if (NAME_AND_ID_CLASS == null || profile == null) {
            return null;
        }

        try {
            if (NAME_AND_ID_PROFILE_CTOR != null && GAME_PROFILE_CLASS != null && GAME_PROFILE_CLASS.isInstance(profile)) {
                return NAME_AND_ID_PROFILE_CTOR.newInstance(profile);
            }
            if (NAME_AND_ID_FACTORY != null) {
                return NAME_AND_ID_FACTORY.invoke(null, profile);
            }
            if (NAME_AND_ID_UUID_CTOR != null) {
                UUID id = extractUuid(profile);
                String name = extractName(profile);
                if (id != null && name != null) {
                    return NAME_AND_ID_UUID_CTOR.newInstance(id, name);
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private static Method findServerMethod(String name, Class<?>... parameterTypes) {
        if (MINECRAFT_SERVER_CLASS == null) {
            return null;
        }
        try {
            Method method = MINECRAFT_SERVER_CLASS.getMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Method findStaticFactory(Class<?> nameAndIdClass) {
        if (nameAndIdClass == null) {
            return null;
        }
        for (Method method : nameAndIdClass.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 1 && (GAME_PROFILE_CLASS == null || params[0].isAssignableFrom(GAME_PROFILE_CLASS))) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static Class<?> findClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private static UUID extractUuid(Object profile) {
        try {
            Method method = profile.getClass().getMethod("getId");
            Object value = method.invoke(profile);
            return (value instanceof UUID uuid) ? uuid : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String extractName(Object profile) {
        try {
            Method method = profile.getClass().getMethod("getName");
            Object value = method.invoke(profile);
            return (value instanceof String str) ? str : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
