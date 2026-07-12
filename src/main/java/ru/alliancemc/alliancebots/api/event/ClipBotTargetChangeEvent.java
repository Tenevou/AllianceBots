package ru.alliancemc.alliancebots.api.event;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import ru.alliancemc.alliancebots.bot.ClipBotTrait;

public final class ClipBotTargetChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final ClipBotTrait bot;
    private final UUID oldTarget;
    private final Player newTarget;

    public ClipBotTargetChangeEvent(ClipBotTrait bot, UUID oldTarget, Player newTarget) {
        this.bot = bot;
        this.oldTarget = oldTarget;
        this.newTarget = newTarget;
    }

    public ClipBotTrait getBot() {
        return bot;
    }

    public UUID getOldTarget() {
        return oldTarget;
    }

    public Player getNewTarget() {
        return newTarget;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
