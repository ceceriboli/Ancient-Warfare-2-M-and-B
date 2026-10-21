package net.shadowmage.ancientwarfare.npc.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.shadowmage.ancientwarfare.core.network.NetworkHandler;
import net.shadowmage.ancientwarfare.npc.network.PacketMarketAction;
import net.shadowmage.ancientwarfare.npc.war.Markets;
import org.lwjgl.input.Mouse;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The market window of a city, in the manner of Mount & Blade: on the left the player's goods with the price the city pays, on the right
 * the city's goods with the price the player pays. A click moves one unit into the deal (shift: ten), a right click takes it back; the bar
 * at the bottom shows the balance. The prices are those of the server for each unit in turn; the server computes the deal again on Confirm.
 */
@SideOnly(Side.CLIENT)
public class GuiMarket extends GuiScreen {
	private static final int PANEL_MAX_WIDTH = 440;
	private static final int PANEL_MAX_HEIGHT = 280;
	private static final int PADDING = 6;
	private static final int ROW_HEIGHT = 18;
	private static final int HEADER_HEIGHT = 24;
	private static final int FOOTER_HEIGHT = 58;
	private static final int PANEL_COLOR = 0xE0101010;
	private static final int BORDER_COLOR = 0xFFB08A3E;
	private static final int ROW_COLOR = 0x40FFFFFF;
	private static final int ROW_HOVER_COLOR = 0x60FFFFFF;
	private static final int ID_CONFIRM = 0;
	private static final int ID_RESET = 1;
	private static final int ID_CLOSE = 2;
	private static final int ID_OLD_TRADES = 3;
	// kept when the server sends the window again after a deal
	private static int playerScroll = 0;
	private static int marketScroll = 0;

	private final ITextComponent title;
	@Nullable
	private final ITextComponent message;
	private final int playerCopper;
	private final int cityCopper;
	private final boolean oldTrades;
	private final List<Row> playerRows = new ArrayList<>();
	private final List<Row> marketRows = new ArrayList<>();
	private boolean replaced = false;
	private int left;
	private int top;
	private int panelWidth;
	private int panelHeight;
	private int columnWidth;
	private int listTop;
	private int visibleRows;

	private GuiMarket(NBTTagCompound tag) {
		title = ITextComponent.Serializer.jsonToComponent(tag.getString("title"));
		message = tag.hasKey("message") ? ITextComponent.Serializer.jsonToComponent(tag.getString("message")) : null;
		playerCopper = tag.getInteger("playerCopper");
		cityCopper = tag.getInteger("cityCopper");
		oldTrades = tag.getBoolean("oldTrades");
		readRows(tag.getTagList("player", Constants.NBT.TAG_COMPOUND), playerRows);
		readRows(tag.getTagList("market", Constants.NBT.TAG_COMPOUND), marketRows);
	}

	private static void readRows(NBTTagList list, List<Row> rows) {
		for (int i = 0; i < list.tagCount(); i++) {
			NBTTagCompound tag = list.getCompoundTagAt(i);
			rows.add(new Row(tag.getString("id"), new ItemStack(tag.getCompoundTag("stack")), tag.getInteger("stock"), tag.getIntArray("prices"),
					tag.getFloat("factor"), tag.getBoolean("special")));
		}
	}

