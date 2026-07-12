package ru.alliancemc.alliancebots.bot;

import java.lang.reflect.Field;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class RotationController {
    private final ClipBotTrait bot;

    public RotationController(ClipBotTrait bot) {
        this.bot = bot;
    }

    public void lookAt(Player target) {
        Entity entity = bot.getNPC().getEntity();
        if (!(entity instanceof Player) || target == null) {
            return;
        }
        Location from = entity.getLocation();
        Location targetLocation = target.getLocation();
        Vector direction = targetLocation.toVector().subtract(from.toVector());
        direction.setY(0.0D);
        if (direction.lengthSquared() < 0.0001) {
            return;
        }

        float targetYaw = yawFromDirection(direction);
        float targetPitch = 0.0F;
        float yaw = targetYaw;
        float pitch = targetPitch;
        if (bot.getSettings().isSmoothRotation()) {
            Location current = entity.getLocation();
            yaw = approachAngle(current.getYaw(), targetYaw, (float) bot.getSettings().getMaxYawChangePerTick());
            pitch = approachAngle(current.getPitch(), targetPitch, (float) bot.getSettings().getMaxPitchChangePerTick());
        }
        setRotation(entity, yaw, pitch);
    }

    private float yawFromDirection(Vector direction) {
        return (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
    }

    private void setRotation(Entity entity, float yaw, float pitch) {
        try {
            Object handle = entity.getClass().getMethod("getHandle").invoke(entity);
            setFloat(handle, "yaw", yaw);
            setFloat(handle, "pitch", pitch);
            setFloat(handle, "lastYaw", yaw);
            setFloat(handle, "lastPitch", pitch);
            setFloatIfPresent(handle, "aK", yaw);
            setFloatIfPresent(handle, "aI", yaw);
            Location location = entity.getLocation();
            location.setYaw(yaw);
            location.setPitch(pitch);
        } catch (Exception ignored) {
            Location location = entity.getLocation();
            location.setYaw(yaw);
            location.setPitch(pitch);
        }
    }

    private void setFloat(Object target, String fieldName, float value) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        if (field == null) {
            throw new NoSuchFieldException(fieldName);
        }
        field.setAccessible(true);
        field.setFloat(target, value);
    }

    private void setFloatIfPresent(Object target, String fieldName, float value) {
        try {
            setFloat(target, fieldName, value);
        } catch (Exception ignored) {
            // Field name is version-specific.
        }
    }

    private Field findField(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private float approachAngle(float current, float target, float maxChange) {
        float delta = wrapDegrees(target - current);
        if (delta > maxChange) {
            delta = maxChange;
        } else if (delta < -maxChange) {
            delta = -maxChange;
        }
        return current + delta;
    }

    private float wrapDegrees(float value) {
        while (value <= -180.0F) {
            value += 360.0F;
        }
        while (value > 180.0F) {
            value -= 360.0F;
        }
        return value;
    }
}
