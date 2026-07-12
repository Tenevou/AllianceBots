package ru.alliancemc.alliancebots.message;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public final class MessageService {
    private final String prefix;

    public MessageService(String prefix) {
        this.prefix = color(prefix == null ? "" : prefix);
    }

    public void send(CommandSender sender, String message) {
        sender.sendMessage(prefix + color(message));
    }

    public String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value);
    }
}
