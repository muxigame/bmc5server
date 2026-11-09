package com.muxigame.realneeds;

public enum Need {
    PROTEIN("protein", "蛋白质", "肉类、鱼类", 0.4, 0xffd99974),
    ENERGY("energy", "能量", "所有食物", 1.5, 0xffe4bf58),
    VITAMINS("vitamins", "维生素", "水果、蔬菜", 0.5, 0xff85bd76),
    WATER("water", "水分", "水瓶、牛奶、汤", 2, 0xff6fb9de),
    SLEEP("sleep", "睡眠", "床上休息、完整过夜", 1, 0xffb5a1dc);

    public final String id, label, hint;
    public final double rate;
    public final int color;
    Need(String id, String label, String hint, double rate, int color) {
        this.id=id; this.label=label; this.hint=hint; this.rate=rate; this.color=color;
    }
    public static Need byId(String id) {
        for (Need n:values()) if(n.id.equals(id)) return n;
        throw new IllegalArgumentException("未知需求："+id);
    }
}
