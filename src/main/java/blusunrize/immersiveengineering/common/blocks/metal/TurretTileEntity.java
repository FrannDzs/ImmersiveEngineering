/*
 * BluSunrize
 * Copyright (c) 2017
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering.common.blocks.metal;

import blusunrize.immersiveengineering.api.ApiUtils;
import blusunrize.immersiveengineering.api.IEEnums.SideConfig;
import blusunrize.immersiveengineering.api.Lib;
import blusunrize.immersiveengineering.api.energy.immersiveflux.FluxStorage;
import blusunrize.immersiveengineering.common.Config.IEConfig;
import blusunrize.immersiveengineering.common.blocks.IEBaseTileEntity;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces.*;
import blusunrize.immersiveengineering.common.util.ChatUtils;
import blusunrize.immersiveengineering.common.util.EnergyHelper.IEForgeEnergyWrapper;
import blusunrize.immersiveengineering.common.util.EnergyHelper.IIEInternalFluxHandler;
import blusunrize.immersiveengineering.common.util.Utils;
import blusunrize.immersiveengineering.common.util.inventory.IIEInventory;
import com.google.common.collect.Lists;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.IEntityOwnable;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.util.ITickable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public abstract class TurretTileEntity extends IEBaseTileEntity implements ITickable, IIEInternalFluxHandler, IIEInventory,
		IHasDummyBlocks, ITileDrop, IDirectionalTile, IBlockBounds, IInteractionObjectIE, IEntityProof, IHammerInteraction, IHasObjProperty
{
	public boolean dummy = false;
	public FluxStorage energyStorage = new FluxStorage(16000);
	public boolean redstoneControlInverted = false;
	public Direction facing = Direction.NORTH;

	public String owner;
	public List<String> targetList = new ArrayList<>();
	public boolean whitelist = false;
	public boolean attackAnimals = false;
	public boolean attackPlayers = false;
	public boolean attackNeutrals = false;

	protected int tick = 0;
	protected LivingEntity target;
	public float rotationYaw;
	public float rotationPitch;

	private UUID targetId;

	public TurretTileEntity(TileEntityType<? extends TurretTileEntity> type)
	{
		super(type);
	}

	@Override
	public void tick()
	{
		ApiUtils.checkForNeedlessTicking(this);
		if(dummy)
			return;
		double range = getRange();
		if(targetId!=null)
		{
			AxisAlignedBB validBox = VoxelShapes.fullCube().getBoundingBox().offset(pos).grow(range);
			List<LivingEntity> entities = world.getEntitiesWithinAABB(LivingEntity.class, validBox);
			for(LivingEntity entity : entities)
				if(entity.getUniqueID().equals(targetId)&&isValidTarget(entity, true))
				{
					target = entity;
					break;
				}
			targetId = null;
		}

		if(target!=null)
		{
			Vec3d delta = getGunToTargetVec(target);
			double dSq = delta.lengthSquared();
			if(dSq > range*range)
				this.target = null;
			else if(world.isRemote)
			{
				float facingYaw = facing==Direction.NORTH?180: facing==Direction.WEST?-90: facing==Direction.EAST?90: 0;
				double yaw = (MathHelper.atan2(delta.x, delta.z)*(180/Math.PI))-facingYaw;
				this.rotationPitch = (float)(Math.atan2(Math.sqrt(delta.x*delta.x+delta.z*delta.z), delta.y)*(180/Math.PI))-90;
				if(this.rotationYaw==0)//moving from default
					this.rotationYaw = (float)(yaw*.5);
				else
					this.rotationYaw = (float)yaw;
			}
		}
		else if(world.isRemote)
		{
			this.rotationYaw *= .75;
			if(Math.abs(rotationYaw) < 10)
				this.rotationYaw = 0;
			this.rotationPitch *= .75;
			if(Math.abs(rotationPitch) < 10)
				this.rotationPitch = 0;
		}


		if(world.isRemote)
			return;
		if(world.getGameTime()%64==((getPos().getX()^getPos().getZ())&63))
			markContainingBlockForUpdate(null);

		int energy = IEConfig.Machines.turret_consumption;
		if(world.getRedstonePowerFromNeighbors(getPos()) > 0^redstoneControlInverted)
		{
			if(energyStorage.extractEnergy(energy, true)==energy)
			{
				energyStorage.extractEnergy(energy, false);
				if(target==null||!target.isAlive()||world.getEntityByID(target.getEntityId())==null||target.getHealth() <= 0||!canShootEntity(target))
				{
					target = getTarget();
					if(target!=null)
					{
						this.markDirty();
						markContainingBlockForUpdate(null);
					}
				}

				//has target, Redstone control check and has power+ammo
				if(target!=null&&canActivate())
				{
					tick++;
					int chargeup = getChargeupTicks();
					if(tick==chargeup)
						this.activate();
					else if(tick > chargeup)
					{
						if(loopActivation())
							this.activate();
						else if(tick==chargeup+getActiveTicks())
							tick = 0;
					}
				}
				else
					tick = 0;
			}
		}
		else if(target!=null)
			target = null;
	}

	private boolean canShootEntity(LivingEntity entity)
	{
		Vec3d start = getGunPosition();
		Vec3d end = getTargetVector(entity);
		//Don't shoot through walls
		if(Utils.rayTraceForFirst(start, end, world, Collections.singleton(getPos().up()))
				!=null)
			return false;
		//Don't shoot non-targeted entities between the turret and the target
		AxisAlignedBB potentialCollateralArea = entity.getBoundingBox().union(new AxisAlignedBB(pos.up()));
		List<LivingEntity> potentialCollateral = world.getEntitiesWithinAABB(LivingEntity.class, potentialCollateralArea);
		for(LivingEntity coll : potentialCollateral)
		{
			AxisAlignedBB entityBB = coll.getBoundingBox().grow(.125f/2+.4);//Add the range of a revolver bullet in all directions
			if(!isValidTarget(coll, false)&&entityBB.calculateIntercept(start, end)!=null)
				return false;
		}
		return true;
	}

	protected Vec3d getTargetVector(LivingEntity e)
	{
		return new Vec3d(e.posX, e.posY+.5*e.getEyeHeight(), e.posZ);
	}

	protected Vec3d getGunPosition()
	{
		return new Vec3d(pos.getX()+.5, pos.getY()+1.375, pos.getZ()+.5);
	}

	protected Vec3d getGunToTargetVec(LivingEntity target)
	{
		//target-gun
		return getGunPosition().subtractReverse(getTargetVector(target));
	}

	@Nullable
	private LivingEntity getTarget()
	{
		double range = getRange();
		List<LivingEntity> list = world.getEntitiesWithinAABB(LivingEntity.class, new AxisAlignedBB(getPos().getX()-range, getPos().getY(), getPos().getZ()-range, getPos().getX()+range, getPos().getY()+3, getPos().getZ()+range));
		if(list.isEmpty())
			return null;
		for(LivingEntity entity : list)
			if(isValidTarget(entity, true))
				return entity;
		return null;
	}

	public boolean isValidTarget(LivingEntity entity, boolean checkCanShoot)
	{
		if(entity==null||!entity.isAlive()||entity.getHealth() <= 0)
			return false;
		//Continue if blacklist and name is in list, or whitelist and name is not in list
		if(whitelist^isListedName(targetList, entity.getName().getString()))
			return false;
		//Same as above but for the owner of the pet, to prevent shooting wolves
		if(entity instanceof IEntityOwnable)
		{
			Entity entityOwner = ((IEntityOwnable)entity).getOwner();
			if(entityOwner!=null&&(whitelist^isListedName(targetList, entityOwner.getName().getString())))
				return false;
		}

		if(entity instanceof AnimalEntity&&!attackAnimals)
			return false;
		if(entity instanceof PlayerEntity&&!attackPlayers)
			return false;
		if(!(entity instanceof PlayerEntity)&&!(entity instanceof AnimalEntity)&&!entity.isCreatureType(EntityClassification.MONSTER, false)&&!attackNeutrals)
			return false;

		if(target==null||entity.getDistanceSq(getPos()) < target.getDistanceSq(getPos())) return true;
		return !checkCanShoot||canShootEntity(entity);
	}

	private boolean isListedName(List<String> list, String name)
	{
		for(String s : list)
			if(name.equalsIgnoreCase(s))
				return true;
		return false;
	}

	protected abstract double getRange();

	protected abstract boolean canActivate();

	protected abstract int getChargeupTicks();

	protected abstract int getActiveTicks();

	protected abstract boolean loopActivation();

	protected abstract void activate();

	protected boolean hasOwnerRights(PlayerEntity player)
	{
		if(player.abilities.isCreativeMode||owner==null||owner.isEmpty())
			return true;
		return owner.equalsIgnoreCase(player.getName().getString());
	}

	@Override
	public void receiveMessageFromClient(CompoundNBT message)
	{
		if(message.hasKey("add"))
			targetList.add(message.getString("add"));
		if(message.hasKey("remove"))
			targetList.remove(message.getInt("remove"));
		if(message.hasKey("whitelist"))
			whitelist = message.getBoolean("whitelist");
		if(message.hasKey("attackAnimals"))
			attackAnimals = message.getBoolean("attackAnimals");
		if(message.hasKey("attackPlayers"))
			attackPlayers = message.getBoolean("attackPlayers");
		if(message.hasKey("attackNeutrals"))
			attackNeutrals = message.getBoolean("attackNeutrals");
		target = null;
		this.markDirty();
	}

	@Override
	public void readCustomNBT(CompoundNBT nbt, boolean descPacket)
	{
		dummy = nbt.getBoolean("dummy");
		redstoneControlInverted = nbt.getBoolean("redstoneInverted");
		facing = Direction.byIndex(nbt.getInt("facing"));
		energyStorage.readFromNBT(nbt);

		if(nbt.hasKey("owner"))
			owner = nbt.getString("owner");
		ListNBT list = nbt.getList("targetList", 8);
		targetList.clear();
		for(int i = 0; i < list.size(); i++)
			targetList.add(list.getString(i));
		whitelist = nbt.getBoolean("whitelist");
		attackAnimals = nbt.getBoolean("attackAnimals");
		attackPlayers = nbt.getBoolean("attackPlayers");
		attackNeutrals = nbt.getBoolean("attackNeutrals");

		target = null;
		if(nbt.contains("target", NBT.TAG_STRING))
			targetId = UUID.fromString(nbt.getString("target"));
	}

	@Override
	public void writeCustomNBT(CompoundNBT nbt, boolean descPacket)
	{
		nbt.putBoolean("dummy", dummy);
		nbt.putBoolean("redstoneInverted", redstoneControlInverted);
		if(facing!=null)
			nbt.putInt("facing", facing.ordinal());
		energyStorage.writeToNBT(nbt);

		if(owner!=null)
			nbt.putString("owner", owner);
		ListNBT list = new ListNBT();
		for(String s : targetList)
			list.add(new StringNBT(s));
		nbt.put("targetList", list);
		nbt.putBoolean("whitelist", whitelist);
		nbt.putBoolean("attackAnimals", attackAnimals);
		nbt.putBoolean("attackPlayers", attackPlayers);
		nbt.putBoolean("attackNeutrals", attackNeutrals);

		if(target!=null)
			nbt.putString("target", target.getUniqueID().toString());
	}

	@Override
	public float[] getBlockBounds()
	{
		if(!dummy)
			return null;
		switch(facing)
		{
			case NORTH:
				return new float[]{.125f, .0625f, .125f, .875f, .875f, 1};
			case SOUTH:
				return new float[]{.125f, .0625f, 0, .875f, .875f, .875f};
			case WEST:
				return new float[]{.125f, .0625f, .125f, 1, .875f, .875f};
			case EAST:
				return new float[]{0, .0625f, .125f, .875f, .875f, .875f};
		}
		return null;
	}

	AxisAlignedBB renderBB;

	@Override
	@OnlyIn(Dist.CLIENT)
	public AxisAlignedBB getRenderBoundingBox()
	{
		if(renderBB==null)
			renderBB = new AxisAlignedBB(getPos().add(-8, -8, -8), getPos().add(8, 8, 8));
		return renderBB;
	}

	@Override
	public boolean hammerUseSide(Direction side, PlayerEntity player, float hitX, float hitY, float hitZ)
	{
		if(dummy)
		{
			TileEntity te = world.getTileEntity(getPos().down());
			if(te instanceof TurretTileEntity)
				return ((TurretTileEntity)te).hammerUseSide(side, player, hitX, hitY, hitZ);
			return false;
		}
		if(player.isSneaking())
		{
			redstoneControlInverted = !redstoneControlInverted;
			ChatUtils.sendServerNoSpamMessages(player, new TranslationTextComponent(Lib.CHAT_INFO+"rsControl."+(redstoneControlInverted?"invertedOn": "invertedOff")));
			markDirty();
			this.markContainingBlockForUpdate(null);
		}
		return true;
	}

	@Override
	public NonNullList<ItemStack> getInventory()
	{
		return NonNullList.create();
	}

	@Override
	public boolean isStackValid(int slot, ItemStack stack)
	{
		return true;
	}

	@Override
	public int getSlotLimit(int slot)
	{
		return 64;
	}

	@Override
	public void doGraphicalUpdates(int slot)
	{
	}

	@Override
	public boolean canUseGui(PlayerEntity player)
	{
		if(hasOwnerRights(player))
			return true;
		ChatUtils.sendServerNoSpamMessages(player, new TranslationTextComponent(Lib.CHAT_INFO+"notOwner", owner));
		return false;
	}

	@Override
	public ResourceLocation getGuiName()
	{
		return Lib.GUIID_Turret;
	}

	@Override
	public IInteractionObjectIE getGuiMaster()
	{
		if(!dummy)
			return this;
		TileEntity te = world.getTileEntity(getPos().down());
		if(te instanceof TurretTileEntity)
			return (IInteractionObjectIE)te;
		return null;
	}

	@Override
	public Direction getFacing()
	{
		return facing;
	}

	@Override
	public void setFacing(Direction facing)
	{
		this.facing = facing;
	}

	@Override
	public int getFacingLimitation()
	{
		return 2;
	}

	@Override
	public boolean mirrorFacingOnPlacement(LivingEntity placer)
	{
		return false;
	}

	@Override
	public boolean canHammerRotate(Direction side, float hitX, float hitY, float hitZ, LivingEntity entity)
	{
		return false;
	}

	@Override
	public boolean canRotate(Direction axis)
	{
		return false;
	}

	@Override
	public boolean canEntityDestroy(Entity entity)
	{
		if(dummy)
		{
			TileEntity te = world.getTileEntity(getPos().down());
			if(te instanceof TurretTileEntity)
				return ((TurretTileEntity)te).canEntityDestroy(entity);
		}
		if(entity instanceof PlayerEntity)
			return hasOwnerRights((PlayerEntity)entity);
		return true;
	}

	@Override
	public boolean isDummy()
	{
		return dummy;
	}

	@Override
	public void placeDummies(BlockPos pos, BlockState state, Direction side, float hitX, float hitY, float hitZ)
	{
		world.setBlockState(pos.up(), state);
		((TurretTileEntity)world.getTileEntity(pos.up())).dummy = true;
		((TurretTileEntity)world.getTileEntity(pos.up())).facing = facing;
	}

	@Override
	public void breakDummies(BlockPos pos, BlockState state)
	{
		if(world.getTileEntity(dummy?getPos().down(): getPos().up()) instanceof TurretTileEntity)
			world.removeBlock(dummy?getPos().down(): getPos().up());
	}

	@Override
	public ItemStack getTileDrop(PlayerEntity player, BlockState state)
	{
		ItemStack stack = new ItemStack(state.getBlock(), 1);
		TurretTileEntity turret = this;
		if(dummy)
		{
			TileEntity t = world.getTileEntity(getPos().down());
			if(t instanceof TurretTileEntity)
				turret = (TurretTileEntity)t;
			else
				return stack;
		}

		CompoundNBT tag = new CompoundNBT();
		//Only writing values when they are different from defaults
		if(turret.owner!=null&&(player==null||!player.getName().getString().equalsIgnoreCase(turret.owner)))
			tag.putString("owner", turret.owner);
		if(turret.targetList.size()!=1||!isListedName(turret.targetList, turret.owner))
		{
			ListNBT list = new ListNBT();
			for(String s : turret.targetList)
				list.add(new StringNBT(s));
			tag.put("targetList", list);
		}
		if(turret.whitelist)
			tag.putBoolean("whitelist", turret.whitelist);
		if(turret.attackAnimals)
			tag.putBoolean("attackAnimals", turret.attackAnimals);
		if(!turret.attackPlayers)
			tag.putBoolean("attackPlayers", turret.attackPlayers);
		if(turret.attackNeutrals)
			tag.putBoolean("attackNeutrals", turret.attackNeutrals);
		if(turret.redstoneControlInverted)
			tag.putBoolean("redstoneControlInverted", turret.redstoneControlInverted);

		if(!tag.isEmpty())
			stack.put(tag);
		return stack;
	}

	@Override
	public void readOnPlacement(@Nullable LivingEntity placer, ItemStack stack)
	{
		if(stack.hasTag())
		{
			CompoundNBT tag = stack.getOrCreateTag();
			if(tag.hasKey("owner"))
				this.owner = tag.getString("owner");
			else if(placer!=null)
				this.owner = placer.getName().getString();
			if(tag.hasKey("targetList"))
			{
				ListNBT list = tag.getList("targetList", 8);
				targetList.clear();
				for(int i = 0; i < list.size(); i++)
					targetList.add(list.getString(i));
			}
			else if(owner!=null)
				targetList.add(owner);
			if(tag.hasKey("whitelist"))
				whitelist = tag.getBoolean("whitelist");
			if(tag.hasKey("attackAnimals"))
				attackAnimals = tag.getBoolean("attackAnimals");
			if(tag.hasKey("attackPlayers"))
				attackPlayers = tag.getBoolean("attackPlayers");
			if(tag.hasKey("attackNeutrals"))
				attackNeutrals = tag.getBoolean("attackNeutrals");
			if(tag.hasKey("redstoneControlInverted"))
				redstoneControlInverted = tag.getBoolean("redstoneControlInverted");
		}
		else if(placer!=null)
		{
			this.owner = placer.getName().getString();
			targetList.add(owner);
		}
	}

	@Nonnull
	@Override
	public FluxStorage getFluxStorage()
	{
		if(dummy)
		{
			TileEntity te = world.getTileEntity(getPos().down());
			if(te instanceof TurretTileEntity)
				return ((TurretTileEntity)te).getFluxStorage();
		}
		return energyStorage;
	}

	@Nonnull
	@Override
	public SideConfig getEnergySideConfig(Direction facing)
	{
		return !dummy?SideConfig.INPUT: SideConfig.NONE;
	}

	IEForgeEnergyWrapper[] wrappers = IEForgeEnergyWrapper.getDefaultWrapperArray(this);

	@Override
	public IEForgeEnergyWrapper getCapabilityWrapper(Direction facing)
	{
		if(!dummy)
			return wrappers[facing==null?0: facing.ordinal()];
		return null;
	}

	static ArrayList<String> displayList = Lists.newArrayList("base");

	@Override
	public ArrayList<String> compileDisplayList()
	{
		return displayList;
	}
}