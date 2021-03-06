package ai.platon.pulsar.common;

import org.apache.hadoop.io.serializer.SerializationFactory;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by vincent on 17-3-2.
 */
public class ReflectionUtils {

    public static final Logger LOG = LoggerFactory.getLogger(ReflectionUtils.class);

    private static final Map<Class<?>, Constructor<?>> CONSTRUCTOR_CACHE = new ConcurrentHashMap<>();

    private static final Class<?>[] EMPTY_ARRAY = new Class[]{};

    volatile private static SerializationFactory serialFactory = null;

    public static <T> T newInstance(Class<T> theClass) {
        T result;
        try {
            Constructor<T> constructor = (Constructor<T>) CONSTRUCTOR_CACHE.get(theClass);
            if (constructor == null) {
                constructor = theClass.getDeclaredConstructor(EMPTY_ARRAY);
                constructor.setAccessible(true);
                CONSTRUCTOR_CACHE.put(theClass, constructor);
            }
            result = constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    public static <T> T forName(@NotNull String className) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
         return (T)Class.forName(className).newInstance();
    }

    public static <T> T forNameOrNull(@NotNull String className) {
        try {
            return (T)Class.forName(className).newInstance();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        return null;
    }
}
