package swapthespire;

import communicationmod.CommunicationMod;
import swapthespire.networking.ExternalControlSocket;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

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
