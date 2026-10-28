package io.github.fchatcolor.api.util;

/**
 * Mini-herramienta de reflection utilizada para sondear el runtime del
 * servidor sin necesidad de compile-time dependencies con APIs modernas.
 *
 * <p>Es la base del soporte multi-version: en lugar de asumir una version
 * concreta, preguntamos "¿existe esta clase/método?".</p>
 */
public final class Reflection {

    private Reflection() {
    }

    /** {@code true} si la clase puede cargarse en el classloader actual. */
    public static boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** {@code true} si la clase puede cargarse usando el classloader indicado. */
    public static boolean classExists(String className, ClassLoader loader) {
        try {
            Class.forName(className, false, loader);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Carga la clase o devuelve {@code null} si no existe. */
    public static Class<?> findClass(String className) {
        try {
            return Class.forName(className);
        } catch (Throwable t) {
            return null;
        }
    }

    /** {@code true} si la clase expone el método indicado (se ignoran los errores de acceso). */
    public static boolean methodExists(Class<?> clazz, String methodName, Class<?>... parameters) {
        if (clazz == null) {
            return false;
        }
        try {
            clazz.getMethod(methodName, parameters);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** {@link Class#getMethod(String, Class...)} en version "no lanza excepción". */
    public static java.lang.reflect.Method findMethod(Class<?> clazz, String methodName, Class<?>... parameters) {
        if (clazz == null) {
            return null;
        }
        try {
            return clazz.getMethod(methodName, parameters);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Invoca un método estático; devuelve {@code null} si algo falla. */
    public static Object invokeStatic(Class<?> clazz, String methodName, Class<?>[] signature, Object[] args) {
        if (clazz == null) {
            return null;
        }
        try {
            java.lang.reflect.Method method = clazz.getMethod(methodName, signature);
            return method.invoke(null, args);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Invoca un método de instancia; devuelve {@code null} si algo falla. */
    public static Object invoke(Object target, String methodName, Class<?>[] signature, Object[] args) {
        if (target == null) {
            return null;
        }
        try {
            java.lang.reflect.Method method = target.getClass().getMethod(methodName, signature);
            return method.invoke(target, args);
        } catch (Throwable t) {
            return null;
        }
    }

    /** {@code true} si la clase expone el método estático indicado. */
    public static boolean staticMethodExists(Class<?> clazz, String methodName, Class<?>... parameters) {
        return methodExists(clazz, methodName, parameters);
    }
}
