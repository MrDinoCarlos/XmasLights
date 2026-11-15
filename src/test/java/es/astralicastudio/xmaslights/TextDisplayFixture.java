package es.astralicastudio.xmaslights;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.DisplayMock;
import java.util.UUID;

/** Metadata storage missing in MockBukkit 4.35; the matrix additionally exercises real Paper entities. */
final class TextDisplayFixture extends DisplayMock implements TextDisplay {
    private Component text = Component.empty();
    private int width;
    private byte opacity;
    private Color background;
    private boolean shadow, seeThrough, defaultBackground, visible = true;
    private TextAlignment alignment = TextAlignment.CENTER;
    private Billboard billboard = Billboard.FIXED;
    TextDisplayFixture(ServerMock server) { super(server, UUID.randomUUID()); }
    @Override public EntityType getType() { return EntityType.TEXT_DISPLAY; }
    @Override public Component text() { return text; }
    @Override public void text(Component value) { text = value; }
    @Override public String getText() { return text.toString(); }
    @Override public void setText(String value) { text = Component.text(value); }
    @Override public int getLineWidth() { return width; }
    @Override public void setLineWidth(int value) { width = value; }
    @Override public Color getBackgroundColor() { return background; }
    @Override public void setBackgroundColor(Color value) { background = value; }
    @Override public byte getTextOpacity() { return opacity; }
    @Override public void setTextOpacity(byte value) { opacity = value; }
    @Override public boolean isShadowed() { return shadow; }
    @Override public void setShadowed(boolean value) { shadow = value; }
    @Override public boolean isSeeThrough() { return seeThrough; }
    @Override public void setSeeThrough(boolean value) { seeThrough = value; }
    @Override public boolean isDefaultBackground() { return defaultBackground; }
    @Override public void setDefaultBackground(boolean value) { defaultBackground = value; }
    @Override public TextAlignment getAlignment() { return alignment; }
    @Override public void setAlignment(TextAlignment value) { alignment = value; }
    @Override public Billboard getBillboard() { return billboard; }
    @Override public void setBillboard(Billboard value) { billboard = value; }
    @Override public void setVisibleByDefault(boolean value) { visible = value; }
    @Override public boolean isVisibleByDefault() { return visible; }
}
