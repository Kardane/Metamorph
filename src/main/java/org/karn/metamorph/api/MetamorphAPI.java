package org.karn.metamorph.api;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import org.jetbrains.annotations.Nullable;

public interface MetamorphAPI {
    void updateMetamorph();

    boolean isMetamorph();

    void MetamorphAs(EntityType<?> entityType);

    void MetamorphAs(Entity entity);

    void clearMetamorph();

    EntityType<?> getMetamorphType();

    Entity getMetamorphEntity();

    void UpdateMetamorphData();

    void setGameProfile(@Nullable GameProfile gameProfile);
}
