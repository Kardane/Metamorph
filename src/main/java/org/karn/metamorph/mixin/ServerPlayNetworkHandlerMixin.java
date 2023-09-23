package org.karn.metamorph.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.karn.metamorph.api.MetamorphAPI;
import org.karn.metamorph.mixin.accessor.*;
import org.karn.metamorph.util.FakePackets;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.karn.metamorph.Metamorph.METAMORPH_TEAM;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Shadow public ServerPlayerEntity player;
    @Shadow public abstract void sendPacket(Packet<?> packet);

    @Shadow @Final private MinecraftServer server;
    @Unique private final Set<Packet<?>> MetaMorphQ = new HashSet<>();
    @Unique private boolean MetamorphSentTeamPacket;
    @Unique private boolean MetamorphSkipCheck;

    @Inject(
            method = "sendPacket(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/PacketCallbacks;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/ClientConnection;send(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/PacketCallbacks;)V"
            ),
            cancellable = true
    )
    private void metamorphEntity(Packet<ClientPlayPacketListener> packet, PacketCallbacks callbacks, CallbackInfo ci) {
        if (!this.MetamorphSkipCheck) {
            if (packet instanceof BundleS2CPacket bundleS2CPacket) {
                if (bundleS2CPacket.getPackets() instanceof ArrayList<Packet<ClientPlayPacketListener>> list) {
                    var list2 = new ArrayList<Packet<ClientPlayPacketListener>>();
                    var adder = new ArrayList<Packet<ClientPlayPacketListener>>();
                    var atomic = new AtomicBoolean(true);
                    for (var packet2 : list) {
                        atomic.set(true);
                        adder.clear();
                        this.metamorphTransformPacket(packet2, () -> atomic.set(false), list2::add);

                        if (atomic.get()) {
                            list2.add(packet2);
                        }

                        list2.addAll(adder);
                    }

                    list.clear();
                    list.addAll(list2);
                }
            } else {
                this.metamorphTransformPacket(packet, ci::cancel, this::sendPacket);
            }
        }
    }

    @Unique
    private void metamorphTransformPacket(Packet<ClientPlayPacketListener> packet, Runnable remove, Consumer<Packet<ClientPlayPacketListener>> add) {
        World world = this.player.getEntityWorld();
        Entity entity = null;
        if (packet instanceof EntitySpawnS2CPacket) {
            this.server.sendMessage(Text.literal("entity spawn packet: "));
            entity = world.getEntityById(((EntitySpawnS2CPacketAccessor) packet).getEntityId());
        } else if (packet instanceof EntitiesDestroyS2CPacket && ((EntitiesDestroyS2CPacketAccessor) packet).getEntityIds().getInt(0) == this.player.getId()) {
            this.server.sendMessage(Text.literal("entity remove packet: "));
            remove.run();
            return;
        } else if(packet instanceof EntityTrackerUpdateS2CPacket) {
            this.server.sendMessage(Text.literal("entity metamorph tracker packet1 "));
            int entityId = ((EntityTrackerUpdateS2CPacketAccessor) packet).getEntityId();
            if(entityId == this.player.getId() && ((MetamorphAPI) this.player).isMetamorph()) {
                this.server.sendMessage(Text.literal("entity metamorph tracker packet2 "));
                List<DataTracker.SerializedEntry<?>> trackedValues = this.player.getDataTracker().getChangedEntries();
                Byte flags = this.player.getDataTracker().get(EntityAccessor.getFLAGS());

                boolean removed = trackedValues.removeIf(entry -> entry.value().equals(flags));
                this.server.sendMessage(Text.literal(String.valueOf(removed)));
                if(removed) {
                    DataTracker.SerializedEntry<Byte> fakeInvisibleFlag = DataTracker.SerializedEntry.of(EntityAccessor.getFLAGS(), (byte) (flags | 1 << 5));
                    trackedValues.add(fakeInvisibleFlag);
                }

                ((EntityTrackerUpdateS2CPacketAccessor) packet).setTrackedValues(trackedValues);
            } else {
                Entity original = world.getEntityById(entityId);
                this.server.sendMessage(Text.literal("entity metamorph tracker packet3 "));
                if(original != null && ((MetamorphAPI) original).isMetamorph()) {
                    Entity disguised = ((MetamorphAPI) original).getMetamorphEntity();
                    this.server.sendMessage(Text.literal("entity metamorph tracker packet4 "));
                    if(disguised != null) {
                        this.server.sendMessage(Text.literal("entity metamorph tracker packet5 "));
                        ((MetamorphAPI) original).updateMetamorph();
                        List<DataTracker.SerializedEntry<?>> trackedValues = disguised.getDataTracker().getChangedEntries();
                        ((EntityTrackerUpdateS2CPacketAccessor) packet).setTrackedValues(trackedValues);
                    }
                }
            }
            return;
        } else if(packet instanceof EntityAttributesS2CPacket) {
            this.server.sendMessage(Text.literal("entity attribute packet: "));
            // Fixing #2
            // Another client spam
            // Entity attributes "cannot" be sent for non-living entities
            Entity original = world.getEntityById(((EntityAttributesS2CPacketAccessor) packet).getEntityId());
            MetamorphAPI entityDisguise = (MetamorphAPI) original;

            if(original != null && entityDisguise.isMetamorph() && !original.isLiving()) {
                remove.run();
                return;
            }
        } else if(packet instanceof EntityVelocityUpdateS2CPacket velocityPacket) {
            this.server.sendMessage(Text.literal("entity velocity packet: "));
            int id = velocityPacket.getId();
            if(id != this.player.getId()) {

                Entity entity1 = world.getEntityById(id);
                if(entity1 != null && ((MetamorphAPI) entity1).isMetamorph()) {
                    // Cancels some client predictions
                    remove.run();
                }
            }
        }

        if(entity != null) {
            metamorphSendFakePacket(entity, remove, add);
        }
    }

    @Unique
    private void metamorphSendFakePacket(Entity entity, Runnable remove, Consumer<Packet<ClientPlayPacketListener>> add) {
        MetamorphAPI disguise = (MetamorphAPI) entity;
        Entity disguiseEntity = disguise.getMetamorphEntity();

        Packet<?> spawnPacket;
        if(!disguise.isMetamorph()){
            spawnPacket = entity.createSpawnPacket();
        }
        else {
            spawnPacket = FakePackets.EntitySpawnPacket(entity);
        }

        this.MetamorphSkipCheck = true;
        if (entity.getId() == this.player.getId()) {
            if (disguise.getMetamorphType() != EntityType.PLAYER && disguise.isMetamorph()) {
                if (disguiseEntity != null) {
                    if (spawnPacket instanceof EntitySpawnS2CPacket) {
                        if(disguiseEntity == null){
                            return;
                        } else {
                            ((EntitySpawnS2CPacketAccessor) spawnPacket).setEntityId(disguiseEntity.getId());
                            ((EntitySpawnS2CPacketAccessor) spawnPacket).setUuid(disguiseEntity.getUuid());
                        }
                    }
                    disguiseEntity.startRiding(this.player, true);
                    add.accept((Packet<ClientPlayPacketListener>) spawnPacket);

                    TeamS2CPacket joinTeamPacket = TeamS2CPacket.changePlayerTeam(METAMORPH_TEAM, this.player.getGameProfile().getName(), TeamS2CPacket.Operation.ADD); // join team
                    add.accept(joinTeamPacket);
                }
            }
            remove.run();
        } else if(disguise.isMetamorph()) {
            add.accept((Packet<ClientPlayPacketListener>) spawnPacket);
            remove.run();
        }

        this.MetamorphSkipCheck = false;
    }
}