	/**
	 * Shows the market sent by the server, or closes the window.
	 */
	public static void show(NBTTagCompound tag) {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.currentScreen instanceof GuiMarket) {
			((GuiMarket) mc.currentScreen).replaced = true;
		} else {
			playerScroll = 0;
			marketScroll = 0;
		}
		if (tag.getBoolean("close")) {
			if (mc.currentScreen instanceof GuiMarket) {
				mc.displayGuiScreen(null);
			}
			return;
		}
		mc.displayGuiScreen(new GuiMarket(tag));
	}

	@Override
	public void initGui() {
		buttonList.clear();
		panelWidth = Math.min(PANEL_MAX_WIDTH, width - 2 * PADDING);
		panelHeight = Math.min(PANEL_MAX_HEIGHT, height - 2 * PADDING);
		left = (width - panelWidth) / 2;
		top = (height - panelHeight) / 2;
		columnWidth = (panelWidth - 3 * PADDING) / 2;
		listTop = top + HEADER_HEIGHT + 12;
		visibleRows = Math.max(1, (top + panelHeight - FOOTER_HEIGHT - listTop) / ROW_HEIGHT);
		playerScroll = clampScroll(playerScroll, playerRows.size());
		marketScroll = clampScroll(marketScroll, marketRows.size());
		int buttonY = top + panelHeight - PADDING - 20;
		int buttons = oldTrades ? 4 : 3;
		int buttonWidth = (panelWidth - 2 * PADDING - (buttons - 1) * 4) / buttons;
		int x = left + PADDING;
		buttonList.add(new GuiButton(ID_CONFIRM, x, buttonY, buttonWidth, 20, I18n.format("gui.aw.market.confirm")));
		x += buttonWidth + 4;
		buttonList.add(new GuiButton(ID_RESET, x, buttonY, buttonWidth, 20, I18n.format("gui.aw.market.reset")));
		x += buttonWidth + 4;
		if (oldTrades) {
			buttonList.add(new GuiButton(ID_OLD_TRADES, x, buttonY, buttonWidth, 20, I18n.format("gui.aw.market.old_trades")));
			x += buttonWidth + 4;
		}
		buttonList.add(new GuiButton(ID_CLOSE, x, buttonY, buttonWidth, 20, I18n.format("gui.aw.market.close")));
		updateConfirm();
	}

	private int clampScroll(int scroll, int rows) {
		return Math.max(0, Math.min(scroll, rows - visibleRows));
	}

	private void updateConfirm() {
		for (GuiButton button : buttonList) {
			if (button.id == ID_CONFIRM) {
				button.enabled = hasDeal() && (getBalance() <= 0 ? -getBalance() <= cityCopper : getBalance() <= playerCopper);
			}
		}
	}

	private boolean hasDeal() {
		for (Row row : playerRows) {
			if (row.quantity > 0) {
				return true;
			}
		}
		for (Row row : marketRows) {
			if (row.quantity > 0) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @return what the player pays (positive) or receives (negative), in copper coins
	 */
	private int getBalance() {
		return sum(marketRows) - sum(playerRows);
	}

	private static int sum(List<Row> rows) {
		int total = 0;
		for (Row row : rows) {
			total += row.getTotal();
		}
		return total;
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		drawRect(left, top, left + panelWidth, top + panelHeight, PANEL_COLOR);
		drawHorizontalLine(left, left + panelWidth - 1, top, BORDER_COLOR);
		drawHorizontalLine(left, left + panelWidth - 1, top + panelHeight - 1, BORDER_COLOR);
		drawVerticalLine(left, top, top + panelHeight - 1, BORDER_COLOR);
		drawVerticalLine(left + panelWidth - 1, top, top + panelHeight - 1, BORDER_COLOR);
		if (title != null) {
			drawCenteredString(fontRenderer, title.getFormattedText(), width / 2, top + PADDING, 0xFFD700);
		}
		int playerX = left + PADDING;
		int marketX = left + 2 * PADDING + columnWidth;
		fontRenderer.drawString(I18n.format("gui.aw.market.your_goods"), playerX, top + HEADER_HEIGHT, 0xC0C0C0);
		fontRenderer.drawString(I18n.format("gui.aw.market.city_goods"), marketX, top + HEADER_HEIGHT, 0xC0C0C0);
		Row hovered = null;
		Row hoveredPlayer = drawColumn(playerRows, playerScroll, playerX, mouseX, mouseY, true);
		Row hoveredMarket = drawColumn(marketRows, marketScroll, marketX, mouseX, mouseY, false);
		if (hoveredPlayer != null) {
			hovered = hoveredPlayer;
		} else if (hoveredMarket != null) {
			hovered = hoveredMarket;
		}
		int footerY = top + panelHeight - FOOTER_HEIGHT + 4;
		int balance = getBalance();
		String deal = balance > 0 ? I18n.format("gui.aw.market.balance_pay", Markets.formatMoney(balance))
				: balance < 0 ? I18n.format("gui.aw.market.balance_receive", Markets.formatMoney(-balance)) : I18n.format("gui.aw.market.balance_none");
		int dealColor = balance > playerCopper || -balance > cityCopper ? 0xFF5555 : 0xFFFFFF;
		fontRenderer.drawString(deal, left + PADDING, footerY, dealColor);
		fontRenderer.drawString(I18n.format("gui.aw.market.coins", Markets.formatMoney(playerCopper), Markets.formatMoney(cityCopper)), left + PADDING,
				footerY + 11, 0xC0C0C0);
		if (message != null) {
			fontRenderer.drawString(fontRenderer.trimStringToWidth(message.getFormattedText(), panelWidth - 2 * PADDING), left + PADDING, footerY + 22, 0xFFFF55);
		}
		super.drawScreen(mouseX, mouseY, partialTicks);
		if (hovered != null) {
			List<String> lines = new ArrayList<>(getItemToolTip(hovered.stack));
			lines.add(TextFormatting.GRAY + I18n.format(hovered == hoveredPlayer ? "gui.aw.market.tip_sell" : "gui.aw.market.tip_buy"));
			if (hovered.special) {
				lines.add(TextFormatting.GOLD + I18n.format("gui.aw.market.tip_special"));
			}
			drawHoveringText(lines, mouseX, mouseY);
		}
	}

	/**
	 * @return the row under the mouse, if any
	 */
	@Nullable
	private Row drawColumn(List<Row> rows, int scroll, int x, int mouseX, int mouseY, boolean playerSide) {
		Row hovered = null;
		if (rows.isEmpty()) {
			fontRenderer.drawString(TextFormatting.ITALIC + I18n.format(playerSide ? "gui.aw.market.nothing_to_sell" : "gui.aw.market.nothing_to_buy"), x + 2,
					listTop + 4, 0x808080);
			return null;
		}
		for (int i = 0; i < visibleRows && scroll + i < rows.size(); i++) {
			Row row = rows.get(scroll + i);
			int y = listTop + i * ROW_HEIGHT;
			boolean over = mouseX >= x && mouseX < x + columnWidth && mouseY >= y && mouseY < y + ROW_HEIGHT - 1;
			if (over) {
				hovered = row;
			}
			drawRect(x, y, x + columnWidth, y + ROW_HEIGHT - 1, over ? ROW_HOVER_COLOR : ROW_COLOR);
			RenderHelper.enableGUIStandardItemLighting();
			itemRender.renderItemAndEffectIntoGUI(row.stack, x + 1, y);
			RenderHelper.disableStandardItemLighting();
			String price = row.prices.length > row.quantity ? Markets.formatMoney(row.prices[row.quantity]) : "-";
			int priceColor = getPriceColor(row, playerSide);
			String count = playerSide ? "x" + (row.stock - row.quantity) : "" + (row.stock - row.quantity);
			String quantity = row.quantity > 0 ? (playerSide ? "-" : "+") + row.quantity : "";
			int rightX = x + columnWidth - 2;
			int priceWidth = fontRenderer.getStringWidth(price);
			fontRenderer.drawString(price, rightX - priceWidth, y + 5, priceColor);
			int countX = rightX - priceWidth - 6 - fontRenderer.getStringWidth(count);
			fontRenderer.drawString(count, countX, y + 5, 0xA0A0A0);
			int nameX = x + 20;
			int nameWidth = countX - nameX - 4;
			if (!quantity.isEmpty()) {
				int quantityWidth = fontRenderer.getStringWidth(quantity);
				fontRenderer.drawString(quantity, countX - quantityWidth - 4, y + 5, 0xFFFF55);
				nameWidth -= quantityWidth + 4;
			}
			String name = (row.special ? TextFormatting.GOLD : "") + row.stack.getDisplayName();
			fontRenderer.drawString(fontRenderer.trimStringToWidth(name, Math.max(10, nameWidth)), nameX, y + 5, 0xFFFFFF);
		}
		if (rows.size() > visibleRows) {
			int barHeight = visibleRows * ROW_HEIGHT;
			int knob = Math.max(8, barHeight * visibleRows / rows.size());
			int knobY = listTop + (barHeight - knob) * scroll / Math.max(1, rows.size() - visibleRows);
			drawRect(x + columnWidth + 1, knobY, x + columnWidth + 3, knobY + knob, 0xFFB08A3E);
		}
		return hovered;
	}

	/**
	 * Green: a good price for the player (cheap to buy here, or the city pays well); red: a bad one.
	 */
	private static int getPriceColor(Row row, boolean playerSide) {
		if (row.special) {
			return 0xFFAA00;
		}
		boolean dear = row.factor > 1.15F;
		boolean cheap = row.factor < 0.9F;
		if (playerSide) {
			return dear ? 0x55FF55 : cheap ? 0xFF7777 : 0xFFFFFF;
		}
		return cheap ? 0x55FF55 : dear ? 0xFF7777 : 0xFFFFFF;
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
		super.mouseClicked(mouseX, mouseY, mouseButton);
		int playerX = left + PADDING;
		int marketX = left + 2 * PADDING + columnWidth;
		Row row = getRowAt(playerRows, playerScroll, playerX, mouseX, mouseY);
		if (row == null) {
			row = getRowAt(marketRows, marketScroll, marketX, mouseX, mouseY);
		}
		if (row == null) {
			return;
		}
		int step = isShiftKeyDown() ? 10 : 1;
		if (mouseButton == 0) {
			row.quantity = Math.min(row.prices.length, Math.min(row.stock, row.quantity + step));
		} else if (mouseButton == 1) {
			row.quantity = Math.max(0, row.quantity - step);
		}
		updateConfirm();
	}

	@Nullable
	private Row getRowAt(List<Row> rows, int scroll, int x, int mouseX, int mouseY) {
		if (mouseX < x || mouseX >= x + columnWidth || mouseY < listTop) {
			return null;
		}
		int index = (mouseY - listTop) / ROW_HEIGHT;
		if (index >= visibleRows || scroll + index >= rows.size()) {
			return null;
		}
		return rows.get(scroll + index);
	}

	@Override
	public void handleMouseInput() throws IOException {
		super.handleMouseInput();
		int wheel = Mouse.getEventDWheel();
		if (wheel == 0) {
			return;
		}
		int mouseX = Mouse.getEventX() * width / mc.displayWidth;
		int step = wheel > 0 ? -1 : 1;
		if (mouseX < left + PADDING + columnWidth + PADDING / 2) {
			playerScroll = clampScroll(playerScroll + step, playerRows.size());
		} else {
			marketScroll = clampScroll(marketScroll + step, marketRows.size());
		}
	}

	@Override
	protected void actionPerformed(GuiButton button) {
		switch (button.id) {
			case ID_CONFIRM:
				NBTTagCompound tag = new NBTTagCompound();
				tag.setString("action", "confirm");
				NBTTagList deal = new NBTTagList();
				addDeal(deal, playerRows);
				addDeal(deal, marketRows);
				tag.setTag("deal", deal);
				for (GuiButton other : buttonList) {
					other.enabled = false;
				}
				NetworkHandler.sendToServer(new PacketMarketAction(tag));
				break;
			case ID_RESET:
				for (Row row : playerRows) {
					row.quantity = 0;
				}
				for (Row row : marketRows) {
					row.quantity = 0;
				}
				updateConfirm();
				break;
			case ID_OLD_TRADES:
				replaced = true;
				NetworkHandler.sendToServer(new PacketMarketAction(action("oldtrades")));
				break;
			default:
				mc.displayGuiScreen(null);
		}
	}

	private static NBTTagCompound action(String name) {
		NBTTagCompound tag = new NBTTagCompound();
		tag.setString("action", name);
		return tag;
	}

	private static void addDeal(NBTTagList deal, List<Row> rows) {
		for (Row row : rows) {
			if (row.quantity > 0) {
				NBTTagCompound line = new NBTTagCompound();
				line.setString("id", row.id);
				line.setInteger("qty", row.quantity);
				deal.appendTag(line);
			}
		}
	}

	@Override
	public void onGuiClosed() {
		if (!replaced) {
			NetworkHandler.sendToServer(new PacketMarketAction(action("close")));
		}
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}

	private static class Row {
		private final String id;
		private final ItemStack stack;
		private final int stock;
		private final int[] prices;
		private final float factor;
		private final boolean special;
		private int quantity = 0;

		private Row(String id, ItemStack stack, int stock, int[] prices, float factor, boolean special) {
			this.id = id;
			this.stack = stack;
			this.stock = stock;
			this.prices = prices;
			this.factor = factor;
			this.special = special;
		}

		private int getTotal() {
			int total = 0;
			for (int i = 0; i < quantity && i < prices.length; i++) {
				total += prices[i];
			}
			return total;
		}
	}
}
