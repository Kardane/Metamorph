package org.karn.metamorph.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.karn.metamorph.api.MetamorphAPI;
import org.karn.metamorph.mixin.accessor.*;
import org.karn.metamorph.util.FakePackets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.karn.metamorph.Metamorph.METAMORPH_TEAM;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Shadow public ServerPlayerEntity player;
    @Shadow public abstract void sendPacket(Packet<?> packet);
    @Unique private final Set<Packet<?>> MetaMorphQ = new HashSet<>();
    @Unique private boolean MetamorphSentTeamPacket;
    @Unique private boolean MetamorphSkipCheck;

    @Inject(
            method = "sendPacket(Lnet/minecraft/network/Packet;Lnet/minecraft/network/PacketCallbacks;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/ClientConnection;send(Lnet/minecraft/network/Packet;Lnet/minecraft/network/PacketCallbacks;)V"
            ),
            cancellable = true
    )
    private void metamorphEntity(Packet<?> packet, PacketCallbacks callbacks, CallbackInfo ci) {
        if (!this.MetamorphSkipCheck) {
            World world = this.player.getEntityWorld();
            Entity entity = null;
            if (packet instanceof EntitySpawnS2CPacket) {
                entity = world.getEntityById(((EntitySpawnS2CPacketAccessor) packet).getEntityId());
            } else if (packet instanceof EntitiesDestroyS2CPacket && ((EntitiesDestroyS2CPacketAccessor) packet).getEntityIds().getInt(0) == this.player.getId()) {
                ci.cancel();
                return;
            } else if(packet instanceof EntityTrackerUpdateS2CPacket) {
                // an ugly fix for #6
                int entityId = ((EntityTrackerUpdateS2CPacketAccessor) packet).getEntityId();
                if(entityId == this.player.getId() && ((MetamorphAPI) this.player).isMetamorph()) {
                    List<DataTracker.SerializedEntry<?>> trackedValues = this.player.getDataTracker().getChangedEntries();
                    Byte flags = this.player.getDataTracker().get(EntityAccessor.getFLAGS());
                    boolean removed = trackedValues.removeIf(entry -> entry.value().equals(flags));
                    if(removed) {
                        DataTracker.SerializedEntry<Byte> fakeInvisibleFlag = DataTracker.SerializedEntry.of(EntityAccessor.getFLAGS(), (byte) (flags | 1 << 5));
                        trackedValues.add(fakeInvisibleFlag);
                    }
                    ((EntityTrackerUpdateS2CPacketAccessor) packet).setTrackedValues(trackedValues);
                } else {
                    Entity original = world.getEntityById(entityId);

                    if(original != null && ((MetamorphAPI) original).isMetamorph()) {
                        Entity disguised = ((MetamorphAPI) original).getMetamorphEntity();
                        if(disguised != null) {
                            ((MetamorphAPI) original).updateMetamorph();
                            List<DataTracker.SerializedEntry<?>> trackedValues = disguised.getDataTracker().getChangedEntries();
                            ((EntityTrackerUpdateS2CPacketAccessor) packet).setTrackedValues(trackedValues);
                        }
                    }
                }
                return;
            } else if(packet instanceof EntityAttributesS2CPacket) {
                // Fixing #2
                // Another client spam
                // Entity attributes "cannot" be sent for non-living entities
                Entity original = world.getEntityById(((EntityAttributesS2CPacketAccessor) packet).getEntityId());
                MetamorphAPI entityDisguise = (MetamorphAPI) original;

                if(original != null && entityDisguise.isMetamorph() && !((MetamorphAPI) original).getMetamorphEntity().isLiving()) {
                    ci.cancel();
                    return;
                }
            } else if(packet instanceof EntityVelocityUpdateS2CPacket velocityPacket) {
                int id = velocityPacket.getId();
                if(id != this.player.getId()) {

                    Entity entity1 = world.getEntityById(id);
                    if(entity1 != null && ((MetamorphAPI) entity1).isMetamorph()) {
                        // Cancels some client predictions
                        ci.cancel();
                    }
                }
            }

            if(entity != null) {
                metamorphsendFakePacket(entity, ci);
            }
        }
    }

    /**
     * Sends fake packet instead of the real one.
     *
     * @param entity the entity that is disguised and needs to have a custom packet sent.
     */
    @Unique
    private void metamorphsendFakePacket(Entity entity, CallbackInfo ci) {
        MetamorphAPI meta = (MetamorphAPI) entity;
        Entity disguiseEntity = meta.getMetamorphEntity();

        Packet<?> spawnPacket;
        if(!meta.isMetamorph())
            spawnPacket = entity.createSpawnPacket();
        else
            spawnPacket = FakePackets.EntitySpawnPacket(entity);

        if (entity.getId() == this.player.getId()) {
            // We must treat disguised player differently
            // Why, I hear you ask ..?
            // Well, sending spawn packet of the new entity makes the player not being able to move :(
            if (meta.getMetamorphType() != EntityType.PLAYER && meta.isMetamorph()) {
                if (disguiseEntity != null) {
                    if (spawnPacket instanceof EntitySpawnS2CPacket) {
                        ((EntitySpawnS2CPacketAccessor) spawnPacket).setEntityId(disguiseEntity.getId());
                        ((EntitySpawnS2CPacketAccessor) spawnPacket).setUuid(disguiseEntity.getUuid());
                    }
                    disguiseEntity.startRiding(this.player, true);
                    this.sendPacket(spawnPacket);

                    TeamS2CPacket joinTeamPacket = TeamS2CPacket.changePlayerTeam(METAMORPH_TEAM, this.player.getGameProfile().getName(), TeamS2CPacket.Operation.ADD); // join team
                    this.sendPacket(joinTeamPacket);
                }
            }
            ci.cancel();
        } else if(meta.isMetamorph()) {
            this.sendPacket(spawnPacket);
            ci.cancel();
        }
    }
}
