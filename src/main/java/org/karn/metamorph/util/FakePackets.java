package org.karn.metamorph.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerSpawnS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.karn.metamorph.api.MetamorphAPI;
import org.karn.metamorph.mixin.accessor.EntitySpawnS2CPacketAccessor;

public class FakePackets {
    public static Packet<?> universalSpawnPacket(Entity entity) {
        Entity metamorphEntity = ((MetamorphAPI) entity).getMetamorphEntity();
        if(metamorphEntity == null) {
            metamorphEntity = entity;
        }

        try {
            Packet<?> packet = metamorphEntity.createSpawnPacket();
            entity.getServer().sendMessage(Text.literal(packet.toString()));
            if(packet instanceof EntitySpawnS2CPacket) {
                packet = EntitySpawnPacket(entity);
            }

            return packet;
        } catch (Throwable e) {
            return entity.createSpawnPacket();
        }
    }
    public static Packet<?> EntitySpawnPacket(Entity entity){
        MetamorphAPI meta = (MetamorphAPI) entity;
        EntitySpawnS2CPacket packet = new EntitySpawnS2CPacket(meta.getMetamorphEntity());

        EntitySpawnS2CPacketAccessor accessor = (EntitySpawnS2CPacketAccessor) packet;
        accessor.setEntityId(entity.getId());
        accessor.setUuid(entity.getUuid());

        var type = meta.getMetamorphType();

        accessor.setEntityType(type != EntityType.MARKER ? type : EntityType.PIG);
        accessor.setX(entity.getX());
        accessor.setY(entity.getY());
        accessor.setZ(entity.getZ());

        accessor.setYaw((byte) ((int) (entity.getY() * 256.0F / 360.0F)));
        accessor.setHeadYaw((byte) ((int) (entity.getHeadYaw() * 256.0F / 360.0F)));
        accessor.setPitch((byte) ((int) (entity.getPitch() * 256.0F / 360.0F)));

        double max = 3.9D;
        Vec3d vec3d = entity.getVelocity();
        double e = MathHelper.clamp(vec3d.x, -max, max);
        double f = MathHelper.clamp(vec3d.y, -max, max);
        double g = MathHelper.clamp(vec3d.z, -max, max);
        accessor.setVelocityX((int) (e * 8000.0D));
        accessor.setVelocityY((int) (f * 8000.0D));
        accessor.setVelocityZ((int) (g * 8000.0D));

        return packet;
    }
}
