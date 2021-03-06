package net.blay09.mods.refinedrelocation.client.gui.base.element;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

public class MultiLineTextFieldWidget extends TextFieldWidget implements IScrollTarget {

    private static final int ENABLED_COLOR = 0xE0E0E0;
    private static final int DISABLED_COLOR = 0x707070;
    private static final int PADDING = 2;

    private final FontRenderer fontRenderer;

    private int scrollOffset;
    private int cursorCounter;

    private String[] renderCache;
    private int lastRowCount;

    public MultiLineTextFieldWidget(int x, int y, int width, int height) {
        super(Minecraft.getInstance().fontRenderer, x, y, width, height, "");
        fontRenderer = Minecraft.getInstance().fontRenderer;
    }

    private int getStartOfLine(int position, int iterations) {
        String text = getText();
        int startOfLine = position;
        for (int i = 0; i < iterations; i++) {
            startOfLine = text.lastIndexOf('\n', startOfLine - 1);
        }
        return startOfLine != -1 ? startOfLine + 1 : 0;
    }

    private int getEndOfLine(int position, int iteration) {
        String text = getText();
        int endOfLine = position - 1;
        for (int i = 0; i < iteration; i++) {
            endOfLine = text.indexOf('\n', endOfLine + 1);
            if (endOfLine == -1) {
                return text.length();
            }
        }
        return endOfLine != -1 ? endOfLine : text.length();
    }

    private int getLineLength(int position) {
        return getEndOfLine(position, 1) - getStartOfLine(position, 1);
    }

