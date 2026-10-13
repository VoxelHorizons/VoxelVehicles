package org.voxelhorizons.vehicles;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * The GUI icon renders using its own VoxelCore item, but its hover metadata
 * must come from the exact original vehicle item captured on placement.
 * These lightweight ItemMeta proxies work without a Bukkit server.
 */
public class VehiclePreviewMetaTest {
    private static final class Meta implements InvocationHandler {
        String name;
        List<String> lore;
        Map<Enchantment,Integer> enchants=new HashMap<Enchantment,Integer>();
        Set<ItemFlag> flags=EnumSet.noneOf(ItemFlag.class);
        boolean unbreakable;
        // Represents VoxelCore item/model identity, which must remain untouched.
        final String renderIdentity;
        Meta(String renderIdentity){this.renderIdentity=renderIdentity;}
        ItemMeta proxy(){
            return (ItemMeta)Proxy.newProxyInstance(ItemMeta.class.getClassLoader(),
                    new Class<?>[]{ItemMeta.class},this);
        }
        @Override public Object invoke(Object proxy,Method method,Object[] arguments){
            String name=method.getName();
            if("hasDisplayName".equals(name))return this.name!=null;
            if("getDisplayName".equals(name))return this.name;
            if("setDisplayName".equals(name)){this.name=(String)arguments[0];return null;}
            if("hasLore".equals(name))return lore!=null;
            if("getLore".equals(name))return lore==null?null:new ArrayList<String>(lore);
            if("setLore".equals(name)){
                lore=arguments[0]==null?null:new ArrayList<String>((List<String>)arguments[0]);
                return null;
            }
            if("getEnchants".equals(name))return new HashMap<Enchantment,Integer>(enchants);
            if("removeEnchant".equals(name))return enchants.remove(arguments[0])!=null;
            if("addEnchant".equals(name)){
                enchants.put((Enchantment)arguments[0],(Integer)arguments[1]);
                return true;
            }
            if("getItemFlags".equals(name))return EnumSet.copyOf(flags);
            if("removeItemFlags".equals(name)){
                for(ItemFlag flag:(ItemFlag[])arguments[0])flags.remove(flag);
                return null;
            }
            if("addItemFlags".equals(name)){
                flags.addAll(Arrays.asList((ItemFlag[])arguments[0]));
                return null;
            }
            if("isUnbreakable".equals(name))return unbreakable;
            if("setUnbreakable".equals(name)){unbreakable=(Boolean)arguments[0];return null;}
            if("toString".equals(name))return renderIdentity;
            throw new UnsupportedOperationException("Unexpected ItemMeta method: "+name);
        }
    }

    @Test public void originalVehicleTooltipOverridesNormalOrChestIconMetadata(){
        for(String icon:Arrays.asList("voxel:tractor_icon","voxel:tractor_chest_icon")){
            Meta preview=new Meta(icon);
            preview.name="§fGeneric Tractor";
            preview.lore=Arrays.asList("§7Generic icon lore");
            preview.enchants.put(Enchantment.DURABILITY,1);
            preview.flags.add(ItemFlag.HIDE_ENCHANTS);

            Meta original=new Meta("voxel:tractor-yellow");
            original.name="§6Golden Tractor";
            original.lore=Arrays.asList("§7Unique player lore","§eOwner edition");
            original.enchants.put(Enchantment.DAMAGE_ALL,2);
            original.flags.add(ItemFlag.HIDE_ATTRIBUTES);
            original.unbreakable=true;

            VehicleInventories.copyVehiclePresentation(preview.proxy(),original.proxy());

            assertEquals("§6Golden Tractor",preview.name);
            assertEquals(original.lore,preview.lore);
            assertEquals(original.enchants,preview.enchants);
            assertEquals(original.flags,preview.flags);
            assertTrue(preview.unbreakable);
            assertEquals(icon,preview.renderIdentity);
            assertEquals("voxel:tractor-yellow",original.renderIdentity);
            assertEquals("§6Golden Tractor",original.name);
            assertEquals(Arrays.asList("§7Unique player lore","§eOwner edition"),original.lore);
        }
    }

    @Test public void noAuthoredNameOrLoreDoesNotLeakGenericIconText(){
        Meta preview=new Meta("voxel:tractor_chest_icon");
        preview.name="§fTractor";
        preview.lore=Arrays.asList("§7Generic");
        preview.unbreakable=true;
        Meta original=new Meta("voxel:tractor");
        VehicleInventories.copyVehiclePresentation(preview.proxy(),original.proxy());
        assertNull(preview.name);
        assertNull(preview.lore);
        assertFalse(preview.unbreakable);
        assertEquals("voxel:tractor_chest_icon",preview.renderIdentity);
    }

    @Test public void invisibleFillersCopyTheOriginalVehicleTooltip(){
        Meta displayed=new Meta("voxel:tractor_chest_icon");
        displayed.name="§6Golden Tractor";
        displayed.lore=Arrays.asList("§7Owner edition");
        Meta filler=new Meta("voxel:ui-blank");
        filler.name="§fBlank";
        VehicleInventories.copyPreviewHover(filler.proxy(),displayed.proxy());
        assertEquals("§6Golden Tractor",filler.name);
        assertEquals(displayed.lore,filler.lore);
        assertEquals("voxel:ui-blank",filler.renderIdentity);
    }
}
