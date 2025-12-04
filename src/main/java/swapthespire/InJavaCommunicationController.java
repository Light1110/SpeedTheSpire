package swapthespire;

import communicationmod.CommunicationMod;
import communicationmod.CommunicationModStateReceiverI;
import swapthespire.networking.ExternalControlSocket;

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
        String mode = SwapTheSpire.allowCommunicationMod() ? "communication" : "ludicrous";
        socket.sendGameState(mode, gameState);
    }
}
