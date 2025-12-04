package swapthespire;

import communicationmod.CommunicationMod;
import communicationmod.CommunicationModStateReceiverI;
import swapthespire.networking.ExternalControlSocket;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class InJavaCommunicationController implements CommunicationModStateReceiverI{
    private static final Logger logger = LogManager.getLogger(InJavaCommunicationController.class.getName());
    private final ExternalControlSocket socket;

    public InJavaCommunicationController(ExternalControlSocket socket){
        this.socket = socket;
        logger.debug("Initializing CommunicationMod state bridge");
        CommunicationMod.subscribeToGameStates(this);
    }

    @Override
    public void receiveGameState(String gameState) {
        // Filter out states when all monsters are dead in combat
        // This prevents sending states with only "end" command available, which causes errors
        if (shouldFilterCombatState()) {
            logger.debug("Filtering combat state: all monsters are dead");
            return;
        }
        
        String mode = SwapTheSpire.allowCommunicationMod() ? "communication" : "ludicrous";
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
