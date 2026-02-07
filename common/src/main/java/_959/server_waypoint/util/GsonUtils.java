package _959.server_waypoint.util;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Set;
import java.util.function.Supplier;

public class GsonUtils {
    public static class DynamicExclusionStrategy implements ExclusionStrategy {
        private final Supplier<Boolean> shouldExclude;
        private final @Unmodifiable Set<String> fieldsToSkip;

        public DynamicExclusionStrategy(Supplier<Boolean> shouldExclude, String... fields) {
            this.shouldExclude = shouldExclude;
            this.fieldsToSkip = Set.of(fields);
        }

        @Override
        public boolean shouldSkipField(FieldAttributes f) {
            if (shouldExclude.get()) {
                return fieldsToSkip.contains(f.getName());
            } else {
                return false;
            }
        }

        @Override
        public boolean shouldSkipClass(Class<?> clazz) {
            return false;
        }
    }
}
