package com.projectrebirth.machinemind;

import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;

/** The large animated parts of a node. Each fills a box of blocks: its own block is in the middle of the bottom layer. */
public enum MindModule implements StringRepresentable {
    COMPUTER("mind_computer"),
    LINE("mind_factory_line"),
    FOUNDRY("mind_foundry"),
    HANGAR("mind_hangar");

    private final String id;

    MindModule(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    /** The look of a computer: a rack in stages I and II, a server hall in III and IV, the brain in V. */
    public static int tier(int stage) {
        return stage >= 5 ? 3 : stage >= 3 ? 2 : 1;
    }

    /** Blocks from the middle to each side. */
    public int reach(int tier) {
        return switch (this) {
            case COMPUTER -> tier == 1 ? 0 : 1;
            case LINE, FOUNDRY -> 1;
            case HANGAR -> 2;
        };
    }

    public int height(int tier) {
        return switch (this) {
            case COMPUTER -> tier + 1;
            case LINE -> 2;
            case FOUNDRY -> 3;
            case HANGAR -> 4;
        };
    }

    public Iterable<BlockPos> box(BlockPos pos, int tier) {
        int r = reach(tier);
        return BlockPos.betweenClosed(pos.offset(-r, 0, -r), pos.offset(r, height(tier) - 1, r));
    }

    public String model(int tier) {
        return this == COMPUTER ? "mind_computer_" + tier : id;
    }
}
