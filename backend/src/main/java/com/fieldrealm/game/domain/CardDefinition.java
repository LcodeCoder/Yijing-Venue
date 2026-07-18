package com.fieldrealm.game.domain;

import java.util.List;

public record CardDefinition(
        String id,
        String name,
        CardType type,
        Rarity rarity,
        int cost,
        int power,
        int guard,
        int points,
        String effectCode,
        String effect,
        String flavor,
        List<String> tags
) { }
