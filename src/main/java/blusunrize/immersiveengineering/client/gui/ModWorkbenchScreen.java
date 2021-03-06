/*
 * BluSunrize
 * Copyright (c) 2017
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering.client.gui;

import blusunrize.immersiveengineering.ImmersiveEngineering;
import blusunrize.immersiveengineering.api.client.TextUtils;
import blusunrize.immersiveengineering.api.crafting.BlueprintCraftingRecipe;
import blusunrize.immersiveengineering.api.crafting.IngredientWithSize;
import blusunrize.immersiveengineering.client.ClientUtils;
import blusunrize.immersiveengineering.client.utils.GuiHelper;
import blusunrize.immersiveengineering.common.blocks.wooden.ModWorkbenchTileEntity;
import blusunrize.immersiveengineering.common.gui.IESlot;
import blusunrize.immersiveengineering.common.gui.ModWorkbenchContainer;
import blusunrize.immersiveengineering.common.network.MessageTileSync;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.client.gui.GuiUtils;
import net.minecraftforge.items.ItemHandlerHelper;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

import static blusunrize.immersiveengineering.client.ClientUtils.mc;

public class ModWorkbenchScreen extends ToolModificationScreen<ModWorkbenchContainer>
{
	private static final ResourceLocation TEXTURE = makeTextureLocation("workbench");

	private final ModWorkbenchTileEntity workbench;

	public ModWorkbenchScreen(ModWorkbenchContainer container, PlayerInventory inventoryPlayer, ITextComponent title)
	{
		super(container, inventoryPlayer, title);
		workbench = container.tile;
		this.ySize = 168;
	}

	@Override
	protected void sendMessage(CompoundNBT data)
	{
		ImmersiveEngineering.packetHandler.sendToServer(new MessageTileSync(this.workbench, data));
	}

	@Override
	public void render(MatrixStack transform, int mx, int my, float partial)
	{
		super.render(transform, mx, my, partial);
		for(int i = 0; i < container.slotCount; i++)
		{
			Slot s = container.getSlot(i);
			if(s instanceof IESlot.BlueprintOutput&&!s.getHasStack())
			{
				BlueprintCraftingRecipe recipe = ((IESlot.BlueprintOutput)s).recipe;
				if(recipe!=null&&!recipe.output.isEmpty())
					if(isPointInRegion(s.xPos, s.yPos, 16, 16, mx, my))
					{
						List<ITextComponent> tooltip = new ArrayList<>();
						tooltip.add(TextUtils.applyFormat(
								recipe.output.getDisplayName().deepCopy(),
								recipe.output.getRarity().color
						));
						List<ItemStack> inputs = new ArrayList<>();
						for(IngredientWithSize stack : recipe.inputs)
						{
							ItemStack toAdd = ItemHandlerHelper.copyStackWithSize(stack.getRandomizedExampleStack(mc().player.ticksExisted), stack.getCount());
							if(toAdd.isEmpty())
								continue;
							boolean isNew = true;
							for(ItemStack ss : inputs)
								if(ItemHandlerHelper.canItemStacksStack(ss, toAdd))
								{
									ss.grow(toAdd.getCount());
									isNew = false;
									break;
								}
							if(isNew)
								inputs.add(toAdd.copy());
						}
						for(ItemStack ss : inputs)
							tooltip.add(TextUtils.applyFormat(
									new StringTextComponent(ss.getCount()+"x ").appendSibling(ss.getDisplayName()),
									TextFormatting.GRAY
							));

						GuiUtils.drawHoveringText(transform, tooltip, mx, my, width, height, -1, font);
						RenderHelper.enableStandardItemLighting();
					}
			}
		}
	}


	@Override
	protected void drawGuiContainerBackgroundLayer(MatrixStack transform, float f, int mx, int my)
	{
		ClientUtils.bindTexture(TEXTURE);
		this.blit(transform, guiLeft, guiTop, 0, 0, xSize, ySize);


		for(int i = 0; i < container.slotCount; i++)
		{
			Slot s = container.getSlot(i);
			GuiHelper.drawSlot(
					transform, guiLeft+s.xPos, guiTop+s.yPos, 16, 16, 0x77222222, 0x77444444, 0x77999999
			);
		}

		ItemRenderer itemRender = mc().getItemRenderer();
		for(int i = 0; i < container.slotCount; i++)
		{
			Slot s = container.getSlot(i);
			if(s instanceof IESlot.BlueprintOutput&&!s.getHasStack())
			{
				ItemStack ghostStack = ((IESlot.BlueprintOutput)s).recipe.output;
				if(!ghostStack.isEmpty())
				{
					itemRender.renderItemAndEffectIntoGUI(ghostStack, guiLeft+s.xPos, guiTop+s.yPos);
					RenderSystem.depthFunc(GL11.GL_GREATER);
					fill(transform, guiLeft+s.xPos, guiTop+s.yPos, guiLeft+s.xPos+16, guiTop+s.yPos+16, 0xbb333333);
					RenderSystem.depthFunc(GL11.GL_LEQUAL);
				}
			}
		}
	}
}