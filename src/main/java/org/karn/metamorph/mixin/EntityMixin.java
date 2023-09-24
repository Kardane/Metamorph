package org.karn.metamorph.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.datafixers.util.Pair;
import net.minecraft.block.BlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.biome.source.BiomeAccess;
import org.jetbrains.annotations.Nullable;
import org.karn.metamorph.api.MetamorphAPI;
import org.karn.metamorph.mixin.accessor.EntityTrackerEntryAccessor;
import org.karn.metamorph.mixin.accessor.ThreadedAnvilChunkStorageAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;
import java.util.stream.Collectors;

import static net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.Action.ADD_PLAYER;

@Mixin(Entity.class)
public abstract class EntityMixin implements MetamorphAPI {
    @Unique private final Entity metamorphOrigin = (Entity) (Object) this;
    @Shadow
    private World world;
    @Shadow
    protected UUID uuid;
    @Unique private Entity metamorphEntity;
    @Unique private EntityType<?> metamorphEntityType;
    @Unique private GameProfile disguiselib$profile;
    @Shadow public abstract float getHeadYaw();
    @Shadow public abstract Text getName();
    @Shadow public abstract DataTracker getDataTracker();
    @Shadow
    @Nullable public abstract Text getCustomName();
    @Shadow public abstract boolean isCustomNameVisible();
    @Shadow public abstract boolean isSprinting();
    @Shadow public abstract boolean isSneaking();
    @Shadow public abstract boolean isSwimming();
    @Shadow public abstract boolean isGlowing();
    @Shadow public abstract boolean isSilent();
    @Shadow private int id;
    @Shadow public abstract EntityPose getPose();
    @Shadow public abstract int getId();
    @Shadow public abstract boolean isOnFire();
    @Shadow public abstract Text getDisplayName();

    @Shadow public abstract void sendMessage(Text message);

    @Shadow public abstract int getFireTicks();

    @Shadow public abstract int getFrozenTicks();

    @Shadow public abstract Vec3d getPos();

    @Shadow public abstract String getEntityName();

    @Shadow private int fireTicks;

    @Shadow public abstract EntityType<?> getType();

    @Shadow public abstract void setNoGravity(boolean noGravity);

    @Shadow protected abstract void fall(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition);

    @Override
    public boolean isMetamorph() {
        return this.metamorphEntity != null;
    }

    @Nullable
    @Override
    public Entity getMetamorphEntity() {
        return this.metamorphEntity;
    }

    @Override
    public EntityType<?> getMetamorphType() {
        return this.metamorphEntityType;
    }

    @Override
    public void MetamorphAs(EntityType<?> entityType) {
        this.metamorphEntityType = entityType;
        PlayerManager manager = this.world.getServer().getPlayerManager();

        if (this.metamorphEntity == null || this.metamorphEntity.getType() != entityType)
            this.metamorphEntity = entityType.create(world);

        if(this.metamorphEntity instanceof MobEntity) {
            ((MobEntity) this.metamorphEntity).setAiDisabled(true);
            ((MobEntity) this.metamorphEntity).setPersistent();
        }

        this.metamorphEntity.setSilent(true);
        this.metamorphEntity.setNoGravity(true);

        RegistryKey<World> worldRegistryKey = this.world.getRegistryKey();

        // Minor datatracker thingies
        this.UpdateMetamorphData();

        // Updating entity on the client
        List<ServerPlayerEntity> players = this.getDimensionPlayersWithoutSelf(manager, worldRegistryKey);
        this.sendToAll(players, new EntitySpawnS2CPacket(this.metamorphOrigin));// will be replaced by network handler

        this.sendToAll(players, new EntityTrackerUpdateS2CPacket(this.id, this.getDataTracker().getChangedEntries()));
        this.sendToAll(players, new EntityEquipmentUpdateS2CPacket(this.id, this.metamorphEquipment()));
        this.sendToAll(players, new EntitySetHeadYawS2CPacket(this.metamorphOrigin, (byte) ((int) (this.getHeadYaw() * 256.0F / 360.0F))));
    }

    private void sendToAll(List<ServerPlayerEntity> players, Packet<?> packet) {
        for (ServerPlayerEntity player : players) {
            player.networkHandler.sendPacket(packet);
        }
    }

    private List<ServerPlayerEntity> getDimensionPlayersWithoutSelf(PlayerManager manager, RegistryKey<World> worldRegistryKey) {
        List<ServerPlayerEntity> players = new ArrayList<>(manager.getServer().getWorld(worldRegistryKey).getPlayers());
        players.remove(this);
        return players;
    }
    @Unique
    private List<Pair<EquipmentSlot, ItemStack>> metamorphEquipment() {
        if(metamorphOrigin instanceof LivingEntity)
            return Arrays.stream(EquipmentSlot.values()).map(slot -> new Pair<>(slot, ((LivingEntity) metamorphOrigin).getEquippedStack(slot))).collect(Collectors.toList());
        return Collections.emptyList();
    }

