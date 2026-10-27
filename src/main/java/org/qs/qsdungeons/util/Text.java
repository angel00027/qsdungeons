package org.qs.qsdungeons.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Utilidades de texto basadas en MiniMessage.
 */
public final class Text {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private Text() {
    }

    public static Component parse(String input) {
        return MINI_MESSAGE.deserialize(input == null ? "" : input);
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static String plain(String miniMessage) {
        return plain(parse(miniMessage));
    }
}
