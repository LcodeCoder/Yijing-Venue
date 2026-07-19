package com.fieldrealm.game.service;

import com.fieldrealm.game.domain.CardDefinition;
import com.fieldrealm.game.domain.CardType;
import com.fieldrealm.game.domain.GamePhase;
import com.fieldrealm.game.domain.GameState;
import com.fieldrealm.game.domain.SiteState;
import com.fieldrealm.game.domain.UnitInstance;
import com.fieldrealm.game.dto.AttackRequest;
import com.fieldrealm.game.dto.PlayCardRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class GameServiceTest {
    private CardCatalogService catalog;
    private GameService games;

    @BeforeEach
    void setUp() {
        catalog = new CardCatalogService();
        games = new GameService(catalog, mock(SimpMessagingTemplate.class));
    }

    @Test
    void demoMatchStartsWithNineSitesOneCoreAndAPlayableOpeningSite() {
        for (int i = 0; i < 20; i++) {
            GameState game = games.create("AI", "Tester");
            assertThat(game.getPlayers().get(0).getEnergy()).isEqualTo(3);
            assertThat(game.getPlayers().get(0).getHand()).hasSize(4)
                    .anySatisfy(id -> assertThat(catalog.require(id).type()).isEqualTo(CardType.SITE));
            assertThat(game.getSites()).hasSize(9);
            assertThat(game.getSites().stream().filter(SiteState::isCore)).hasSize(1);
            assertThat(game.getSites().get(8).isCore()).isTrue();
        }
    }

    @Test
    void boardSizeControlsEnergyGrantedAtMatchStartAndEveryTurn() {
        GameState fourByFour = games.create("AI", "Tester", 4, null, false);
        assertThat(fourByFour.getPlayers().get(0).getEnergy()).isEqualTo(4);

        fourByFour.setInitialContestResolved(true);
        fourByFour.setPhase(GamePhase.CONTEST);
        fourByFour.setMode("PVP");
        games.endTurn(fourByFour.getId(), "p1");
        assertThat(fourByFour.getPlayers().get(1).getEnergy()).isEqualTo(4);

        GameState fiveByFive = games.create("AI", "Tester", 5, null, false);
        assertThat(fiveByFive.getPlayers().get(0).getEnergy()).isEqualTo(5);
    }

    @Test
    void initiativeDiceRollsOnlyAfterBothPlayersFinishDeployment() {
        for (int i = 0; i < 40; i++) {
            GameState game = games.create("AI", "Tester");
            assertThat(game.getPlayerRoll()).isZero();
            assertThat(game.getOpponentRoll()).isZero();
            assertThat(game.getContestStarterIndex()).isEqualTo(-1);

            deployOpeningSite(game, 0);
            games.endTurn(game.getId(), "p1");

            assertThat(game.getPlayerRoll()).isBetween(1, 6);
            assertThat(game.getOpponentRoll()).isBetween(1, 6);
            assertThat(game.getPlayerRoll()).isNotEqualTo(game.getOpponentRoll());
            assertThat(game.getContestStarterIndex())
                    .isEqualTo(game.getPlayerRoll() > game.getOpponentRoll() ? 0 : 1);
        }
    }

    @Test
    void contestIsBlockedUntilBothPlayersHaveDeployedASite() {
        GameState game = games.create("AI", "Tester");
        deployOpeningSite(game, 0);

        assertThatThrownBy(() -> games.enterContest(game.getId(), "p1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("\u53cc\u65b9\u90fd\u5e03\u7f6e\u81f3\u5c11\u4e00\u5f20\u573a\u5730");
    }

    @Test
    void completingInitialDeploymentMakesAiDeployBeforeFirstContest() {
        GameState game = games.create("AI", "Tester");
        deployOpeningSite(game, 0);

        games.endTurn(game.getId(), "p1");

        assertThat(game.isInitialContestResolved()).isTrue();
        assertThat(game.getSites()).anyMatch(site -> "p2".equals(site.getOwnerId()));
        assertThat(game.getActivePlayerIndex()).isEqualTo(0);
        if (game.getContestStarterIndex() == 0) {
            assertThat(game.getPhase()).isEqualTo(GamePhase.CONTEST);
        } else {
            assertThat(game.getPhase()).isIn(GamePhase.DEPLOY, GamePhase.FINISHED);
        }
    }

    @Test
    void siteCanBeDeployedAtCentralCore() {
        GameState game = games.create("AI", "Tester");
        String siteId = openingSiteId(game);
        CardDefinition site = catalog.require(siteId);

        GameState after = games.playCard(game.getId(), new PlayCardRequest("p1", siteId, 8, null));
        assertThat(after.getSites().get(8).getOwnerId()).isEqualTo("p1");
        assertThat(after.getSites().get(8).isCore()).isTrue();
        assertThat(after.getPlayers().get(0).getEnergy()).isEqualTo(3 - site.cost());
        assertThat(after.getPhase()).isEqualTo(GamePhase.DEPLOY);
    }

    @Test
    void normalUnitCannotAttackAnOuterRealmAtDistanceTwo() {
        GameState game = games.create("AI", "Tester");
        UnitInstance unit = placeUnit(game, 0, "p1", "unit-sentinel", "normal-1");
        occupy(game, 2, "p2");
        game.setPhase(GamePhase.CONTEST);

        assertThatThrownBy(() -> games.attack(game.getId(), new AttackRequest("p1", unit.getInstanceId(), 2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("\u653b\u51fb\u8ddd\u79bb\u53ea\u67091");
        assertThat(unit.isExhausted()).isFalse();
    }

    @Test
    void scoutCanAttackAnOuterRealmAtDistanceTwo() {
        GameState game = games.create("AI", "Tester");
        UnitInstance scout = placeUnit(game, 0, "p1", "unit-scout", "scout-1");
        occupy(game, 2, "p2");
        game.setPhase(GamePhase.CONTEST);

        games.attack(game.getId(), new AttackRequest("p1", scout.getInstanceId(), 2));

        assertThat(scout.getAttackRange()).isEqualTo(2);
        assertThat(scout.isExhausted()).isTrue();
        assertThat(game.getSites().get(2).getOwnerId()).isEqualTo("p1");
    }

    @Test
    void centralCoreIsAdjacentToEveryOuterRealm() {
        GameState game = games.create("AI", "Tester");
        UnitInstance unit = placeUnit(game, 8, "p1", "unit-sentinel", "center-1");
        occupy(game, 4, "p2");
        game.setPhase(GamePhase.CONTEST);

        games.attack(game.getId(), new AttackRequest("p1", unit.getInstanceId(), 4));

        assertThat(unit.isExhausted()).isTrue();
        assertThat(game.getSites().get(4).getOwnerId()).isEqualTo("p1");
    }

    @Test
    void deployedUnitCanRetreatToDiscardDuringDeployPhase() {
        GameState game = games.create("AI", "Tester");
        UnitInstance unit = placeUnit(game, 0, "p1", "unit-warden", "retreat-1");

        games.retreatUnit(game.getId(), "p1", unit.getInstanceId());

        assertThat(game.getSites().get(0).getUnits()).isEmpty();
        assertThat(game.getPlayers().get(0).getDiscard()).contains("unit-warden");
    }

    @Test
    void aiDoesNotAttackOutsideItsUnitsRange() {
        GameState game = games.create("AI", "Tester");
        UnitInstance aiUnit = placeUnit(game, 0, "p2", "unit-sentinel", "ai-range-1");
        aiUnit.setPower(20);
        occupy(game, 2, "p1");
        game.getPlayers().get(1).getHand().clear();
        game.getPlayers().get(1).getDeck().clear();
        game.setInitialContestResolved(true);

        games.endTurn(game.getId(), "p1");

        assertThat(game.getSites().get(2).getOwnerId()).isEqualTo("p1");
    }

    @Test
    void phasesUseSixtySecondDeadline() {
        GameState game = games.create("AI", "Tester");

        assertThat(GameService.PHASE_DURATION_SECONDS).isEqualTo(60);
        assertThat(game.getPhaseDurationSeconds()).isEqualTo(60);
    }

    @Test
    void boardSizeControlsTheMaximumRoundCount() {
        GameState game = games.create("AI", "Tester", 4, null, false);
        game.setInitialContestResolved(true);
        game.setPhase(GamePhase.CONTEST);
        game.setMode("PVP");
        game.setTurnNumber(24); // 12 rounds * 2 player turns

        games.endTurn(game.getId(), "p1");

        assertThat(game.getPhase()).isEqualTo(GamePhase.FINISHED);
        assertThat(game.getVictoryType()).isEqualTo("\u79ef\u5206\u5e73\u5c40");
        assertThat(game.getStatusText()).contains("12\u56de\u5408");
    }

    @Test
    void playerCanStillPlayCardsBeforeEndingAnOverflowingTurn() {
        GameState game = games.create("AI", "Tester");
        String siteId = "site-verdant";
        game.getPlayers().get(0).getHand().clear();
        for (int i = 0; i < 8; i++) game.getPlayers().get(0).getHand().add(siteId);
        game.getPlayers().get(0).setEnergy(99);

        games.playCard(game.getId(), new PlayCardRequest("p1", siteId, 0, null));

        assertThat(game.getSites().get(0).getOwnerId()).isEqualTo("p1");
        assertThat(game.getPlayers().get(0).getHand()).hasSize(7);
    }

    @Test
    void overflowingHandIsAutoDiscardedWhenTurnEnds() {
        GameState game = games.create("AI", "Tester");
        String cardId = game.getPlayers().get(0).getHand().get(0);
        while (game.getPlayers().get(0).getHand().size() < 8) {
            game.getPlayers().get(0).getHand().add(cardId);
        }
        game.setInitialContestResolved(true);
        game.setPhase(GamePhase.CONTEST);
        game.setMode("PVP");

        games.endTurn(game.getId(), "p1");

        assertThat(game.getPlayers().get(0).getHand()).hasSize(7);
        assertThat(game.getPlayers().get(0).getDiscard()).contains(cardId);
    }

    @Test
    void overflowingHandIsAutoDiscardedDownToSevenCards() {
        GameState game = games.create("AI", "Tester");
        String cardId = game.getPlayers().get(0).getHand().get(0);
        while (game.getPlayers().get(0).getHand().size() < 10) {
            game.getPlayers().get(0).getHand().add(cardId);
        }
        game.setInitialContestResolved(true);
        game.setPhase(GamePhase.CONTEST);
        game.setMode("PVP");

        games.endTurn(game.getId(), "p1");

        assertThat(game.getPlayers().get(0).getHand()).hasSize(7);
        assertThat(game.getPlayers().get(0).getDiscard()).hasSize(3);
    }

    @Test
    void playerCanDiscardDuringContestToResolveEndTurnHandLimit() {
        GameState game = games.create("AI", "Tester");
        String cardId = game.getPlayers().get(0).getHand().get(0);
        game.setPhase(GamePhase.CONTEST);

        games.discard(game.getId(), "p1", cardId);

        assertThat(game.getPlayers().get(0).getHand()).doesNotContain(cardId);
        assertThat(game.getPlayers().get(0).getDiscard()).contains(cardId);
    }

    private String openingSiteId(GameState game) {
        return game.getPlayers().get(0).getHand().stream()
                .filter(id -> catalog.require(id).type() == CardType.SITE)
                .findFirst().orElseThrow();
    }

    private void deployOpeningSite(GameState game, int siteIndex) {
        String siteId = openingSiteId(game);
        games.playCard(game.getId(), new PlayCardRequest("p1", siteId, siteIndex, null));
    }

    private UnitInstance placeUnit(GameState game, int siteIndex, String ownerId, String cardId, String instanceId) {
        SiteState site = game.getSites().get(siteIndex);
        site.setOwnerId(ownerId);
        UnitInstance unit = new UnitInstance(instanceId, catalog.require(cardId), ownerId);
        site.getUnits().add(unit);
        return unit;
    }

    private void occupy(GameState game, int siteIndex, String ownerId) {
        SiteState site = game.getSites().get(siteIndex);
        site.setOwnerId(ownerId);
        site.setBaseGuard(0);
    }
}
