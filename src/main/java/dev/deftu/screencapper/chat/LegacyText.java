package dev.deftu.screencapper.chat;

//? if <1.21 {
/*import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Formatting;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.Locale;

public final class LegacyText {
    private LegacyText() {
    }

    public static Text convert(Component component) {
        Text text = new LiteralText(content(component));
        text.setStyle(style(component));
        for (Component child : component.children()) text.append(convert(child));
        return text;
    }

    private static String content(Component component) {
        if (component instanceof TextComponent) return ((TextComponent) component).content();
        if (component instanceof TranslatableComponent) {
            TranslatableComponent translatable = (TranslatableComponent) component;
            String translated = I18n.translate(translatable.key());
            if (!translated.equals(translatable.key())) return translated;
            return translatable.fallback() != null ? translatable.fallback() : translatable.key();
        }
        return "";
    }

    private static Style style(Component component) {
        net.kyori.adventure.text.format.Style source = component.style();
        Style style = new Style();

        TextColor color = source.color();
        if (color != null) style.setColor(Formatting.valueOf(NamedTextColor.nearestTo(color).toString().toUpperCase(Locale.ROOT)));

        decoration(source, TextDecoration.BOLD, style::setBold);
        decoration(source, TextDecoration.ITALIC, style::setItalic);
        decoration(source, TextDecoration.UNDERLINED, style::setUnderlined);
        decoration(source, TextDecoration.STRIKETHROUGH, style::setStrikethrough);
        decoration(source, TextDecoration.OBFUSCATED, style::setObfuscated);

        net.kyori.adventure.text.event.ClickEvent click = source.clickEvent();
        if (click != null) {
            ClickEvent.Action action = clickAction(click.action());
            String value = click.payload() instanceof net.kyori.adventure.text.event.ClickEvent.Payload.Text
                ? ((net.kyori.adventure.text.event.ClickEvent.Payload.Text) click.payload()).value()
                : null;
            if (action != null && value != null) style.setClickEvent(new ClickEvent(action, value));
        }

        net.kyori.adventure.text.event.HoverEvent<?> hover = source.hoverEvent();
        if (hover != null && hover.action() == net.kyori.adventure.text.event.HoverEvent.Action.SHOW_TEXT) {
            style.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, convert((Component) hover.value())));
        }
        return style;
    }

    private static ClickEvent.Action clickAction(net.kyori.adventure.text.event.ClickEvent.Action action) {
        switch (action) {
            case OPEN_URL: return ClickEvent.Action.OPEN_URL;
            case OPEN_FILE: return ClickEvent.Action.OPEN_FILE;
            case RUN_COMMAND: return ClickEvent.Action.RUN_COMMAND;
            case SUGGEST_COMMAND: return ClickEvent.Action.SUGGEST_COMMAND;
            default: return null;
        }
    }

    private static void decoration(net.kyori.adventure.text.format.Style source, TextDecoration decoration, java.util.function.Consumer<Boolean> apply) {
        TextDecoration.State state = source.decoration(decoration);
        if (state != TextDecoration.State.NOT_SET) apply.accept(state == TextDecoration.State.TRUE);
    }
}
*///?} else {
public final class LegacyText {
    private LegacyText() {
    }
}
//?}
