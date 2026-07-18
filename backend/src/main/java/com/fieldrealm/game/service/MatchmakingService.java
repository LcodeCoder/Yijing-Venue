package com.fieldrealm.game.service;

import com.fieldrealm.game.domain.GameState;
import com.fieldrealm.game.domain.UserAccount;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MatchmakingService {
    private record QueueEntry(String userId, String matchId, int boardSize) { }
    private final Map<Integer, QueueEntry> queues = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> statuses = new ConcurrentHashMap<>();
    private final GameService games;
    private final AuthService auth;

    public MatchmakingService(GameService games, AuthService auth) { this.games = games; this.auth = auth; }

    public synchronized Map<String, Object> queue(String authorization, int requestedSize) {
        UserAccount user = auth.require(authorization);
        auth.ensureMatchAllowed(user);
        int size = requestedSize == 4 || requestedSize == 5 ? requestedSize : 3;
        Map<String, Object> current = statuses.get(user.getId());
        if (current != null && !"CANCELLED".equals(current.get("status"))) return current;
        QueueEntry waiting = queues.get(size);
        if (waiting != null && !waiting.userId().equals(user.getId())) {
            GameState match = games.join(waiting.matchId(), user.getDisplayName(), user.getId());
            Map<String, Object> hostStatus = result("MATCHED", match.getId(), "p1", size);
            Map<String, Object> guestStatus = result("MATCHED", match.getId(), "p2", size);
            statuses.put(waiting.userId(), hostStatus); statuses.put(user.getId(), guestStatus); queues.remove(size);
            return guestStatus;
        }
        GameState match = games.create("PVP", user.getDisplayName(), size, user.getId(), true);
        queues.put(size, new QueueEntry(user.getId(), match.getId(), size));
        Map<String, Object> status = result("WAITING", match.getId(), "p1", size);
        statuses.put(user.getId(), status);
        return status;
    }

    public Map<String, Object> status(String authorization) {
        UserAccount user = auth.require(authorization);
        return statuses.getOrDefault(user.getId(), Map.of("status", "IDLE"));
    }

    public synchronized Map<String, Object> cancel(String authorization) {
        UserAccount user = auth.require(authorization);
        queues.entrySet().removeIf(entry -> entry.getValue().userId().equals(user.getId()));
        Map<String, Object> result = Map.of("status", "CANCELLED"); statuses.put(user.getId(), result); return result;
    }

    private Map<String, Object> result(String status, String matchId, String playerId, int size) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", status); map.put("matchId", matchId); map.put("playerId", playerId); map.put("boardSize", size); map.put("ranked", true);
        return map;
    }
}
