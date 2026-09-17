package net.kyori.adventure.text;
import net.kyori.adventure.text.format.NamedTextColor;
public final class Component {
    private final String text;
    private Component(String text) { this.text = text; }
    public static Component text(String text) { return new Component(text); }
    public static Component text(String text, NamedTextColor color) { return new Component(text); }
    @Override public String toString() { return text; }
}
