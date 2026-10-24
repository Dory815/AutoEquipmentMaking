package io.github.autoequipmentmaking.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class AutoEquipmentClient implements ClientModInitializer {
    public static Robot robot;
    @Override public void onInitializeClient(){
        robot=new Robot();
        RobotSettings.INSTANCE.init(robot);
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.CHAT.register((message,signed,sender,params,time)->{
            if(signed!=null&&sender!=null)robot.playerChat(sender.name(),signed.signedContent());
        });
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((message,overlay)->{
            if(!overlay)robot.unsignedPlayerChat(message.getString());
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc->robot.tick());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->{
            var root=literal("aem");
            root.then(literal("start").executes(c->{robot.requestStart();return 1;}));
            root.then(literal("settings").executes(c->{RobotSettings.INSTANCE.openGui();return 1;}));
            root.then(literal("pause").executes(c->{robot.manualPause("用户暂停");return 1;}));
            root.then(literal("resume").executes(c->{robot.resume();return 1;}));
            root.then(literal("status").executes(c->{robot.status();return 1;}));
            root.then(literal("cancel").executes(c->{robot.cancel();return 1;}));
            root.then(literal("bind").executes(c->{robot.bind();return 1;}));
            var mark=literal("mark");for(String type:new String[]{"corner1","corner2","craft","smith","anvil","packing","output"})mark.then(literal(type).executes(c->{robot.mark(type);return 1;}));
            root.then(mark);dispatcher.register(root);
        });
    }
}