    @Override
    public void UpdateMetamorphData() {
        this.metamorphEntity.setNoGravity(true);
        this.metamorphEntity.setSilent(true);
        this.metamorphEntity.setCustomName(Text.of(this.getEntityName()));
        this.metamorphEntity.setCustomNameVisible(true);
        this.metamorphEntity.setSprinting(this.isSprinting());
        this.metamorphEntity.setSneaking(this.isSneaking());
        this.metamorphEntity.setSwimming(this.isSwimming());
        this.metamorphEntity.setGlowing(this.isGlowing());
        this.metamorphEntity.setPose(this.getPose());
        this.metamorphEntity.setOnFireFor(this.fireTicks/20);
        this.metamorphEntity.setFireTicks(this.getFireTicks());
        this.metamorphEntity.setFrozenTicks(this.getFrozenTicks());
    }

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void postTick(CallbackInfo ci) {
        if(this.isMetamorph()) {
            if(this.world.getServer().getTicks() % 100 == 0){
                this.UpdateMetamorphData();
            }

            if(this.world.getServer() != null && !(this.metamorphEntity instanceof LivingEntity))
                //LivingEntity가 아니면 위치 패킷 포내기
                this.world.getServer().getPlayerManager().sendToDimension(new EntityPositionS2CPacket(this.metamorphOrigin), this.world.getRegistryKey());
            else if(this.metamorphOrigin instanceof ServerPlayerEntity && this.world.getServer().getTicks() % 40 == 0 && this.metamorphEntity != null) {
                MutableText msg = Text.literal("위장상태: ")
                        .append(Text.translatable(this.metamorphEntity.getType().getTranslationKey()))
                        .formatted(Formatting.GREEN);

                ((ServerPlayerEntity) this.metamorphOrigin).sendMessage(msg, true);
            }
        }
    }


    @Override
    public void clearMetamorph() {
        if(this.metamorphEntity==null) return;
        this.metamorphEntity.remove(Entity.RemovalReason.DISCARDED);
        this.metamorphEntity = this.metamorphOrigin;
        this.metamorphEntityType = this.getType();
        this.setGameProfile(new GameProfile(this.uuid, this.getName().getString()));
        this.metamorphEntity = null;
        this.setNoGravity(false);
    }

    @Override
    public void setGameProfile(@Nullable GameProfile gameProfile) {
        this.disguiselib$profile = gameProfile;
        if(gameProfile != null) {
            String name = gameProfile.getName();
            if(name.length() > 16) {
                // Minecraft kicks players on such profile name received
                name = name.substring(0, 16);
            }
            PropertyMap properties = gameProfile.getProperties();
            this.disguiselib$profile = new GameProfile(gameProfile.getId(), name);
            Collection<Property> textures = properties.get("textures");

            if(!textures.isEmpty())  this.disguiselib$profile.getProperties().put("textures", textures.stream().findFirst().get());
        }

        this.sendProfileUpdate();
    }

    @Unique
    private void sendProfileUpdate() {
        PlayerRemoveS2CPacket packet = new PlayerRemoveS2CPacket(new ArrayList(Collections.singletonList(this.disguiselib$profile.getId())));

        PlayerManager playerManager = this.world.getServer().getPlayerManager();
        playerManager.sendToAll(packet);

        PlayerListS2CPacket addPacket = new PlayerListS2CPacket(ADD_PLAYER, (ServerPlayerEntity) this.metamorphOrigin);
        /*((PlayerListS2CPacketAccessor) addPacket).getEntries().forEach(entry -> {

        });*/
        playerManager.sendToAll(addPacket);

        ServerChunkManager manager = (ServerChunkManager) this.world.getChunkManager();
        ThreadedAnvilChunkStorage storage = manager.threadedAnvilChunkStorage;
        EntityTrackerEntryAccessor trackerEntry = ((ThreadedAnvilChunkStorageAccessor) storage).getEntityTrackers().get(this.getId());
        if (trackerEntry != null)
            trackerEntry.getListeners().forEach(tracking -> trackerEntry.getEntry().startTracking(tracking.getPlayer()));

        // Changing entity on client
        if (this.metamorphOrigin instanceof ServerPlayerEntity player) {
            ServerWorld targetWorld = player.getServerWorld();

            player.networkHandler.sendPacket(new PlayerRespawnS2CPacket(
                    targetWorld.getDimensionKey(),  // getDimension()
                    targetWorld.getRegistryKey(),
                    BiomeAccess.hashSeed(targetWorld.getSeed()),
                    player.interactionManager.getGameMode(),
                    player.interactionManager.getPreviousGameMode(),
                    targetWorld.isDebugWorld(),
                    targetWorld.isFlat(),
                    (byte) 3,
                    Optional.empty(),
                    player.getPortalCooldown()
            ));
            player.networkHandler.requestTeleport(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());

            player.server.getPlayerManager().sendCommandTree(player);

            player.networkHandler.sendPacket(new ExperienceBarUpdateS2CPacket(player.experienceProgress, player.totalExperience, player.experienceLevel));
            player.networkHandler.sendPacket(new HealthUpdateS2CPacket(player.getHealth(), player.getHungerManager().getFoodLevel(), player.getHungerManager().getSaturationLevel()));

            for (StatusEffectInstance statusEffect : player.getStatusEffects()) {
                player.networkHandler.sendPacket(new EntityStatusEffectS2CPacket(player.getId(), statusEffect));
            }

            player.sendAbilitiesUpdate();
            playerManager.sendWorldInfo(player, targetWorld);
            playerManager.sendPlayerStatus(player);
        }
    }
}
