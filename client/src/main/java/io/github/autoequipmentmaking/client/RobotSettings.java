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
    enum WorkMode implements IConfigOptionListEntry {
        NEWCOMER("制作新人礼包"),BULK("批量制作工具");
        final String label;WorkMode(String label){this.label=label;}
        public String getStringValue(){return name().toLowerCase(Locale.ROOT);}
        public String getDisplayName(){return label;}
        public IConfigOptionListEntry cycle(boolean forward){return this==NEWCOMER?BULK:NEWCOMER;}
        public IConfigOptionListEntry fromString(String value){return "bulk".equalsIgnoreCase(value)?BULK:NEWCOMER;}
    }
    enum BulkItem implements IConfigOptionListEntry {
        NETHERITE_PICKAXE("精准采集下界合金镐","minecraft:netherite_pickaxe"),DIAMOND_PICKAXE("精准采集钻石镐","minecraft:diamond_pickaxe"),ELYTRA("满附魔鞘翅","minecraft:elytra");
        final String label,id;BulkItem(String label,String id){this.label=label;this.id=id;}
        public String getStringValue(){return name().toLowerCase(Locale.ROOT);}
        public String getDisplayName(){return label;}
        public IConfigOptionListEntry cycle(boolean forward){return values()[(ordinal()+(forward?1:values().length-1))%values().length];}
        public IConfigOptionListEntry fromString(String value){for(var item:values())if(item.getStringValue().equalsIgnoreCase(value))return item;return NETHERITE_PICKAXE;}
    }
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
    final ConfigHotkey cancel=new ConfigHotkey("cancel","LEFT_CONTROL,F9","取消旧任务；保留现场物资；再次开始时重新扫描");
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
    final ConfigOptionList workMode=new ConfigOptionList("workMode",WorkMode.NEWCOMER,"工作模式");
    final ConfigBoolean useOldEquipment=new ConfigBoolean("useOldEquipment",true,"使用原料容器中的旧装备制作；关闭后从钻石和木棍新制");
    final ConfigStringList bulkOutputBoxes=new ConfigStringList("bulkOutputBoxes",ImmutableList.of("minecraft:yellow_shulker_box"),"批量工具成品盒子物品 ID");
    final ConfigInteger bulkQuantity=new ConfigInteger("bulkQuantity",1,1,10,true,"制作盒数；每盒27件");
    final ConfigOptionList bulkItem=new ConfigOptionList("bulkItem",BulkItem.NETHERITE_PICKAXE,"物品类型；镐命名为挖山镐");
    final List<IConfigBase> global=List.of(workMode,start,pause,resume,emergency,cancel,bindDelivery,openSettings,scanLength,scanWidth,scanHeight,fontScale,trading,spareAnvils);
    final List<IConfigBase> newcomer=List.of(quantityMode,quantity,outputBoxes,useOldEquipment,swiftSneak,soulSpeed,elytra,shield);
    final List<IConfigBase> bulk=List.of(bulkOutputBoxes,bulkQuantity,bulkItem);
    final List<IConfigBase> all=java.util.stream.Stream.of(global,newcomer,bulk).flatMap(List::stream).toList();
    final List<ConfigHotkey> hotkeys=List.of(start,pause,resume,emergency,cancel,bindDelivery,openSettings);
    private RobotSettings(){
        start.setTranslatedName("开始工作");pause.setTranslatedName("暂停工作");resume.setTranslatedName("恢复工作");emergency.setTranslatedName("急停（保留任务）");cancel.setTranslatedName("取消旧任务");
        bindDelivery.setTranslatedName("绑定交付箱");openSettings.setTranslatedName("打开设置");
        quantityMode.setTranslatedName("装备数量模式");quantity.setTranslatedName("手动制作套数");
        scanLength.setTranslatedName("自动识别长度");scanWidth.setTranslatedName("自动识别宽度");scanHeight.setTranslatedName("自动识别高度");
        fontScale.setTranslatedName("本模组字体倍率");
        outputBoxes.setTranslatedName("产物盒方块 ID 列表");swiftSneak.setTranslatedName("附魔迅捷潜行");soulSpeed.setTranslatedName("附魔灵魂疾行");
        workMode.setTranslatedName("工作模式");useOldEquipment.setTranslatedName("是否使用旧装备制作");bulkOutputBoxes.setTranslatedName("成品盒子物品 ID");bulkQuantity.setTranslatedName("制作盒数");bulkItem.setTranslatedName("物品类型");
        elytra.setTranslatedName("交付鞘翅");shield.setTranslatedName("交付盾牌");trading.setTranslatedName("交易补足缺货物品");spareAnvils.setTranslatedName("损坏后取出备用铁砧");
    }
    void init(Robot robot){
        load();
        start.getKeybind().setCallback((action,key)->{robot.requestStart();return true;});
        pause.getKeybind().setCallback((action,key)->{robot.manualPause("快捷键暂停");return true;});
        resume.getKeybind().setCallback((action,key)->{robot.resume();return true;});
        emergency.getKeybind().setCallback((action,key)->{robot.emergencyStop();return true;});
        cancel.getKeybind().setCallback((action,key)->{robot.cancel();return true;});
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
        for(String value:(workMode.getOptionListValue()==WorkMode.BULK?bulkOutputBoxes:outputBoxes).getStrings()){
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
        int tab;
        double laidOutScale=fontScale.getDoubleValue();
        SettingsGui(){super(10,42,"auto_equipment_making",Minecraft.getInstance().gui.screen(),"自动打装备设置");setConfigWidth(labelColumnWidth());}
        @Override protected void buildConfigSwitcher(){
            var labels=List.of("全局设置","新人装备","批量工具");
            for(int i=0;i<labels.size();i++){int selected=i;addButton(new fi.dy.masa.malilib.gui.button.ButtonGeneric(10+i*110,23,104,18,labels.get(i)),(button,mouse)->{getListWidget().applyPendingModifications();tab=selected;reCreateListWidget();initGui();});}
        }
        public List<ConfigOptionWrapper> getConfigs(){return ConfigOptionWrapper.createFor(switch(tab){case 1->newcomer;case 2->bulk;default->global;});}
        @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event){
            boolean handled=super.mouseReleased(event);
            if(laidOutScale!=fontScale.getDoubleValue()){laidOutScale=fontScale.getDoubleValue();setConfigWidth(labelColumnWidth());initGui();}
            return handled;
        }
        @Override public void removed(){super.removed();save();}
    }
}
