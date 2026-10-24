package io.github.autoequipmentmaking.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

/** Observe MiniHUD's existing handshake; never register a second network receiver. */
public final class ServuxSession {
    private record Handshake(Object connection,int protocol,String version) {}
    private static volatile Handshake last;
    private ServuxSession(){}
    static boolean compatible(int protocol,String version){
        return protocol==1&&version!=null&&java.util.regex.Pattern.compile("(?:^|[^0-9.])0\\.11\\.2(?:$|[^0-9.])").matcher(version).find();
    }
    public static boolean metadata(CompoundTag nbt){
        return accept(Minecraft.getInstance().getConnection(),nbt);
    }
    static boolean accept(Object connection,CompoundTag nbt){
        var reply=new Handshake(connection,nbt.getIntOr("version",-1),nbt.getStringOr("servux","?"));last=reply;
        return connection!=null&&compatible(reply.protocol,reply.version);
    }
    static void requireReady(Object connection){
        var reply=last;
        if(reply==null||connection==null||reply.connection!=connection)throw new IllegalStateException("当前连接尚未收到 Servux 握手；请稍后重新开始");
        if(!compatible(reply.protocol,reply.version))throw new IllegalStateException("Servux 握手不兼容：需要 0.11.2 / 协议1，收到 "+reply.version+" / 协议"+reply.protocol);
    }
}
