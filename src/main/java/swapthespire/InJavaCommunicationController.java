package swapthespire;

import communicationmod.CommunicationMod;
import swapthespire.networking.ExternalControlSocket;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.actions.GameActionManager;

import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.monsters.AbstractMonster.Intent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class InJavaCommunicationController {
    private static final Logger logger = LogManager.getLogger(InJavaCommunicationController.class.getName());
    private final ExternalControlSocket socket;
    
    // For deduplication
    private String lastGameState = null;
    private long lastSendTime = 0;
    private static final long DEDUPE_INTERVAL_MS = 200; // 200ms deduplication window

    public static InJavaCommunicationController instance;

    public InJavaCommunicationController(ExternalControlSocket socket){
        this.socket = socket;
        instance = this;
        logger.debug("Initializing CommunicationMod state bridge");
        // CommunicationMod.subscribeToGameStates(this); // Not available in origin
    }

    public void receiveGameState(String gameState) {
        // Filter out states when all monsters are dead in combat
        // This prevents sending states with only "end" command available, which causes errors
        if (shouldFilterCombatState()) {
            logger.debug("Filtering combat state: all monsters are dead");
            return;
        }

        // Use stricter stability checks similar to CommunicationMod
        if (!isStateStable()) {
            logger.debug("Filtering unstable game state");
            return;
        }
        
        // Deduplicate identical states sent within a short window ONLY when game is over (in_game: false)
        // This fixes the double state send issue at game end
        long now = System.currentTimeMillis();
        boolean isNotInGame = gameState.contains("\"in_game\":false");
        
        if (isNotInGame && gameState.equals(lastGameState) && (now - lastSendTime < DEDUPE_INTERVAL_MS)) {
            logger.info("Filtering duplicate state payload");
            return;
        }
        
        lastGameState = gameState;
        lastSendTime = now;
        
        String mode = SwapTheSpire.allowCommunicationMod() ? "communication" : "ludicrous";
        // logger.info("Sending State Payload: " + gameState);
        socket.sendGameState(mode, gameState);
    }

    private boolean isStateStable() {
        // Basic stability check: no actions in queue
        if (AbstractDungeon.actionManager != null) {
            boolean hasActions = !AbstractDungeon.actionManager.actions.isEmpty();
            boolean hasCardQueue = !AbstractDungeon.actionManager.cardQueue.isEmpty();
            boolean isWaiting = AbstractDungeon.actionManager.phase == GameActionManager.Phase.WAITING_ON_USER;
            
            // Generally, if actions are pending, or we are not waiting for user input, we shouldn't send state.
            if (hasActions || hasCardQueue || !isWaiting) {
                return false;
            }
        }

        // Deep check for monster intent stability
        // This ensures applyPowers() has been executed and intents are finalized
        if (AbstractDungeon.currMapNode != null && AbstractDungeon.getCurrRoom() != null && AbstractDungeon.getCurrRoom().monsters != null) {
            for (AbstractMonster m : AbstractDungeon.getCurrRoom().monsters.monsters) {
                if (m.isDead || m.isEscaping) continue;

                // Check for DEBUG intent (not fully initialized)
                if (m.intent == Intent.DEBUG) {
                    return false;
                }

                // Check if attack damage has been calculated (applyPowers executed)
                // If intent is an attack but damage is negative, it means calculation is pending
                boolean isAttack = (
                    m.intent == Intent.ATTACK ||
                    m.intent == Intent.ATTACK_BUFF ||
                    m.intent == Intent.ATTACK_DEBUFF ||
                    m.intent == Intent.ATTACK_DEFEND
                );

                if (isAttack && m.getIntentDmg() < 0) {
                     // Force update logic if LudicrousSpeed skipped it
                     m.applyPowers();
                     
                     // Even if we fixed it, the current gameState string is stale (contains -1).
                     // We must return false to filter this message.
                     // The game loop/mod should generate a new message with correct values soon.
                    //  logger.info("Fixed intent damage via applyPowers. Filtering stale state.");
                     return false;
                }
            }
        }
        return true;
    }

    /**
     * Check if we should filter out this combat state.
     * Returns true if we're in combat and all monsters are basically dead,
     * which means the battle is ending and we shouldn't send states.
     */
    private boolean shouldFilterCombatState() {
        // Only filter during combat phase
        if (AbstractDungeon.currMapNode == null 
                || AbstractDungeon.getCurrRoom() == null
                || AbstractDungeon.getCurrRoom().phase != AbstractRoom.RoomPhase.COMBAT) {
            return false;
        }

        // Filter if all monsters are basically dead
        if (AbstractDungeon.getCurrRoom().monsters != null 
                && AbstractDungeon.getCurrRoom().monsters.areMonstersBasicallyDead()) {
            return true;
        }

        // Also filter if battle is registered as over
        if (AbstractDungeon.getCurrRoom().isBattleOver) {
            return true;
        }

        return false;
    }
}
