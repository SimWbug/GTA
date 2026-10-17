package fr.cleangang.gta.city;

import org.bukkit.Material;

import java.util.Locale;

/**
 * Les trois villes du GTA de 1997, réinterprétées en blocs.
 * Le plan (routes, pâtés) reste le même : le thème change les matériaux, la végétation et les toits.
 */
public enum CityTheme {

    LIBERTY("Liberty City", "liberty",
            Material.GRAY_CONCRETE, Material.YELLOW_CONCRETE, Material.SMOOTH_STONE,
            Material.GRASS_BLOCK, Material.DIRT_PATH, Material.SMOOTH_SANDSTONE, Material.POLISHED_ANDESITE,
            Material.CLAY, Tree.OAK,
            new Material[]{Material.SHORT_GRASS, Material.POPPY, Material.DANDELION, Material.CORNFLOWER},
            new Material[][]{
                    {Material.BRICKS, Material.GRAY_TERRACOTTA},
                    {Material.WHITE_TERRACOTTA, Material.LIGHT_GRAY_CONCRETE},
                    {Material.STONE_BRICKS, Material.CYAN_TERRACOTTA},
                    {Material.QUARTZ_BLOCK, Material.WHITE_CONCRETE},
                    {Material.SANDSTONE, Material.ORANGE_TERRACOTTA},
                    {Material.DEEPSLATE_BRICKS, Material.BLACK_CONCRETE},
                    {Material.TERRACOTTA, Material.BROWN_TERRACOTTA},
                    {Material.POLISHED_ANDESITE, Material.GREEN_TERRACOTTA},
                    {Material.LIGHT_BLUE_TERRACOTTA, Material.BLUE_TERRACOTTA},
                    {Material.YELLOW_TERRACOTTA, Material.LIGHT_GRAY_TERRACOTTA},
            }),

    VICE("Vice City", "vice",
            Material.GRAY_CONCRETE, Material.WHITE_CONCRETE, Material.SMOOTH_QUARTZ,
            Material.SAND, Material.CUT_SANDSTONE, Material.SMOOTH_QUARTZ, Material.LIGHT_GRAY_CONCRETE,
            Material.LIGHT_BLUE_CONCRETE, Tree.PALM,
            new Material[]{Material.DEAD_BUSH, Material.FERN},
            new Material[][]{
                    {Material.WHITE_CONCRETE, Material.PINK_CONCRETE},
                    {Material.PINK_TERRACOTTA, Material.WHITE_CONCRETE},
                    {Material.WHITE_TERRACOTTA, Material.LIGHT_BLUE_CONCRETE},
                    {Material.LIGHT_BLUE_TERRACOTTA, Material.CYAN_CONCRETE},
                    {Material.YELLOW_TERRACOTTA, Material.ORANGE_CONCRETE},
                    {Material.QUARTZ_BLOCK, Material.MAGENTA_TERRACOTTA},
                    {Material.WHITE_CONCRETE, Material.LIME_TERRACOTTA},
                    {Material.PINK_TERRACOTTA, Material.YELLOW_CONCRETE},
                    {Material.CYAN_TERRACOTTA, Material.WHITE_CONCRETE},
                    {Material.SMOOTH_SANDSTONE, Material.PINK_CONCRETE},
            }),

    SAN_ANDREAS("San Andreas", "sanandreas",
            Material.BLACK_CONCRETE, Material.YELLOW_CONCRETE, Material.SMOOTH_STONE,
            Material.GRASS_BLOCK, Material.COARSE_DIRT, Material.SMOOTH_RED_SANDSTONE, Material.POLISHED_ANDESITE,
            Material.CLAY, Tree.ACACIA,
            new Material[]{Material.DEAD_BUSH, Material.SHORT_GRASS, Material.SHORT_GRASS, Material.FERN},
            new Material[][]{
                    {Material.SMOOTH_RED_SANDSTONE, Material.RED_TERRACOTTA},
                    {Material.WHITE_TERRACOTTA, Material.ORANGE_TERRACOTTA},
                    {Material.MUD_BRICKS, Material.BROWN_CONCRETE},
                    {Material.PACKED_MUD, Material.RED_TERRACOTTA},
                    {Material.TERRACOTTA, Material.LIGHT_GRAY_CONCRETE},
                    {Material.SANDSTONE, Material.ORANGE_TERRACOTTA},
                    {Material.BRICKS, Material.BROWN_TERRACOTTA},
                    {Material.WHITE_CONCRETE, Material.RED_TERRACOTTA},
                    {Material.ORANGE_TERRACOTTA, Material.GRAY_CONCRETE},
                    {Material.POLISHED_GRANITE, Material.LIGHT_GRAY_TERRACOTTA},
            });

    public enum Tree { OAK, PALM, ACACIA }

    public final String label;
    public final String id;
    public final Material road, roadLine, sidewalk, ground, path, plaza, parking, waterBed;
    public final Tree tree;
    public final Material[] decorations;
    final Material[][] palettes;

    CityTheme(String label, String id, Material road, Material roadLine, Material sidewalk, Material ground,
              Material path, Material plaza, Material parking, Material waterBed, Tree tree,
              Material[] decorations, Material[][] palettes) {
        this.label = label;
        this.id = id;
        this.road = road;
        this.roadLine = roadLine;
        this.sidewalk = sidewalk;
        this.ground = ground;
        this.path = path;
        this.plaza = plaza;
        this.parking = parking;
        this.waterBed = waterBed;
        this.tree = tree;
        this.decorations = decorations;
        this.palettes = palettes;
    }

    public Material log() {
        return switch (tree) {
            case OAK -> Material.OAK_LOG;
            case PALM -> Material.STRIPPED_JUNGLE_LOG;
            case ACACIA -> Material.ACACIA_LOG;
        };
    }

    public Material leaves() {
        return switch (tree) {
            case OAK -> Material.OAK_LEAVES;
            case PALM -> Material.JUNGLE_LEAVES;
            case ACACIA -> Material.ACACIA_LEAVES;
        };
    }

    /** « vice », « vicecity », « san_andreas »… → thème, ou null. */
    public static CityTheme parse(String s) {
        if (s == null) return null;
        String k = s.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
        for (CityTheme t : values()) {
            if (k.equals(t.id) || k.equals(t.id + "city") || k.equals(t.name().toLowerCase(Locale.ROOT).replace("_", ""))) return t;
        }
        return null;
    }
}
