package fr.cleangang.gta.game;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

public enum GtaTeam {
    POLICE("Police", "Policier", NamedTextColor.BLUE),
    THIEVES("Voleurs", "Voleur", NamedTextColor.RED);

    public final String label;
    public final String member;
    public final TextColor color;

    GtaTeam(String label, String member, TextColor color) {
        this.label = label;
        this.member = member;
        this.color = color;
    }
}
