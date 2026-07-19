package com.fieldrealm.game.service;

import com.fieldrealm.game.domain.CardDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CardCatalogServiceTest {
    @Test
    void starterDeckMeetsConstructionRules() {
        CardCatalogService catalog = new CardCatalogService();
        List<String> deck = catalog.starterDeck();
        assertThat(deck).hasSize(CardCatalogService.DECK_SIZE);
        long sites = deck.stream().map(catalog::require).map(CardDefinition::type).filter(t -> t.name().equals("SITE")).count();
        long units = deck.stream().map(catalog::require).map(CardDefinition::type).filter(t -> t.name().equals("UNIT")).count();
        long ssr = deck.stream().map(catalog::require).filter(c -> c.rarity().name().equals("SSR")).count();
        long maxCopies = deck.stream().collect(Collectors.groupingBy(id -> id, Collectors.counting()))
                .values().stream().mapToLong(Long::longValue).max().orElse(0);
        assertThat(sites).isGreaterThanOrEqualTo(CardCatalogService.MIN_SITES);
        assertThat(units).isGreaterThanOrEqualTo(CardCatalogService.MIN_UNITS);
        assertThat(ssr).isLessThanOrEqualTo(CardCatalogService.MAX_SSR);
        assertThat(maxCopies).isLessThanOrEqualTo(CardCatalogService.MAX_COPIES);
    }
}
