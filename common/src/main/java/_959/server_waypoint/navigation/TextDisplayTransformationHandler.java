package _959.server_waypoint.navigation;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Optional handler capability for applying a complete text display
 * transformation without retaining it in shared navigation state.
 */
public interface TextDisplayTransformationHandler<P> extends NavigationMethodHandler<P> {
    void applyTransformation(
            P player,
            Vector3f translation,
            Quaternionf rotation,
            Vector3f scale
    );
}
