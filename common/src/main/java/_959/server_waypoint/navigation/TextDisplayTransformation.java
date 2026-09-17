package _959.server_waypoint.navigation;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;

/**
 * Per-player text display transformation adjustments. Translation and rotation
 * are offsets from the built-in base values; scale is a multiplier.
 */
public record TextDisplayTransformation(
        Vector3f translation,
        Vector3f rotation,
        Vector3f scale
) {
    public static final float MAX_TRANSLATION = 16.0F;
    public static final float MAX_ROTATION_DEGREES = 360.0F;
    public static final float MAX_SCALE_MULTIPLIER = 4.0F;
    private static final float BASE_SCALE = 0.22F;

    public TextDisplayTransformation {
        translation = copyAndValidate(
                translation,
                MAX_TRANSLATION,
                "translation offset"
        );
        rotation = copyAndValidate(
                rotation,
                MAX_ROTATION_DEGREES,
                "rotation offset"
        );
        scale = copyAndValidate(scale, MAX_SCALE_MULTIPLIER, "scale multiplier");
    }

    public Vector3f translation() {
        return new Vector3f(this.translation);
    }

    public Vector3f rotation() {
        return new Vector3f(this.rotation);
    }

    public Vector3f scale() {
        return new Vector3f(this.scale);
    }

    public static TextDisplayTransformation defaultValue() {
        return new TextDisplayTransformation(
                new Vector3f(),
                new Vector3f(),
                new Vector3f(1.0F)
        );
    }

    public static Vector3f baseTranslation() {
        return new Vector3f(0.0F, -0.45F, -1.2F);
    }

    public static Vector3f baseRotation() {
        return new Vector3f(-48.0F, 0.0F, 0.0F);
    }

    public static Vector3f baseScale() {
        return new Vector3f(BASE_SCALE, BASE_SCALE, BASE_SCALE);
    }

    public TextDisplayTransformation withTranslation(Vector3f value) {
        return new TextDisplayTransformation(value, this.rotation, this.scale);
    }

    public TextDisplayTransformation withRotation(Vector3f value) {
        return new TextDisplayTransformation(this.translation, value, this.scale);
    }

    public TextDisplayTransformation withScale(Vector3f value) {
        return new TextDisplayTransformation(this.translation, this.rotation, value);
    }

    public Vector3f resolvedTranslation() {
        return baseTranslation().add(this.translation);
    }

    public Vector3f resolvedRotation() {
        return baseRotation().add(this.rotation);
    }

    public Vector3f resolvedScale() {
        return baseScale().mul(this.scale);
    }

    public Quaternionf rotationQuaternion() {
        Vector3f resolvedRotation = this.resolvedRotation();
        return new Quaternionf().rotationXYZ(
                (float) Math.toRadians(resolvedRotation.x()),
                (float) Math.toRadians(resolvedRotation.y()),
                (float) Math.toRadians(resolvedRotation.z())
        );
    }

    private static Vector3f copyAndValidate(Vector3f vector, float maximum, String name) {
        Objects.requireNonNull(vector, name);
        if (!vector.isFinite()) {
            throw new IllegalArgumentException(name + " values must be finite");
        }
        if (Math.abs(vector.x()) > maximum
                || Math.abs(vector.y()) > maximum
                || Math.abs(vector.z()) > maximum) {
            throw new IllegalArgumentException(name + " values must be within ±" + maximum);
        }
        return new Vector3f(vector);
    }
}
