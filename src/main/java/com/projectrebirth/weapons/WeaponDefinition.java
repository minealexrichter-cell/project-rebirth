package com.projectrebirth.weapons;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;

/**
 * What kind of weapon an item is, read from data/&lt;namespace&gt;/projectrebirth/weapon/&lt;item id&gt;.json. How strong it
 * is belongs to the server config, section weapons.&lt;item id&gt;.
 *
 * @param ammo     the only item this weapon loads
 * @param fireMode one shot per press, or shots for as long as the trigger is held
 * @param reload   the whole magazine at once, or round by round
 */
public record WeaponDefinition(Holder<Item> ammo, FireMode fireMode, ReloadMode reload) {
    public static final Codec<WeaponDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("ammo").forGetter(WeaponDefinition::ammo),
            FireMode.CODEC.fieldOf("fire_mode").forGetter(WeaponDefinition::fireMode),
            ReloadMode.CODEC.fieldOf("reload").forGetter(WeaponDefinition::reload)
    ).apply(instance, WeaponDefinition::new));

    public enum FireMode implements StringRepresentable {
        AUTOMATIC("automatic"),
        SEMI_AUTOMATIC("semi_automatic");

        static final Codec<FireMode> CODEC = StringRepresentable.fromEnum(FireMode::values);
        private final String id;

        FireMode(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    public enum ReloadMode implements StringRepresentable {
        MAGAZINE("magazine"),
        SINGLE_ROUND("single_round");

        static final Codec<ReloadMode> CODEC = StringRepresentable.fromEnum(ReloadMode::values);
        private final String id;

        ReloadMode(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }
}
