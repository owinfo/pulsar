package ai.platon.pulsar.common.config;

/**
 * Created by vincent on 17-1-8.
 * @deprecated Reloadable is not a good concept, use JobInitialized, Configurable, Parameterized instead
 */
@Deprecated
public interface ReloadableParameterized extends Parameterized {
    ImmutableConfig getConf();

    /**
     * TODO: need a better name: reset/setup/init, etc
     * */
    default void reload(ImmutableConfig conf) {
    }

    default void reload(Params params) {
    }
}
