package net.minecraft.src;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class GuiChat extends GuiScreen
{
    protected String message;
    private int updateCounter;
    private int cursorPos;
    private int selectionStart;
    private boolean hasSelection;
    private boolean mouseDown;
    private int mouseDownCursor;

    private static final int MAX_LENGTH   = 100;
    private static final int CURSOR_BLINK = 6;
    private static final int TEXT_X       = 4;
    private static final int TEXT_Y_OFF   = 12;
    private static final int COL_TEXT     = 0xe0e0e0;
    private static final int COL_SEL_BG   = 0xff000060;
    private static final int COL_CURSOR   = 0xffe0e0e0;

    private static final String ALLOWED = ChatAllowedCharacters.allowedCharacters;
    private static final String PREFIX  = "> ";

    public GuiChat()
    {
        message       = "";
        updateCounter = 0;
        cursorPos     = 0;
        selectionStart = 0;
        hasSelection  = false;
        mouseDown     = false;
        mouseDownCursor = 0;
    }

    public void initGui()
    {
        Keyboard.enableRepeatEvents(true);
    }

    public void onGuiClosed()
    {
        Keyboard.enableRepeatEvents(false);
    }

    public void updateScreen()
    {
        updateCounter++;
    }

    private int textOriginX() { return TEXT_X + fontRenderer.getStringWidth(PREFIX); }

    private int xToIndex(int screenX)
    {
        int rel = screenX - textOriginX();
        if (rel <= 0) return 0;
        for (int i = 1; i <= message.length(); i++)
        {
            if (fontRenderer.getStringWidth(message.substring(0, i)) >= rel)
                return i;
        }
        return message.length();
    }

    private int indexToX(int index)
    {
        return textOriginX() + fontRenderer.getStringWidth(message.substring(0, index));
    }
    private void deleteSelection()
    {
        if (!hasSelection) return;
        int lo = Math.min(cursorPos, selectionStart);
        int hi = Math.max(cursorPos, selectionStart);
        message   = message.substring(0, lo) + message.substring(hi);
        cursorPos = lo;
        clearSelection();
    }

    private void clearSelection()
    {
        hasSelection   = false;
        selectionStart = cursorPos;
    }

    private void moveCursor(int newPos, boolean selecting)
    {
        int clamped = Math.max(0, Math.min(newPos, message.length()));
        if (selecting)
        {
            if (!hasSelection) { selectionStart = cursorPos; hasSelection = true; }
            cursorPos = clamped;
            if (cursorPos == selectionStart) hasSelection = false;
        }
        else
        {
            cursorPos = clamped;
            clearSelection();
        }
    }

    private String getSelectedText()
    {
        if (!hasSelection) return "";
        int lo = Math.min(cursorPos, selectionStart);
        int hi = Math.max(cursorPos, selectionStart);
        return message.substring(lo, hi);
    }

    private void copyToClipboard(String s)
    {
        try { StringSelection ss = new StringSelection(s);
              Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ss, ss); }
        catch (Exception ignored) {}
    }

    private String getClipboard()
    {
        try
        {
            Transferable t = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (t != null && t.isDataFlavorSupported(DataFlavor.stringFlavor))
                return (String) t.getTransferData(DataFlavor.stringFlavor);
        }
        catch (Exception ignored) {}
        return "";
    }

    private void insertText(String insert)
    {
        if (hasSelection) deleteSelection();
        int remaining = MAX_LENGTH - message.length();
        if (remaining <= 0) return;
        String filtered = filterAllowed(insert);
        if (filtered.length() > remaining) filtered = filtered.substring(0, remaining);
        message    = message.substring(0, cursorPos) + filtered + message.substring(cursorPos);
        cursorPos += filtered.length();
        clearSelection();
    }

    private String filterAllowed(String input)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++)
        {
            char c = input.charAt(i);
            if (ALLOWED.indexOf(c) >= 0) sb.append(c);
        }
        return sb.toString();
    }

    protected void keyTyped(char c, int keyCode)
    {
        boolean ctrl  = Keyboard.isKeyDown(29) || Keyboard.isKeyDown(157);
        boolean shift = Keyboard.isKeyDown(42) || Keyboard.isKeyDown(54);

        switch (keyCode)
        {
            case 1:
                mc.displayGuiScreen(null);
                return;

            case 28:
                String s = message.trim();
                if (s.length() > 0 && !mc.lineIsCommand(s))
                    mc.thePlayer.sendChatMessage(s);
                mc.displayGuiScreen(null);
                return;

            case 203:
                moveCursor(cursorPos - 1, shift);
                return;

            case 205:
                moveCursor(cursorPos + 1, shift);
                return;

            case 199:
                moveCursor(0, shift);
                return;

            case 207:
                moveCursor(message.length(), shift);
                return;

            case 14:
                if (hasSelection) deleteSelection();
                else if (cursorPos > 0)
                {
                    message = message.substring(0, cursorPos - 1) + message.substring(cursorPos);
                    cursorPos--;
                }
                return;

            case 211:
                if (hasSelection) deleteSelection();
                else if (cursorPos < message.length())
                    message = message.substring(0, cursorPos) + message.substring(cursorPos + 1);
                return;
        }

        if (ctrl)
        {
            switch (keyCode)
            {
                case 30:
                    selectionStart = 0; cursorPos = message.length();
                    hasSelection = message.length() > 0;
                    return;
                case 46:
                    if (hasSelection) copyToClipboard(getSelectedText());
                    return;
                case 45:
                    if (hasSelection) { copyToClipboard(getSelectedText()); deleteSelection(); }
                    return;
                case 47:
                    String clip = getClipboard();
                    if (clip != null && clip.length() > 0) insertText(clip);
                    return;
            }
            return;
        }

        if (ALLOWED.indexOf(c) >= 0) insertText(String.valueOf(c));
    }

    public void handleMouseInput()
    {
        super.handleMouseInput();

        if (Mouse.isButtonDown(0))
        {
            int mx = (Mouse.getX() * width)  / mc.displayWidth;
            int my = height - (Mouse.getY() * height) / mc.displayHeight - 1;
            boolean inBox = my >= height - 14 && my <= height - 2
                         && mx >= 2           && mx <= width - 2;

            if (inBox)
            {
                int idx = xToIndex(mx);
                if (!mouseDown)
                {
                    mouseDown       = true;
                    mouseDownCursor = idx;
                    cursorPos       = idx;
                    clearSelection();
                }
                else
                {
                    if (idx != mouseDownCursor)
                    {
                        selectionStart = mouseDownCursor;
                        cursorPos      = idx;
                        hasSelection   = true;
                    }
                    else
                    {
                        cursorPos = idx;
                        clearSelection();
                    }
                }
            }
        }
        else
        {
            mouseDown = false;
        }
    }

    protected void mouseClicked(int x, int y, int button)
    {
        if (button == 0)
        {
            boolean inBox = y >= height - 14 && y <= height - 2
                         && x >= 2           && x <= width - 2;
            if (inBox) return;
        }

        if (mc.ingameGUI.field_933_a != null && button == 0)
        {
            if (message.length() > 0 && !message.endsWith(" ")) message += " ";
            message += mc.ingameGUI.field_933_a;
            if (message.length() > MAX_LENGTH) message = message.substring(0, MAX_LENGTH);
            cursorPos = message.length();
            clearSelection();
            return;
        }

        super.mouseClicked(x, y, button);
    }

    public void drawScreen(int mouseX, int mouseY, float partialTick)
    {
        drawRect(2, height - 14, width - 2, height - 2, 0x80000000);

        int ty = height - TEXT_Y_OFF;

        drawString(fontRenderer, PREFIX, TEXT_X, ty, COL_TEXT);

        int originX = textOriginX();

        if (hasSelection)
        {
            int lo  = Math.min(cursorPos, selectionStart);
            int hi  = Math.max(cursorPos, selectionStart);
            int sx1 = indexToX(lo);
            int sx2 = indexToX(hi);
            drawRect(sx1, ty - 1, sx2, ty + 9, COL_SEL_BG);
            drawString(fontRenderer, message.substring(0, lo),  originX, ty, COL_TEXT);
            drawString(fontRenderer, message.substring(lo, hi), sx1,     ty, COL_TEXT);
            drawString(fontRenderer, message.substring(hi),     sx2,     ty, COL_TEXT);
        }
        else
        {
            boolean blink = (updateCounter / CURSOR_BLINK) % 2 == 0;
            String before = message.substring(0, cursorPos);
            String after  = message.substring(cursorPos);
            drawString(fontRenderer, before + (blink ? "_" : "") + after, originX, ty, COL_TEXT);
        }

        super.drawScreen(mouseX, mouseY, partialTick);
    }
}
