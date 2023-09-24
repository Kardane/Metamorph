package org.karn.metamorph.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.drex.vanish.api.VanishAPI;
import net.minecraft.command.argument.RegistryEntryArgumentType;
import net.minecraft.entity.EntityType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.karn.metamorph.api.MetamorphAPI;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class MetamorphCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("metamorph")
                .then(argument("on/off", BoolArgumentType.bool())
                        .executes(ctx -> {
                            metamorphtoggle(ctx.getSource(),BoolArgumentType.getBool(ctx,"on/off"), 1);
                            return 1;
                        })
                        .then(argument("num", IntegerArgumentType.integer())
                                .executes(ctx -> {
                                    metamorphtoggle(ctx.getSource(),BoolArgumentType.getBool(ctx,"on/off"), IntegerArgumentType.getInteger(ctx,"num"));
                                    return 1;
                                })
                        )
                )
        );
    }

    public static int metamorphtoggle(ServerCommandSource source, boolean bool, int num) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        VanishAPI.setVanish(player,false);
        if(bool) {
            if(num==1) ((MetamorphAPI) player).MetamorphAs(EntityType.SKELETON);
            else if(num==2) ((MetamorphAPI) player).MetamorphAs(EntityType.CREEPER);
            else if(num==3) ((MetamorphAPI) player).MetamorphAs(EntityType.DROWNED);
            else if(num==4) ((MetamorphAPI) player).MetamorphAs(EntityType.BLAZE);
            else if(num==5) ((MetamorphAPI) player).MetamorphAs(EntityType.ARMOR_STAND);
            else if(num==6) ((MetamorphAPI) player).MetamorphAs(EntityType.ARROW);
            else  return 1;
        } else ((MetamorphAPI) player).clearMetamorph();
        source.sendFeedback(() -> Text.literal("Command Success. Vansih Status: ").append(String.valueOf(bool)), false);
        return 1;
    }
}
