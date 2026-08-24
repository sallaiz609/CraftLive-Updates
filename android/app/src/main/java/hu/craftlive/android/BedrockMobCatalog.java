package hu.craftlive.android;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class BedrockMobCatalog {
    public static final class Item {
        public final String id;
        private final String hu;
        private final String en;

        private Item(String id, String hu, String en) {
            this.id = id;
            this.hu = hu;
            this.en = en;
        }

        public String label() {
            String language = Locale.getDefault().getLanguage();
            return ("hu".equals(language) ? hu : en) + "  ·  " + id;
        }
    }

    private static final List<Item> ITEMS;

    static {
        ArrayList<Item> items = new ArrayList<>();
        add(items, "allay", "Allay", "Allay");
        add(items, "armadillo", "Tatu", "Armadillo");
        add(items, "axolotl", "Axolotl", "Axolotl");
        add(items, "bat", "Denevér", "Bat");
        add(items, "bee", "Méh", "Bee");
        add(items, "blaze", "Őrláng", "Blaze");
        add(items, "bogged", "Mocsári csontváz", "Bogged");
        add(items, "breeze", "Szellő", "Breeze");
        add(items, "camel", "Teve", "Camel");
        add(items, "camel_husk", "Kiszáradt teve", "Camel Husk");
        add(items, "cat", "Macska", "Cat");
        add(items, "cave_spider", "Barlangi pók", "Cave Spider");
        add(items, "chicken", "Csirke", "Chicken");
        add(items, "cod", "Tőkehal", "Cod");
        add(items, "copper_golem", "Rézgólem", "Copper Golem");
        add(items, "cow", "Tehén", "Cow");
        add(items, "creaking", "Nyikorgó", "Creaking");
        add(items, "creeper", "Creeper", "Creeper");
        add(items, "dolphin", "Delfin", "Dolphin");
        add(items, "donkey", "Szamár", "Donkey");
        add(items, "drowned", "Vízbefúlt", "Drowned");
        add(items, "elder_guardian", "Ősi őrszem", "Elder Guardian");
        add(items, "ender_dragon", "Endersárkány", "Ender Dragon");
        add(items, "enderman", "Enderman", "Enderman");
        add(items, "endermite", "Endermite", "Endermite");
        add(items, "evocation_illager", "Idéző", "Evoker");
        add(items, "fox", "Róka", "Fox");
        add(items, "frog", "Béka", "Frog");
        add(items, "ghast", "Ghast", "Ghast");
        add(items, "glow_squid", "Világító tintahal", "Glow Squid");
        add(items, "goat", "Kecske", "Goat");
        add(items, "guardian", "Őrszem", "Guardian");
        add(items, "happy_ghast", "Vidám Ghast", "Happy Ghast");
        add(items, "hoglin", "Hoglin", "Hoglin");
        add(items, "horse", "Ló", "Horse");
        add(items, "husk", "Kiszáradt zombi", "Husk");
        add(items, "iron_golem", "Vasgólem", "Iron Golem");
        add(items, "llama", "Láma", "Llama");
        add(items, "magma_cube", "Magmakocka", "Magma Cube");
        add(items, "mooshroom", "Gombatehén", "Mooshroom");
        add(items, "mule", "Öszvér", "Mule");
        add(items, "nautilus", "Nautilus", "Nautilus");
        add(items, "npc", "NPC", "NPC");
        add(items, "ocelot", "Ocelot", "Ocelot");
        add(items, "panda", "Panda", "Panda");
        add(items, "parched", "Kiszáradt csontváz", "Parched");
        add(items, "parrot", "Papagáj", "Parrot");
        add(items, "phantom", "Fantom", "Phantom");
        add(items, "pig", "Malac", "Pig");
        add(items, "piglin", "Piglin", "Piglin");
        add(items, "piglin_brute", "Piglin verőember", "Piglin Brute");
        add(items, "pillager", "Fosztogató", "Pillager");
        add(items, "polar_bear", "Jegesmedve", "Polar Bear");
        add(items, "pufferfish", "Gömbhal", "Pufferfish");
        add(items, "rabbit", "Nyúl", "Rabbit");
        add(items, "ravager", "Pusztító", "Ravager");
        add(items, "salmon", "Lazac", "Salmon");
        add(items, "sheep", "Bárány", "Sheep");
        add(items, "shulker", "Shulker", "Shulker");
        add(items, "silverfish", "Ezüstmoly", "Silverfish");
        add(items, "skeleton", "Csontváz", "Skeleton");
        add(items, "skeleton_horse", "Csontvázló", "Skeleton Horse");
        add(items, "slime", "Nyálka", "Slime");
        add(items, "sniffer", "Szimatoló", "Sniffer");
        add(items, "snow_golem", "Hógólem", "Snow Golem");
        add(items, "spider", "Pók", "Spider");
        add(items, "squid", "Tintahal", "Squid");
        add(items, "stray", "Kóborló", "Stray");
        add(items, "strider", "Lépegető", "Strider");
        add(items, "sulfur_cube", "Kénkocka", "Sulfur Cube");
        add(items, "tadpole", "Ebihal", "Tadpole");
        add(items, "trader_llama", "Kereskedőláma", "Trader Llama");
        add(items, "tropicalfish", "Trópusi hal", "Tropical Fish");
        add(items, "turtle", "Teknős", "Turtle");
        add(items, "vex", "Bosszúálló szellem", "Vex");
        add(items, "villager", "Falusi", "Villager");
        add(items, "villager_v2", "Falusi (új)", "Villager (new)");
        add(items, "vindicator", "Bosszúálló", "Vindicator");
        add(items, "wandering_trader", "Vándorkereskedő", "Wandering Trader");
        add(items, "warden", "Warden", "Warden");
        add(items, "witch", "Boszorkány", "Witch");
        add(items, "wither", "Wither", "Wither");
        add(items, "wither_skeleton", "Wither csontváz", "Wither Skeleton");
        add(items, "wolf", "Farkas", "Wolf");
        add(items, "zoglin", "Zoglin", "Zoglin");
        add(items, "zombie", "Zombi", "Zombie");
        add(items, "zombie_horse", "Zombiló", "Zombie Horse");
        add(items, "zombie_nautilus", "Zombi nautilus", "Zombie Nautilus");
        add(items, "zombie_pigman", "Zombifikált piglin", "Zombified Piglin");
        add(items, "zombie_villager", "Zombi falusi", "Zombie Villager");
        add(items, "zombie_villager_v2", "Zombi falusi (új)", "Zombie Villager (new)");
        ITEMS = Collections.unmodifiableList(items);
    }

    private BedrockMobCatalog() {
    }

    public static List<Item> all() {
        return ITEMS;
    }

    public static int indexOf(String id) {
        if (id == null) return 0;
        for (int index = 0; index < ITEMS.size(); index++) {
            if (ITEMS.get(index).id.equalsIgnoreCase(id.trim())) return index;
        }
        return 0;
    }

    private static void add(List<Item> items, String id, String hu, String en) {
        items.add(new Item(id, hu, en));
    }
}
