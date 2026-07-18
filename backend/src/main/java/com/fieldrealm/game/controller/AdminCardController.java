package com.fieldrealm.game.controller;

import com.fieldrealm.game.domain.CardDefinition;
import com.fieldrealm.game.service.AuthService;
import com.fieldrealm.game.service.CardCatalogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/cards")
public class AdminCardController {
    private final CardCatalogService cards;
    private final AuthService auth;
    public AdminCardController(CardCatalogService cards, AuthService auth) { this.cards = cards; this.auth = auth; }

    @GetMapping
    public List<CardDefinition> all(@RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.requireAdmin(authorization); return cards.all();
    }
    @PostMapping
    public CardDefinition create(@RequestHeader(value = "Authorization", required = false) String authorization,
                                 @RequestBody CardDefinition card) {
        auth.requireAdmin(authorization); return cards.upsert(card);
    }
    @PutMapping("/{id}")
    public CardDefinition update(@PathVariable String id,
                                 @RequestHeader(value = "Authorization", required = false) String authorization,
                                 @RequestBody CardDefinition card) {
        auth.requireAdmin(authorization);
        if (card == null) throw new IllegalArgumentException("卡牌数据不能为空");
        return cards.upsert(new CardDefinition(id, card.name(), card.type(), card.rarity(), card.cost(), card.power(), card.guard(), card.points(), card.effectCode(), card.effect(), card.flavor(), card.tags()));
    }
    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id, @RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.requireAdmin(authorization); cards.delete(id);
    }
}
