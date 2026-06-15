package _959.server_waypoint.util;

import _959.server_waypoint.core.waypoint.WaypointPos;

public final class CoordinateInputParser {
    private CoordinateInputParser() {
    }

    public static WaypointPos resolve(String xText, String yText, String zText, WaypointPos playerPos, WaypointPos defaultPos, float pitch, float yaw) {
        CoordinateExpression x = CoordinateExpression.parse(xText, defaultPos.x());
        CoordinateExpression y = CoordinateExpression.parse(yText, defaultPos.y());
        CoordinateExpression z = CoordinateExpression.parse(zText, defaultPos.z());
        validateLocalCoordinateMode(x, y, z);

        if (!x.local() && !y.local() && !z.local()) {
            return new WaypointPos(
                    x.resolve(playerPos.x(), defaultPos.x()),
                    y.resolve(playerPos.y(), defaultPos.y()),
                    z.resolve(playerPos.z(), defaultPos.z())
            );
        }

        Vector3 localOffset = applyLocalCoordinatesToRotation(pitch, yaw, x.localValue(), y.localValue(), z.localValue());
        int resolvedX = floor(playerPos.x() + localOffset.x());
        int resolvedY = floor(playerPos.y() + localOffset.y());
        int resolvedZ = floor(playerPos.z() + localOffset.z());

        if (!x.local() && !x.isUnchangedDefault()) {
            resolvedX = x.resolve(playerPos.x(), defaultPos.x());
        }
        if (!y.local() && !y.isUnchangedDefault()) {
            resolvedY = y.resolve(playerPos.y(), defaultPos.y());
        }
        if (!z.local() && !z.isUnchangedDefault()) {
            resolvedZ = z.resolve(playerPos.z(), defaultPos.z());
        }

        return new WaypointPos(resolvedX, resolvedY, resolvedZ);
    }

    public static boolean isCoordinateExpression(String text) {
        try {
            CoordinateExpression.parse(text, 0);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isLocalCoordinateExpression(String text) {
        try {
            return CoordinateExpression.parse(text, 0).local();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isMixedLocalCoordinateMode(String xText, String yText, String zText) {
        try {
            CoordinateExpression x = CoordinateExpression.parse(xText, 0);
            CoordinateExpression y = CoordinateExpression.parse(yText, 0);
            CoordinateExpression z = CoordinateExpression.parse(zText, 0);
            return hasMixedLocalCoordinateMode(x, y, z);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static void validateLocalCoordinateMode(CoordinateExpression x, CoordinateExpression y, CoordinateExpression z) {
        if (hasMixedLocalCoordinateMode(x, y, z)) {
            throw new IllegalArgumentException("Local coordinates cannot be mixed with absolute or relative coordinates.");
        }
    }

    private static boolean hasMixedLocalCoordinateMode(CoordinateExpression x, CoordinateExpression y, CoordinateExpression z) {
        boolean hasLocal = x.local() || y.local() || z.local();
        boolean hasNonLocal = !x.local() || !y.local() || !z.local();
        return hasLocal && hasNonLocal;
    }

    private static Vector3 applyLocalCoordinatesToRotation(float pitch, float yaw, double left, double up, double forwards) {
        float yawCos = cos((yaw + 90.0F) * degreesToRadians());
        float yawSin = sin((yaw + 90.0F) * degreesToRadians());
        float pitchCos = cos(-pitch * degreesToRadians());
        float pitchSin = sin(-pitch * degreesToRadians());
        float upPitchCos = cos((-pitch + 90.0F) * degreesToRadians());
        float upPitchSin = sin((-pitch + 90.0F) * degreesToRadians());
        Vector3 forward = new Vector3(yawCos * pitchCos, pitchSin, yawSin * pitchCos);
        Vector3 upVector = new Vector3(yawCos * upPitchCos, upPitchSin, yawSin * upPitchCos);
        Vector3 leftVector = forward.cross(upVector).scale(-1.0D);
        return new Vector3(
                forward.x() * forwards + upVector.x() * up + leftVector.x() * left,
                forward.y() * forwards + upVector.y() * up + leftVector.y() * left,
                forward.z() * forwards + upVector.z() * up + leftVector.z() * left
        );
    }

    private static float degreesToRadians() {
        return (float) (Math.PI / 180.0D);
    }

    private static float cos(float value) {
        return (float) Math.cos(value);
    }

    private static float sin(float value) {
        return (float) Math.sin(value);
    }

    private static int floor(double value) {
        double nearestInteger = Math.rint(value);
        if (Math.abs(value - nearestInteger) < 1.0E-5D) {
            return (int) nearestInteger;
        }
        int intValue = (int) value;
        return value < intValue ? intValue - 1 : intValue;
    }

    private record CoordinateExpression(Type type, int value, boolean isUnchangedDefault) {
        static CoordinateExpression parse(String text, int defaultValue) {
            String trimmed = text == null ? "" : text.trim();
            if (trimmed.isEmpty()) {
                return new CoordinateExpression(Type.ABSOLUTE, defaultValue, true);
            }

            Type type = switch (trimmed.charAt(0)) {
                case '~' -> Type.RELATIVE;
                case '^' -> Type.LOCAL;
                default -> Type.ABSOLUTE;
            };
            String numberText = type == Type.ABSOLUTE ? trimmed : trimmed.substring(1);
            int value = numberText.isEmpty() ? 0 : Integer.parseInt(numberText);
            return new CoordinateExpression(type, value, type == Type.ABSOLUTE && Integer.toString(defaultValue).equals(trimmed));
        }

        boolean local() {
            return this.type == Type.LOCAL;
        }

        double localValue() {
            return this.local() ? this.value : 0.0D;
        }

        int resolve(int playerCoordinate, int defaultValue) {
            return switch (this.type) {
                case ABSOLUTE -> this.isUnchangedDefault ? defaultValue : this.value;
                case RELATIVE -> playerCoordinate + this.value;
                case LOCAL -> playerCoordinate;
            };
        }
    }

    private enum Type {
        ABSOLUTE,
        RELATIVE,
        LOCAL
    }

    private record Vector3(double x, double y, double z) {
        Vector3 cross(Vector3 other) {
            return new Vector3(
                    this.y * other.z - this.z * other.y,
                    this.z * other.x - this.x * other.z,
                    this.x * other.y - this.y * other.x
            );
        }

        Vector3 scale(double scalar) {
            return new Vector3(this.x * scalar, this.y * scalar, this.z * scalar);
        }
    }
}