    private int getStartOfWord(int position) {
        String text = getText();
        if (text.isEmpty()) {
            return 0;
        }

        position = Math.max(Math.min(position, text.length() - 1), 0);
        if (text.charAt(position) == '\n') {
            return position;
        }
        boolean foundAlphabetic = false;
        for (int i = position; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '\n') {
                return i + 1;
            }
            if (Character.isAlphabetic(c)) {
                foundAlphabetic = true;
            } else if (foundAlphabetic) {
                return i + 1;
            }
        }
        return 0;
    }

    private int getStartOfNextWord(int position) {
        String text = getText();
        position = Math.max(Math.min(position, text.length() - 1), 0);
        if (text.charAt(position) == '\n') {
            return position;
        }
        boolean foundNonAlphabetic = false;
        for (int i = position; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                return i;
            }
            if (!Character.isAlphabetic(c)) {
                foundNonAlphabetic = true;
            } else if (foundNonAlphabetic) {
                return i;
            }
        }
        return text.length();
    }

    public void setCursorPosition(int cursorPosition) {
        String text = getText();
        super.setCursorPosition(Math.min(Math.max(cursorPosition, 0), text.length()));

        int cursorLine = 0;
        for (int i = 0; i < this.getCursorPosition(); i++) {
            if (text.charAt(i) == '\n') {
                cursorLine++;
            }
        }

        int innerHeight = height - PADDING;
        int cursorRenderY = (cursorLine - scrollOffset) * fontRenderer.FONT_HEIGHT + PADDING;
        if (cursorRenderY < 0) {
            scroll(0, cursorRenderY / fontRenderer.FONT_HEIGHT - 1);
        } else if (cursorRenderY > innerHeight - fontRenderer.FONT_HEIGHT) {
            scroll(0, (cursorRenderY - innerHeight) / fontRenderer.FONT_HEIGHT + 1);
        }

        int innerWidth = width - PADDING;
        int startOfLine = getStartOfLine(cursorPosition, 1);
        int endOfLine = getEndOfLine(cursorPosition, 1);
        int cursorPositionX = Math.min(getLineLength(cursorPosition), cursorPosition - startOfLine);
        String lineText = text.substring(startOfLine, endOfLine);
        lineScrollOffset = Math.max(Math.min(lineScrollOffset, lineText.length()), 0);
        String renderText = fontRenderer.trimStringToWidth(lineText, innerWidth);
        if (cursorPositionX == lineScrollOffset) {
            lineScrollOffset -= renderText.length();
        }

        lineScrollOffset = Math.max(Math.min(lineScrollOffset, lineText.length()), 0);
        int offset = renderText.length() + lineScrollOffset;
        if (cursorPositionX > offset) {
            lineScrollOffset += cursorPositionX - offset;
        } else if (cursorPositionX <= lineScrollOffset) {
            lineScrollOffset -= lineScrollOffset - cursorPositionX;
        }

        lineScrollOffset = Math.max(Math.min(lineScrollOffset, lineText.length()), 0);
    }

    public void scroll(int x, int y) {
        lineScrollOffset = Math.max(lineScrollOffset + x, 0);
        scrollOffset = Math.max(scrollOffset + y, 0);
    }

    private void deleteBack(boolean wholeWord) {
        int cursorPosition = getCursorPosition();
        int deleteCount = 1;
        if (wholeWord) {
            deleteCount = cursorPosition - getStartOfWord(cursorPosition);
        }

        if (cursorPosition > 0) {
            String text = getText();
            text = text.substring(0, cursorPosition - deleteCount) + text.substring(cursorPosition);
            setText(text);

            setCursorPosition(cursorPosition - deleteCount);
            renderCache = null;
        }
    }

    private void deleteFront(boolean wholeWord) {
        int cursorPosition = getCursorPosition();
        int deleteCount = 1;
        if (wholeWord) {
            deleteCount = getStartOfNextWord(cursorPosition) - cursorPosition;
        }

        String text = getText();
        if (cursorPosition < text.length()) {
            text = text.substring(0, cursorPosition) + text.substring(cursorPosition + deleteCount);
            setText(text);
            renderCache = null;
        }
    }

    @Override
    public void writeText(String textToWrite) {
        super.writeText(textToWrite);
        renderCache = null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        boolean isInside = mouseX >= x && mouseX < (x + width) && mouseY >= y && mouseY < (y + height);

        if (isFocused() && isInside && mouseButton == 0) {
            double relX = mouseX - x;
            double relY = mouseY - y - fontRenderer.FONT_HEIGHT + 5;
            int lineNumber = Math.round((float) relY / (float) fontRenderer.FONT_HEIGHT) + scrollOffset + 1;
            int startOfLine = getStartOfLine(getEndOfLine(0, lineNumber), 1);
            int endOfLine = getEndOfLine(startOfLine, 1);
            if (startOfLine == endOfLine) {
                setCursorPosition(startOfLine);
            } else {
                String renderText = fontRenderer.trimStringToWidth(getText().substring(Math.max(startOfLine + lineScrollOffset, 0), Math.max(0, endOfLine)), width - PADDING);
                setCursorPosition(startOfLine + fontRenderer.trimStringToWidth(renderText, (int) relX).length() + lineScrollOffset);
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused()) {
            return false;
        }

        int cursorPosition = getCursorPosition();
        switch (keyCode) {
            case GLFW.GLFW_KEY_END:
                if (Screen.hasControlDown()) {
                    setCursorPosition(getText().length());
                } else {
                    setCursorPosition(getEndOfLine(cursorPosition, 1));
                }
                return true;
            case GLFW.GLFW_KEY_HOME:
                if (Screen.hasControlDown()) {
                    setCursorPosition(0);
                } else {
                    setCursorPosition(getStartOfLine(cursorPosition, 1));
                }
                return true;
            case GLFW.GLFW_KEY_LEFT:
                if (Screen.hasControlDown()) {
                    setCursorPosition(getStartOfWord(cursorPosition - 1));
                } else {
                    setCursorPosition(cursorPosition - 1);
                }
                return true;
            case GLFW.GLFW_KEY_RIGHT:
                if (Screen.hasControlDown()) {
                    setCursorPosition(getStartOfNextWord(cursorPosition + 1));
                } else {
                    setCursorPosition(cursorPosition + 1);
                }
                return true;
            case GLFW.GLFW_KEY_UP:
                if (Screen.hasControlDown()) {
                    scroll(0, -1);
                } else {
                    int upLine = getStartOfLine(cursorPosition, 2);
                    setCursorPosition(upLine + Math.min(getLineLength(upLine), (cursorPosition - getStartOfLine(cursorPosition, 1))));
                }
                return true;
            case GLFW.GLFW_KEY_DOWN:
                if (Screen.hasControlDown()) {
                    scroll(0, 1);
                } else {
                    int downLine = getEndOfLine(cursorPosition, 2);
                    setCursorPosition(getStartOfLine(downLine, 1) + Math.min(getLineLength(downLine), (cursorPosition - getStartOfLine(cursorPosition, 1))));
                }
                return true;
            case GLFW.GLFW_KEY_ENTER:
                if (active) {
                    setText(getText() + "\n");
                    setCursorPosition(getCursorPosition() + 1);
                }
                return true;
            case GLFW.GLFW_KEY_DELETE:
                if (active) {
                    deleteFront(Screen.hasControlDown());
                }
                return true;
            case GLFW.GLFW_KEY_BACKSPACE:
                if (active) {
                    deleteBack(Screen.hasControlDown());
                }
                return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        super.tick();

        scrollOffset = Math.max(Math.min(scrollOffset, getRowCount() - getVisibleRows()), 0);

        cursorCounter++;
    }

    @Override
    public void renderButton(int mouseX, int mouseY, float partialTicks) {
        if (getVisible()) {
            fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFFEEEEEE);
            fill(x, y, x + width, y + height, 0xFF000000);

            if (renderCache == null) {
                renderCache = getText().split("\n", -1);
            }

            for (int i = scrollOffset; i < renderCache.length; i++) {
                int y = (i - scrollOffset) * fontRenderer.FONT_HEIGHT;
                if (y + fontRenderer.FONT_HEIGHT >= height) {
                    break;
                }

                if (lineScrollOffset >= renderCache[i].length()) {
                    continue;
                }

                String renderText = fontRenderer.trimStringToWidth(renderCache[i].substring(lineScrollOffset), width - PADDING);
                fontRenderer.drawStringWithShadow(renderText, this.x + PADDING, this.y + PADDING + y, active ? ENABLED_COLOR : DISABLED_COLOR);
            }

            int cursorLine = 0;
            int lastLineIdx = 0;
            for (int i = 0; i < getCursorPosition(); i++) {
                if (getText().charAt(i) == '\n') {
                    cursorLine++;
                    lastLineIdx = i + 1;
                }
            }

            if (cursorCounter / 6 % 2 == 0 && (cursorLine - scrollOffset) * fontRenderer.FONT_HEIGHT >= 0 && (cursorLine - scrollOffset + 1) * fontRenderer.FONT_HEIGHT < height + PADDING) {
                drawCursor(x + fontRenderer.getStringWidth(getText().substring(lastLineIdx + lineScrollOffset, getCursorPosition())) + PADDING, y + (cursorLine - scrollOffset) * fontRenderer.FONT_HEIGHT + PADDING);
            }
        }
    }

    private void drawCursor(int x, int y) {
        Tessellator tessellator = Tessellator.getInstance();
        GlStateManager.color4f(0f, 0f, 1f, 1f);
        GlStateManager.disableTexture();
        GlStateManager.enableColorLogicOp();
        GlStateManager.logicOp(GlStateManager.LogicOp.OR_REVERSE);
        tessellator.getBuffer().begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        tessellator.getBuffer().pos(x, y + fontRenderer.FONT_HEIGHT, 0).endVertex();
        tessellator.getBuffer().pos(x + 1, y + fontRenderer.FONT_HEIGHT, 0).endVertex();
        tessellator.getBuffer().pos(x + 1, y, 0).endVertex();
        tessellator.getBuffer().pos(x, y, 0).endVertex();
        tessellator.draw();
        GlStateManager.disableColorLogicOp();
        GlStateManager.enableTexture();
    }

    public void setText(String text) {
        int cursorPosition = getCursorPosition();
        super.setText(text);
        setCursorPosition(cursorPosition);
        renderCache = null;
    }

    @Override
    public int getVisibleRows() {
        return height / fontRenderer.FONT_HEIGHT;
    }

    @Override
    public int getRowCount() {
        if (renderCache != null) {
            lastRowCount = renderCache.length;
        }
        return lastRowCount;
    }

    @Override
    public int getCurrentOffset() {
        return scrollOffset;
    }

    @Override
    public void setCurrentOffset(int offset) {
        this.scrollOffset = offset;
    }

}
