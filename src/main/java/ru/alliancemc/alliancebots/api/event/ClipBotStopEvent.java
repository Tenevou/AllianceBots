package ru.alliancemc.alliancebots.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import ru.alliancemc.alliancebots.bot.ClipBotTrait;

public final class ClipBotStopEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final ClipBotTrait bot;

    public ClipBotStopEvent(ClipBotTrait bot) {
        this.bot = bot;
    }

    public ClipBotTrait getBot() {
        return bot;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
