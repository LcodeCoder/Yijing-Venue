package com.fieldrealm.game.controller;

import com.fieldrealm.game.domain.GameState;
import com.fieldrealm.game.domain.UserAccount;
import com.fieldrealm.game.dto.*;
import com.fieldrealm.game.service.AuthService;
import com.fieldrealm.game.service.GameService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/matches")
public class MatchController {
    private final GameService games;
    private final AuthService auth;
    public MatchController(GameService games, AuthService auth) { this.games = games; this.auth = auth; }

    @PostMapping
    public GameState create(@RequestBody(required = false) CreateMatchRequest request,
                            @RequestHeader(value = "Authorization", required = false) String authorization) {
        UserAccount user = auth.optional(authorization);
        String mode = request == null || request.mode() == null ? "AI" : request.mode();
        String name = request == null ? null : request.playerName();
        int boardSize = request == null || request.boardSize() == null ? 3 : request.boardSize();
        boolean ranked = request != null && Boolean.TRUE.equals(request.ranked());
        if (ranked && user == null) throw new IllegalArgumentException("排位赛需要先登录");
        if (user != null && (name == null || name.isBlank())) name = user.getDisplayName();
        return games.create(mode, name, boardSize, user == null ? null : user.getId(), ranked);
    }

    @PostMapping("/{id}/join")
    public GameState join(@PathVariable String id, @RequestBody(required = false) JoinMatchRequest request,
                          @RequestHeader(value = "Authorization", required = false) String authorization) {
        UserAccount user = auth.optional(authorization);
        String name = request == null ? null : request.playerName();
        if (user != null && (name == null || name.isBlank())) name = user.getDisplayName();
        return games.join(id, name, user == null ? null : user.getId());
    }

    @GetMapping("/{id}") public GameState get(@PathVariable String id) { return games.get(id); }
    @PostMapping("/{id}/cards") public GameState playCard(@PathVariable String id, @Valid @RequestBody PlayCardRequest request) { return games.playCard(id, request); }
    @PostMapping("/{id}/contest") public GameState contest(@PathVariable String id, @Valid @RequestBody PlayerActionRequest request) { return games.enterContest(id, request.playerId()); }
    @PostMapping("/{id}/attacks") public GameState attack(@PathVariable String id, @Valid @RequestBody AttackRequest request) { return games.attack(id, request); }
    @PostMapping("/{id}/end-turn") public GameState endTurn(@PathVariable String id, @Valid @RequestBody PlayerActionRequest request) { return games.endTurn(id, request.playerId()); }
    @PostMapping("/{id}/units/retreat") public GameState retreatUnit(@PathVariable String id, @Valid @RequestBody UnitActionRequest request) { return games.retreatUnit(id, request.playerId(), request.unitId()); }
    @PostMapping("/{id}/discard") public GameState discard(@PathVariable String id, @Valid @RequestBody Map<String, String> request) { return games.discard(id, request.get("playerId"), request.get("cardId")); }
}
