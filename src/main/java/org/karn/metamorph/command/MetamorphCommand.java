package org.karn.metamorph.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.drex.vanish.api.VanishAPI;
import net.minecraft.entity.EntityType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.karn.metamorph.api.MetamorphAPI;

import static net.minecraft.server.command.CommandManager.literal;

public class MetamorphCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("metamorph")
                .then(CommandManager.literal("on")
                        .executes(ctx -> {
                            metamorphtoggle(ctx.getSource(),true);
                            return 1;
                        })
                )
                .then(CommandManager.literal("off")
                        .executes(ctx -> {
                            metamorphtoggle(ctx.getSource(),false);
                            return 1;
                        })
                )
        );
    }

    public static int metamorphtoggle(ServerCommandSource source, boolean bool) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        VanishAPI.setVanish(player,bool);
        if(bool) {
            ((MetamorphAPI) player).MetamorphAs(EntityType.CREEPER);
        } else ((MetamorphAPI) player).clearMetamorph();
        source.sendFeedback(() -> Text.literal("Command Success. Vansih Status: ").append(String.valueOf(VanishAPI.isVanished(player))), false);
        return 1;
    }
}
