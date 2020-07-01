package us.myles.ViaVersion.protocols.protocol1_16to1_15_2.packets;

import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.IntArrayTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import us.myles.ViaVersion.api.Via;
import us.myles.ViaVersion.api.minecraft.item.Item;
import us.myles.ViaVersion.api.protocol.Protocol;
import us.myles.ViaVersion.api.remapper.PacketRemapper;
import us.myles.ViaVersion.api.rewriters.ItemRewriter;
import us.myles.ViaVersion.api.type.Type;
import us.myles.ViaVersion.api.type.types.UUIDIntArrayType;
import us.myles.ViaVersion.protocols.protocol1_15to1_14_4.ClientboundPackets1_15;
import us.myles.ViaVersion.protocols.protocol1_15to1_14_4.data.RecipeRewriter1_15;
import us.myles.ViaVersion.protocols.protocol1_16to1_15_2.ServerboundPackets1_16;
import us.myles.ViaVersion.protocols.protocol1_16to1_15_2.data.MappingData;

import java.util.UUID;

public class InventoryPackets {

    public static void register(Protocol protocol) {
        ItemRewriter itemRewriter = new ItemRewriter(protocol, InventoryPackets::toClient, InventoryPackets::toServer);

        protocol.registerOutgoing(ClientboundPackets1_15.OPEN_WINDOW, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.VAR_INT);
                map(Type.VAR_INT);
                map(Type.COMPONENT);

                handler(wrapper -> {
                    int windowType = wrapper.get(Type.VAR_INT, 1);
                    if (windowType >= 20) { // smithing added with id 20
                        wrapper.set(Type.VAR_INT, 1, ++windowType);
                    }
                });
            }
        });
        protocol.registerOutgoing(ClientboundPackets1_15.WINDOW_PROPERTY, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.UNSIGNED_BYTE); // Window id
                map(Type.SHORT); // Property
                map(Type.SHORT); // Value

                handler(wrapper -> {
                    short property = wrapper.get(Type.SHORT, 0);
                    if (property >= 4 && property <= 6) { // Enchantment id
                        short enchantmentId = wrapper.get(Type.SHORT, 1);
                        if (enchantmentId >= 11) { // soul_speed added with id 11
                            wrapper.set(Type.SHORT, 1, ++enchantmentId);
                        }
                    }
                });
            }
        });

        itemRewriter.registerSetCooldown(ClientboundPackets1_15.COOLDOWN, InventoryPackets::getNewItemId);
        itemRewriter.registerWindowItems(ClientboundPackets1_15.WINDOW_ITEMS, Type.FLAT_VAR_INT_ITEM_ARRAY);
        itemRewriter.registerTradeList(ClientboundPackets1_15.TRADE_LIST, Type.FLAT_VAR_INT_ITEM);
        itemRewriter.registerSetSlot(ClientboundPackets1_15.SET_SLOT, Type.FLAT_VAR_INT_ITEM);
        itemRewriter.registerAdvancements(ClientboundPackets1_15.ADVANCEMENTS, Type.FLAT_VAR_INT_ITEM);

        protocol.registerOutgoing(ClientboundPackets1_15.ENTITY_EQUIPMENT, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.VAR_INT); // 0 - Entity ID

                handler(wrapper -> {
                    int slot = wrapper.read(Type.VAR_INT);
                    wrapper.write(Type.BYTE, (byte) slot);
                    InventoryPackets.toClient(wrapper.passthrough(Type.FLAT_VAR_INT_ITEM));
                });
            }
        });

        new RecipeRewriter1_15(protocol, InventoryPackets::toClient).registerDefaultHandler(ClientboundPackets1_15.DECLARE_RECIPES);

        itemRewriter.registerClickWindow(ServerboundPackets1_16.CLICK_WINDOW, Type.FLAT_VAR_INT_ITEM);
        itemRewriter.registerCreativeInvAction(ServerboundPackets1_16.CREATIVE_INVENTORY_ACTION, Type.FLAT_VAR_INT_ITEM);

        protocol.registerIncoming(ServerboundPackets1_16.EDIT_BOOK, new PacketRemapper() {
            @Override
            public void registerMap() {
                handler(wrapper -> InventoryPackets.toServer(wrapper.passthrough(Type.FLAT_VAR_INT_ITEM)));
            }
        });
    }

    public static void toClient(Item item) {
        if (item == null) return;

        if (item.getIdentifier() == 771 && item.getTag() != null) {
            CompoundTag tag = item.getTag();
            Tag ownerTag = tag.get("SkullOwner");
            if (ownerTag instanceof CompoundTag) {
                CompoundTag ownerCompundTag = (CompoundTag) ownerTag;
                Tag idTag = ownerCompundTag.get("Id");
                if (idTag instanceof StringTag) {
                    UUID id = UUID.fromString((String) idTag.getValue());
                    ownerCompundTag.put(new IntArrayTag("Id", UUIDIntArrayType.uuidToIntArray(id)));
                }
            }
        }

        item.setIdentifier(getNewItemId(item.getIdentifier()));
    }

    public static void toServer(Item item) {
        if (item == null) return;

        item.setIdentifier(getOldItemId(item.getIdentifier()));

        if (item.getIdentifier() == 771 && item.getTag() != null) {
            CompoundTag tag = item.getTag();
            Tag ownerTag = tag.get("SkullOwner");
            if (ownerTag instanceof CompoundTag) {
                CompoundTag ownerCompundTag = (CompoundTag) ownerTag;
                Tag idTag = ownerCompundTag.get("Id");
                if (idTag instanceof IntArrayTag) {
                    UUID id = UUIDIntArrayType.uuidFromIntArray((int[]) idTag.getValue());
                    ownerCompundTag.put(new StringTag("Id", id.toString()));
                }
            }
        }
    }

    public static int getNewItemId(int id) {
        int newId = MappingData.oldToNewItems.get(id);
        if (newId == -1) {
            Via.getPlatform().getLogger().warning("Missing 1.16 item for 1.15.2 item " + id);
            return 1;
        }
        return newId;
    }

    public static int getOldItemId(int id) {
        int oldId = MappingData.oldToNewItems.inverse().get(id);
        return oldId != -1 ? oldId : 1;
    }
}
