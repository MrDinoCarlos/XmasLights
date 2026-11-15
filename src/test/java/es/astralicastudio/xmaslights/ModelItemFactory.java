package es.astralicastudio.xmaslights;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.mockbukkit.mockbukkit.inventory.ItemFactoryMock;
import org.mockbukkit.mockbukkit.inventory.meta.ItemMetaMock;
import java.util.*;

/** Explicit Paper 1.21.4 component shim: MockBukkit 4.35 predates these item-model methods. */
public final class ModelItemFactory extends ItemFactoryMock implements AutoCloseable {
    private final Map<Object,Object> originalTypes = new IdentityHashMap<>();
    void installItemTypes() {
        if (!originalTypes.isEmpty()) return;
        try {
            var field=org.mockbukkit.mockbukkit.inventory.ItemTypeMock.class.getDeclaredField("metaClass"); field.setAccessible(true);
            for(Material material:List.of(Material.PAPER,Material.GLOWSTONE,Material.LIME_STAINED_GLASS)) {
                Object type=material.asItemType(); originalTypes.put(type,field.get(type)); field.set(type,ModelMeta.class);
            }
        } catch(ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    @Override public void close() {
        try {
            var field=org.mockbukkit.mockbukkit.inventory.ItemTypeMock.class.getDeclaredField("metaClass"); field.setAccessible(true);
            for(var entry:originalTypes.entrySet()) field.set(entry.getKey(),entry.getValue()); originalTypes.clear();
        } catch(ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    @Override public ItemMeta getItemMeta(Material material) {
        return Set.of(Material.PAPER,Material.GLOWSTONE,Material.LIME_STAINED_GLASS).contains(material) ? new ModelMeta() : super.getItemMeta(material);
    }
    @Override public ItemMeta asMetaFor(ItemMeta meta, Material material) {
        if (meta instanceof ModelMeta && Set.of(Material.PAPER,Material.GLOWSTONE,Material.LIME_STAINED_GLASS).contains(material)) return meta.clone();
        return super.asMetaFor(meta,material);
    }
    public static class ModelMeta extends ItemMetaMock {
        private NamespacedKey model;
        private ModelData data = new ModelData();
        @Override public NamespacedKey getItemModel() { return model; }
        @Override public void setItemModel(NamespacedKey model) { this.model=model; }
        @Override public boolean hasItemModel() { return model!=null; }
        @Override public CustomModelDataComponent getCustomModelDataComponent() { return new ModelData(data); }
        @Override @SuppressWarnings("deprecation") public void setCustomModelDataComponent(CustomModelDataComponent value) {
            data=value==null?new ModelData():new ModelData(value);
            super.setCustomModelData(data.floats.isEmpty()?null:data.floats.getFirst().intValue());
        }
    }
    public static class ModelData implements CustomModelDataComponent {
        List<Float> floats=List.of(); List<Boolean> flags=List.of(); List<String> strings=List.of(); List<Color> colors=List.of();
        ModelData() { }
        ModelData(CustomModelDataComponent source) { floats=List.copyOf(source.getFloats());flags=List.copyOf(source.getFlags());strings=List.copyOf(source.getStrings());colors=List.copyOf(source.getColors()); }
        @Override public List<Float> getFloats(){return floats;} @Override public void setFloats(List<Float> value){floats=List.copyOf(value);}
        @Override public List<Boolean> getFlags(){return flags;} @Override public void setFlags(List<Boolean> value){flags=List.copyOf(value);}
        @Override public List<String> getStrings(){return strings;} @Override public void setStrings(List<String> value){strings=List.copyOf(value);}
        @Override public List<Color> getColors(){return colors;} @Override public void setColors(List<Color> value){colors=List.copyOf(value);}
        @Override public Map<String,Object> serialize(){return Map.of("floats",floats,"flags",flags,"strings",strings,"colors",colors);}
    }
}
