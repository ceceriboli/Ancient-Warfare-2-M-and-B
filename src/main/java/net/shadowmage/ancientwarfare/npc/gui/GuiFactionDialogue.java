package net.shadowmage.ancientwarfare.npc.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.shadowmage.ancientwarfare.core.network.NetworkHandler;
import net.shadowmage.ancientwarfare.npc.network.PacketFactionDialogueAction;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Dialogue window of a faction npc (a ruling leader): its title, what it says and a column of buttons, as in Minecraft Comes Alive.
 * The pages are built by the server (FactionDialogue) and the buttons send their action back to it. The game is not paused.
 */
@SideOnly(Side.CLIENT)
public class GuiFactionDialogue extends GuiScreen {
	private static final int PANEL_MAX_WIDTH = 300;
	private static final int PADDING = 8;
	private static final int LINE_HEIGHT = 10;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_SPACING = 22;
	private static final int PANEL_COLOR = 0xD0101010;
	private static final int BORDER_COLOR = 0xFFB08A3E;

	private final int entityId;
	private final ITextComponent title;
	private final ITextComponent text;
	private final List<Entry> entries = new ArrayList<>();
	private List<String> textLines = Collections.emptyList();
	private int panelLeft;
	private int panelTop;
	private int panelWidth;
	private int panelHeight;

	private GuiFactionDialogue(NBTTagCompound tag) {
		entityId = tag.getInteger("entity");
		title = ITextComponent.Serializer.jsonToComponent(tag.getString("title"));
		text = ITextComponent.Serializer.jsonToComponent(tag.getString("text"));
		NBTTagList list = tag.getTagList("buttons", Constants.NBT.TAG_COMPOUND);
		for (int i = 0; i < list.tagCount(); i++) {
			NBTTagCompound buttonTag = list.getCompoundTagAt(i);
			entries.add(new Entry(ITextComponent.Serializer.jsonToComponent(buttonTag.getString("label")), buttonTag.getString("action"),
					buttonTag.getBoolean("enabled"), buttonTag.hasKey("hover") ? ITextComponent.Serializer.jsonToComponent(buttonTag.getString("hover")) : null));
		}
	}

	/**
	 * Shows the page sent by the server, or closes the window.
	 */
	public static void show(NBTTagCompound tag) {
		Minecraft mc = Minecraft.getMinecraft();
		if (tag.getBoolean("close")) {
			if (mc.currentScreen instanceof GuiFactionDialogue) {
				mc.displayGuiScreen(null);
			}
			return;
		}
		mc.displayGuiScreen(new GuiFactionDialogue(tag));
	}

	@Override
	public void initGui() {
		buttonList.clear();
		panelWidth = Math.min(PANEL_MAX_WIDTH, width - 2 * PADDING);
		int innerWidth = panelWidth - 2 * PADDING;
		textLines = text == null ? Collections.emptyList() : fontRenderer.listFormattedStringToWidth(text.getFormattedText(), innerWidth);
		panelHeight = PADDING + LINE_HEIGHT + 6 + textLines.size() * LINE_HEIGHT + PADDING + entries.size() * BUTTON_SPACING + PADDING - 2;
		panelLeft = (width - panelWidth) / 2;
		panelTop = Math.max(PADDING, (height - panelHeight) / 2);
		int y = panelTop + PADDING + LINE_HEIGHT + 6 + textLines.size() * LINE_HEIGHT + PADDING;
		for (int i = 0; i < entries.size(); i++) {
			Entry entry = entries.get(i);
			GuiButton button = new GuiButton(i, panelLeft + PADDING, y, innerWidth, BUTTON_HEIGHT, entry.label.getFormattedText());
			button.enabled = entry.enabled;
			buttonList.add(button);
			y += BUTTON_SPACING;
		}
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		drawRect(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, PANEL_COLOR);
		drawHorizontalLine(panelLeft, panelLeft + panelWidth - 1, panelTop, BORDER_COLOR);
		drawHorizontalLine(panelLeft, panelLeft + panelWidth - 1, panelTop + panelHeight - 1, BORDER_COLOR);
		drawVerticalLine(panelLeft, panelTop, panelTop + panelHeight - 1, BORDER_COLOR);
		drawVerticalLine(panelLeft + panelWidth - 1, panelTop, panelTop + panelHeight - 1, BORDER_COLOR);
		if (title != null) {
			drawCenteredString(fontRenderer, title.getFormattedText(), width / 2, panelTop + PADDING, 0xFFD700);
		}
		int y = panelTop + PADDING + LINE_HEIGHT + 6;
		for (String line : textLines) {
			fontRenderer.drawString(line, panelLeft + PADDING, y, 0xFFFFFF);
			y += LINE_HEIGHT;
		}
		super.drawScreen(mouseX, mouseY, partialTicks);
		for (GuiButton button : buttonList) {
			Entry entry = entries.get(button.id);
			if (entry.hover != null && mouseX >= button.x && mouseY >= button.y && mouseX < button.x + button.width && mouseY < button.y + button.height) {
				drawHoveringText(fontRenderer.listFormattedStringToWidth(entry.hover.getFormattedText(), 200), mouseX, mouseY);
			}
		}
	}

	@Override
	protected void actionPerformed(GuiButton button) {
		Entry entry = entries.get(button.id);
		if ("close".equals(entry.action)) {
			mc.displayGuiScreen(null);
			return;
		}
		// the answer of the server replaces this page; no double clicks meanwhile
		for (GuiButton other : buttonList) {
			other.enabled = false;
		}
		NetworkHandler.sendToServer(new PacketFactionDialogueAction(entityId, entry.action));
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}

	private static class Entry {
		private final ITextComponent label;
		private final String action;
		private final boolean enabled;
		@Nullable
		private final ITextComponent hover;

		private Entry(ITextComponent label, String action, boolean enabled, @Nullable ITextComponent hover) {
			this.label = label;
			this.action = action;
			this.enabled = enabled;
			this.hover = hover;
		}
	}
}
