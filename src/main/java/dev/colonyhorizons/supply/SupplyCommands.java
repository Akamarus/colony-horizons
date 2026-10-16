package dev.colonyhorizons.supply;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@EventBusSubscriber(modid="colonyhorizons")
public final class SupplyCommands {
    @SubscribeEvent public static void register(RegisterCommandsEvent event){
        var root=Commands.literal("chsupply");
        root.then(Commands.literal("menu").executes(c->run(c,()->{
            var p=c.getSource().getPlayerOrException();var colony=com.minecolonies.api.colony.IColonyManager.getInstance().getColonyByPosFromWorld(p.serverLevel(),p.blockPosition());
            if(colony==null)throw new IllegalArgumentException("Visit your colony to open its supplies menu");
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,dev.colonyhorizons.menu.SupplyMenuSessions.open(p,colony.getID()));
        })));
        root.then(Commands.literal("bind").then(Commands.argument("name",StringArgumentType.word())
            .then(Commands.argument("outpost",BoolArgumentType.bool()).then(Commands.argument("hut",BlockPosArgument.blockPos()).executes(c->run(c,()->{
                var player=c.getSource().getPlayerOrException();var service=NativeSupplyService.get(player.serverLevel());
                var id=service.bind(player.getUUID(),BlockPosArgument.getLoadedBlockPos(c,"hut"),StringArgumentType.getString(c,"name"),BoolArgumentType.getBool(c,"outpost"));
                reply(c,"Bound hut: "+id+". true = outpost, false = home.");
            }))))));
        root.then(Commands.literal("route").then(Commands.argument("source",StringArgumentType.word())
            .then(Commands.argument("destination",StringArgumentType.word()).then(Commands.argument("target",IntegerArgumentType.integer(1,4096))
            .then(Commands.argument("reserve",IntegerArgumentType.integer(0,4096)).then(Commands.argument("batch",IntegerArgumentType.integer(1,64)).executes(c->run(c,()->{
                var p=c.getSource().getPlayerOrException();var item=p.getMainHandItem();if(item.isEmpty())throw new IllegalArgumentException("Hold the exact item to ship; it will not be consumed");
                var id=NativeSupplyService.get(p.serverLevel()).link(p.getUUID(),StringArgumentType.getString(c,"source"),StringArgumentType.getString(c,"destination"),item,IntegerArgumentType.getInteger(c,"target"),IntegerArgumentType.getInteger(c,"reserve"),IntegerArgumentType.getInteger(c,"batch"));
                reply(c,"Protected route created: "+id);
            }))))))));
        for(String action:new String[]{"pause","resume","cancel"})root.then(Commands.literal(action).then(Commands.argument("route",UuidArgument.uuid()).executes(c->run(c,()->{
            var p=c.getSource().getPlayerOrException();var service=NativeSupplyService.get(p.serverLevel());var id=UuidArgument.getUuid(c,"route");
            if(action.equals("cancel"))service.cancel(p.getUUID(),id);else service.pause(p.getUUID(),id,action.equals("pause"));
            reply(c,action+": "+id+(action.equals("cancel")?"; remaining cargo returns to source when available":""));
        }))));
        root.then(Commands.literal("status").executes(c->run(c,()->{
            var p=c.getSource().getPlayerOrException();var service=NativeSupplyService.get(p.serverLevel());
            if(!service.problem().isEmpty()){reply(c,service.problem());return;}
            var endpoints=service.endpoints(p.getUUID());var routes=service.routes(p.getUUID());
            reply(c,"Protected supply: "+endpoints.size()+" huts, "+routes.size()+" routes. Uses hut inventories; racks are not included.");
            for(var e:endpoints)reply(c,e.name()+" ("+(e.outpost()?"outpost":"home")+"): "+service.availability(e));
            for(var r:routes)reply(c,r.id()+": "+r.reason()+", cargo "+(r.cargo().isEmpty()?"none":r.cargo().getCount()+" "+r.cargo().getHoverName().getString())+", travel "+((r.remainingTicks()+19)/20)+"s, delivered "+r.delivered());
        })));
        event.getDispatcher().register(root);
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Post event){
        if(event.getLevel() instanceof ServerLevel level&&level.getGameTime()%100==0)NativeSupplyService.get(level).tick(100);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){NativeSupplyService.stop();dev.colonyhorizons.menu.SupplyMenuSessions.clear();}
    private interface Action {void run()throws Exception;}
    private static int run(CommandContext<CommandSourceStack> c,Action action){
        try{action.run();return 1;}catch(Exception e){c.getSource().sendFailure(Component.literal("Supply: "+e.getMessage()));return 0;}
    }
    private static void reply(CommandContext<CommandSourceStack> c,String text){c.getSource().sendSuccess(()->Component.literal(text),false);}
}
