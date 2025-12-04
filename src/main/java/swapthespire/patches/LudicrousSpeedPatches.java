
package swapthespire.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import com.megacrit.cardcrawl.screens.DeathScreen;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.actions.GameActionManager;

import communicationmod.EndOfTurnAction;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import communicationmod.CommunicationMod;
import swapthespire.SwapTheSpire;

import ludicrousspeed.simulator.patches.ServerStartupPatches;
import ludicrousspeed.LudicrousSpeedMod;

/* 
 * Patches against stuff LudicrousSpeed stuff does that I don't want it to do
 */

public class LudicrousSpeedPatches {
    private static final Logger logger = LogManager.getLogger(  LudicrousSpeedPatches.class.getName());

    @SpirePatch(
            clz= ServerStartupPatches.GameStartupPatch.class,
            paramtypez = {CardCrawlGame.class},
            method="afterStart"
    )
    public static class PreventLudicrousFromFixingRandomStateAfterStart {
        public static SpireReturn Prefix(CardCrawlGame game) {
            logger.info("SpeedTheSpire preventing LudicrousState.patches.ServerStartupPatches.GameStartupPatch.afterStart");
            return SpireReturn.Return(null);
        }

    }

    /**
     * Fix for "how did we get here?" issue.
     * 
     * In plaidMode, GameActionManager.callEndOfTurnActions() should be completely
     * bypassed since ActionSimulator handles end-of-turn logic. LudicrousSpeed's
     * patch only warns but continues execution, which can cause state inconsistencies
     * and hanging. This patch completely skips the call to prevent issues.
     */
    @SpirePatch(
            clz = GameActionManager.class,
            method = "callEndOfTurnActions",
            paramtypez = {}
    )
    public static class SkipCallEndOfTurnActionsInPlaidMode {
        public static SpireReturn<Void> Prefix() {
            if (LudicrousSpeedMod.plaidMode) {
                // In plaidMode, ActionSimulator handles end-of-turn actions,
                // so we should completely skip the normal game flow here
                // This prevents the "how did we get here?" warning and potential hangs
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }
}