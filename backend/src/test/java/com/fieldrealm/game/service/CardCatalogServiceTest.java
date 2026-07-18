package com.fieldrealm.game.service;

import com.fieldrealm.game.domain.CardDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CardCatalogServiceTest {
    @Test
    void starterDeckMeetsConstructionRules() {
        CardCatalogService catalog = new CardCatalogService();
        assertThat(catalog.starterDeck()).hasSize(40);
        long sites = catalog.starterDeck().stream().map(catalog::require).map(CardDefinition::type).filter(t -> t.name().equals("SITE")).count();
        long units = catalog.starterDeck().stream().map(catalog::require).map(CardDefinition::type).filter(t -> t.name().equals("UNIT")).count();
        assertThat(sites).isGreaterThanOrEqualTo(5);
        assertThat(units).isGreaterThanOrEqualTo(15);
    }
}
