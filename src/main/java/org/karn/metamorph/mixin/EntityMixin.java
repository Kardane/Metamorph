package org.karn.metamorph.mixin;

import com.mojang.datafixers.util.Pair;
import net.minecraft.entity.*;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.karn.metamorph.api.MetamorphAPI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;
import java.util.stream.Collectors;

@Mixin(Entity.class)
public abstract class EntityMixin implements MetamorphAPI {
    @Unique private final Entity metamorphOrigin = (Entity) (Object) this;
    @Shadow public World world;
    @Shadow private UUID uuid;
    @Unique private Entity metamorphEntity;
    @Unique private EntityType<?> metamorphEntityType;
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

        if(this.metamorphEntity instanceof MobEntity)
            ((MobEntity) this.metamorphEntity).setAiDisabled(true);
        this.metamorphEntity.setSilent(true);
        this.metamorphEntity.setNoGravity(true);

        RegistryKey<World> worldRegistryKey = this.world.getRegistryKey();

        // Minor datatracker thingies
        this.UpdateMetamorphData();

        // Updating entity on the client
        List<ServerPlayerEntity> players = this.getDimensionPlayersWithoutSelf(manager, worldRegistryKey);
        //this.sendToAll(players, new EntitiesDestroyS2CPacket(this.id));
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
    public void clearMetamorph() {
        if(!this.isMetamorph()) return;
        this.metamorphEntity.remove(Entity.RemovalReason.DISCARDED);
        this.metamorphEntity = null;
        this.metamorphEntityType = null;
    }

    @Override
    public void UpdateMetamorphData() {
        this.metamorphEntity.setNoGravity(true);
        this.metamorphEntity.setSilent(true);
        this.metamorphEntity.setCustomName(this.getDisplayName());
        this.metamorphEntity.setCustomNameVisible(true);
        this.metamorphEntity.setSprinting(this.isSprinting());
        this.metamorphEntity.setSneaking(this.isSneaking());
        this.metamorphEntity.setSwimming(this.isSwimming());
        this.metamorphEntity.setGlowing(this.isGlowing());
        this.metamorphEntity.setOnFire(this.isOnFire());
        this.metamorphEntity.setPose(this.getPose());
    }

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void postTick(CallbackInfo ci) {
        if(this.isMetamorph()) {
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
}
