package io.github.autoequipmentmaking.client;

import com.google.common.collect.ImmutableList;
import com.google.gson.*;
import fi.dy.masa.malilib.config.*;
import fi.dy.masa.malilib.config.options.*;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.hotkeys.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/** User settings only; workshop coordinates remain in WorkConfig. */
public final class RobotSettings implements IKeybindProvider {
    enum QuantityMode implements IConfigOptionListEntry {
        AUTO("自动识别"),MANUAL("手动选择");
        final String label;
        QuantityMode(String label){this.label=label;}
        public String getStringValue(){return name().toLowerCase(Locale.ROOT);}
        public String getDisplayName(){return label;}
        public IConfigOptionListEntry cycle(boolean forward){return values()[(ordinal()+(forward?1:values().length-1))%values().length];}
        public IConfigOptionListEntry fromString(String value){for(var mode:values())if(mode.getStringValue().equalsIgnoreCase(value))return mode;return AUTO;}
    }
    static final RobotSettings INSTANCE=new RobotSettings();
    static final Path FILE=WorkConfig.DIR.resolve("settings.json");
    final ConfigHotkey start=new ConfigHotkey("start","F8","开始工作");
    final ConfigHotkey pause=new ConfigHotkey("pause","F10","暂停工作");
    final ConfigHotkey resume=new ConfigHotkey("resume","F12","恢复工作");
    final ConfigHotkey emergency=new ConfigHotkey("emergency","F9","急停");
    final ConfigHotkey bindDelivery=new ConfigHotkey("bindDelivery","B","绑定交付箱");
    final ConfigHotkey openSettings=new ConfigHotkey("openSettings","F6","打开设置");
    final ConfigOptionList quantityMode=new ConfigOptionList("quantityMode",QuantityMode.AUTO,"装备数量模式");
    final ConfigInteger quantity=new ConfigInteger("quantity",1,1,10,true,"手动制作套数");
    final ConfigInteger scanLength=new ConfigInteger("scanLength",21,3,51,true,"自动识别长度");
    final ConfigInteger scanWidth=new ConfigInteger("scanWidth",21,3,51,true,"自动识别宽度");
    final ConfigInteger scanHeight=new ConfigInteger("scanHeight",5,1,21,true,"自动识别高度");
    final ConfigDouble fontScale=new ConfigDouble("fontScale",1.0,0.1,3.0,true,"本模组字体倍率");
    final ConfigStringList outputBoxes=new ConfigStringList("outputBoxes",ImmutableList.of("minecraft:light_blue_shulker_box"),"产物盒方块 ID 列表");
    final ConfigBoolean swiftSneak=new ConfigBoolean("swiftSneak",true,"附魔迅捷潜行");
    final ConfigBoolean soulSpeed=new ConfigBoolean("soulSpeed",true,"附魔灵魂疾行");
    final ConfigBoolean elytra=new ConfigBoolean("elytra",true,"交付鞘翅");
    final ConfigBoolean shield=new ConfigBoolean("shield",true,"交付盾牌");
    final ConfigBoolean trading=new ConfigBoolean("trading",true,"交易补足缺货物品");
    final ConfigBoolean spareAnvils=new ConfigBoolean("spareAnvils",true,"损坏后取出备用铁砧");
    final List<IConfigBase> all=List.of(start,pause,resume,emergency,bindDelivery,openSettings,quantityMode,quantity,scanLength,scanWidth,scanHeight,fontScale,outputBoxes,swiftSneak,soulSpeed,elytra,shield,trading,spareAnvils);
    final List<ConfigHotkey> hotkeys=List.of(start,pause,resume,emergency,bindDelivery,openSettings);
    private RobotSettings(){
        start.setTranslatedName("开始工作");pause.setTranslatedName("暂停工作");resume.setTranslatedName("恢复工作");emergency.setTranslatedName("急停");
        bindDelivery.setTranslatedName("绑定交付箱");openSettings.setTranslatedName("打开设置");
        quantityMode.setTranslatedName("装备数量模式");quantity.setTranslatedName("手动制作套数");
        scanLength.setTranslatedName("自动识别长度");scanWidth.setTranslatedName("自动识别宽度");scanHeight.setTranslatedName("自动识别高度");
        fontScale.setTranslatedName("本模组字体倍率");
        outputBoxes.setTranslatedName("产物盒方块 ID 列表");swiftSneak.setTranslatedName("附魔迅捷潜行");soulSpeed.setTranslatedName("附魔灵魂疾行");
        elytra.setTranslatedName("交付鞘翅");shield.setTranslatedName("交付盾牌");trading.setTranslatedName("交易补足缺货物品");spareAnvils.setTranslatedName("损坏后取出备用铁砧");
    }
    void init(Robot robot){
        load();
        start.getKeybind().setCallback((action,key)->{robot.requestStart();return true;});
        pause.getKeybind().setCallback((action,key)->{robot.manualPause("快捷键暂停");return true;});
        resume.getKeybind().setCallback((action,key)->{robot.resume();return true;});
        emergency.getKeybind().setCallback((action,key)->{robot.emergencyStop();return true;});
        bindDelivery.getKeybind().setCallback((action,key)->{robot.bindDelivery();return true;});
        openSettings.getKeybind().setCallback((action,key)->{openGui();return true;});
        InputEventHandler.getKeybindManager().registerKeybindProvider(this);
    }
    void openGui(){GuiBase.openGui(new SettingsGui());}
    public static float guiFontScale(){return (float)INSTANCE.fontScale.getDoubleValue();}
    public static int labelColumnWidth(){
        int longest=INSTANCE.all.stream().mapToInt(option->Minecraft.getInstance().font.width(option.getTranslatedName())).max().orElse(0);
        return Math.max(210,Math.round(longest*guiFontScale()+50));
    }
    boolean manual(){return quantityMode.getOptionListValue()==QuantityMode.MANUAL;}
    Set<net.minecraft.world.level.block.Block> outputBlocks(){
        var blocks=new HashSet<net.minecraft.world.level.block.Block>();
        for(String value:outputBoxes.getStrings()){
            Identifier id=Identifier.tryParse(value.trim());
            if(id==null||!BuiltInRegistries.BLOCK.containsKey(id))throw new IllegalStateException("未知产物盒方块 ID："+value);
            var block=BuiltInRegistries.BLOCK.getValue(id);
            if(!(block instanceof ShulkerBoxBlock))throw new IllegalStateException("产物盒必须是潜影盒："+value);
            blocks.add(block);
        }
        if(blocks.isEmpty())throw new IllegalStateException("请在设置中填写至少一种产物盒方块 ID");
        return Set.copyOf(blocks);
    }
    void load(){
        try{
            if(!Files.isRegularFile(FILE))return;
            var json=JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
            for(var option:all)if(json.has(option.getName()))option.setValueFromJsonElement(json.get(option.getName()));
        }catch(Exception e){throw new IllegalStateException("设置读取失败："+FILE,e);}
    }
    void save(){
        try{
            Files.createDirectories(FILE.getParent());var json=new JsonObject();json.addProperty("schemaVersion",1);
            for(var option:all)json.add(option.getName(),option.getAsJsonElement());
            Path tmp=FILE.resolveSibling("settings.json.tmp");Files.writeString(tmp,WorkConfig.GSON.toJson(json));Files.move(tmp,FILE,StandardCopyOption.REPLACE_EXISTING);
            InputEventHandler.getKeybindManager().updateUsedKeys();
        }catch(Exception e){throw new IllegalStateException("设置保存失败："+FILE,e);}
    }
    @Override public void addKeysToMap(IKeybindManager manager){for(var option:hotkeys)manager.addKeybindToMap(option.getKeybind());}
    @Override public void addHotkeys(IKeybindManager manager){manager.addHotkeysForCategory("自动打装备","操作",hotkeys);}
    private final class SettingsGui extends GuiConfigsBase {
        double laidOutScale=fontScale.getDoubleValue();
        SettingsGui(){super(10,42,"auto_equipment_making",Minecraft.getInstance().gui.screen(),"自动打装备设置");setConfigWidth(labelColumnWidth());}
        @Override protected void buildConfigSwitcher(){}
        public List<ConfigOptionWrapper> getConfigs(){return ConfigOptionWrapper.createFor(all);}
        @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event){
            boolean handled=super.mouseReleased(event);
            if(laidOutScale!=fontScale.getDoubleValue()){laidOutScale=fontScale.getDoubleValue();setConfigWidth(labelColumnWidth());initGui();}
            return handled;
        }
        @Override public void removed(){super.removed();save();}
    }
}
