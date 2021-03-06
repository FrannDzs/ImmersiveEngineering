/*
 * BluSunrize
 * Copyright (c) 2020
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */
package blusunrize.immersiveengineering.common.util.compat.top;

import blusunrize.immersiveengineering.ImmersiveEngineering;
import blusunrize.immersiveengineering.api.IEEnums.IOSideConfig;
import blusunrize.immersiveengineering.api.Lib;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ProbeMode;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

/**
 * @author Robustprogram - 26.1.2020
 */
public class SideConfigProvider implements IProbeInfoProvider
{

	@Override
	public String getID()
	{
		return ImmersiveEngineering.MODID+":"+"SideConfigInfo";
	}

	@Override
	public void addProbeInfo(ProbeMode mode, IProbeInfo probeInfo, PlayerEntity player, World world,
		BlockState blockState, IProbeHitData data)
	{
		TileEntity te = world.getTileEntity(data.getPos());
		if(te instanceof IEBlockInterfaces.IConfigurableSides&&data.getSideHit()!=null)
		{
			boolean flip = player.isSneaking();
			Direction side = flip ? data.getSideHit().getOpposite() : data.getSideHit();
			IOSideConfig config = ((IEBlockInterfaces.IConfigurableSides)te).getSideConfig(side);
			
			StringTextComponent combined = new StringTextComponent("");
			TranslationTextComponent direction =
					new TranslationTextComponent(Lib.DESC_INFO+"blockSide." + (flip?"opposite": "facing"));
			TranslationTextComponent connection = 
					new TranslationTextComponent(Lib.DESC_INFO+"blockSide.io." + config.getString());
			
			combined.appendSibling(direction);
			combined.appendString(": ");
			combined.appendSibling(connection);
			
			probeInfo.text(combined);
		}
	}
}
