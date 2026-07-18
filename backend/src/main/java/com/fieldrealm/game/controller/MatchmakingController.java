package com.fieldrealm.game.controller;

import com.fieldrealm.game.dto.MatchmakingRequest;
import com.fieldrealm.game.service.MatchmakingService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/matchmaking")
public class MatchmakingController {
    private final MatchmakingService matchmaking;
    public MatchmakingController(MatchmakingService matchmaking) { this.matchmaking = matchmaking; }

    @PostMapping("/queue")
    public Map<String, Object> queue(@RequestHeader(value = "Authorization", required = false) String authorization,
                                     @RequestBody(required = false) MatchmakingRequest request) {
        return matchmaking.queue(authorization, request == null || request.boardSize() == null ? 3 : request.boardSize());
    }
    @GetMapping("/status") public Map<String, Object> status(@RequestHeader(value = "Authorization", required = false) String authorization) { return matchmaking.status(authorization); }
    @DeleteMapping("/queue") public Map<String, Object> cancel(@RequestHeader(value = "Authorization", required = false) String authorization) { return matchmaking.cancel(authorization); }
}
