package org.karn.metamorph.api;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;

public interface MetamorphAPI {
    void updateMetamorph();

    boolean isMetamorph();

    void MetamorphAs(EntityType<?> entityType);

    void MetamorphAs(Entity entity);

    void clearMetamorph();

    EntityType<?> getMetamorphType();

    Entity getMetamorphEntity();
}
