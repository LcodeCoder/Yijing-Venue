package com.fieldrealm.game.controller;

import com.fieldrealm.game.domain.CardDefinition;
import com.fieldrealm.game.domain.UserAccount;
import com.fieldrealm.game.service.AuthService;
import com.fieldrealm.game.service.CardCatalogService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class MetaController {
    private final CardCatalogService catalog;
    private final AuthService auth;
    public MetaController(CardCatalogService catalog, AuthService auth) { this.catalog = catalog; this.auth = auth; }

    @GetMapping("/cards")
    public List<CardDefinition> cards() { return catalog.all(); }

    @GetMapping("/profile")
    public Map<String, Object> profile(@RequestHeader(value = "Authorization", required = false) String authorization) {
        UserAccount user = auth.optional(authorization);
        if (user != null) {
            Map<String, Object> profile = new LinkedHashMap<>(auth.publicView(user));
            profile.put("level", Math.max(1, user.getGames() / 5 + 1));
            profile.put("collection", catalog.all().size()); profile.put("collectionTotal", catalog.all().size());
            profile.put("favorite", "\u955c\u6f6e\u63a7\u573a"); profile.put("season", "\u88c2\u9699\u56de\u54cd");
            return profile;
        }
        return Map.ofEntries(
                Map.entry("id", "guest"), Map.entry("name", "\u6e38\u5ba2\u6267\u68cb\u8005"), Map.entry("title", "\u672a\u767b\u5f55"),
                Map.entry("level", 1), Map.entry("rank", "\u9752\u94dc"), Map.entry("rating", 1200),
                Map.entry("wins", 0), Map.entry("games", 0), Map.entry("collection", catalog.all().size()),
                Map.entry("collectionTotal", catalog.all().size()), Map.entry("favorite", "\u5f85\u53d1\u73b0"),
                Map.entry("season", "\u96fe\u6d77\u7eaa\u5143"), Map.entry("role", "GUEST"), Map.entry("avatar", "\u6e38")
        );
    }

    @GetMapping("/rankings")
    public List<Map<String, Object>> rankings() { return auth.rankings(); }

    @GetMapping("/decks/starter")
    public Map<String, Object> starterDeck() {
        return Map.of("id", "starter", "name", "\u4e94\u57df\u521d\u9635", "cards", catalog.starterDeck(), "valid", true);
    }
}
