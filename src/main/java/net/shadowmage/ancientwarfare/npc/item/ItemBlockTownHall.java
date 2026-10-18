package net.shadowmage.ancientwarfare.npc.item;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.shadowmage.ancientwarfare.core.item.ItemBlockOwned;
import net.shadowmage.ancientwarfare.npc.faction.Kingdoms;

/**
 * The town hall: the first one a player places founds their kingdom.
 */
public class ItemBlockTownHall extends ItemBlockOwned {
	public ItemBlockTownHall(Block block) {
		super(block);
	}

	@Override
	public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, IBlockState newState) {
		boolean placed = super.placeBlockAt(stack, player, world, pos, side, hitX, hitY, hitZ, newState);
		if (placed && !world.isRemote) {
			Kingdoms.onTownHallPlaced(world, player, pos);
		}
		return placed;
	}
}
