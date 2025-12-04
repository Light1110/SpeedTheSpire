package swapthespire.controller;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import ludicrousspeed.Controller;
import ludicrousspeed.simulator.commands.Command;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Runs command lists streamed in from the external controller socket.
 *
 * <p>This mirrors the behaviour of scumthespire's CommandRunnerController but without relying on
 * BattleAiMod static state.</p>
 */
public class SocketCommandController implements Controller {
    private volatile boolean isDone = true;
    private volatile boolean activeComplete = true;

    private List<Command> activeCommands = Collections.emptyList();
    private Iterator<Command> activeIterator = activeCommands.iterator();

    private List<Command> queuedCommands = null;
    private boolean queuedComplete = true;

    public synchronized void replaceCommands(List<Command> commands, boolean isComplete) {
        if (commands == null) {
            this.activeCommands = Collections.emptyList();
            this.activeIterator = this.activeCommands.iterator();
            this.activeComplete = true;
            this.isDone = true;
            return;
        }

        this.activeCommands = commands;
        this.activeIterator = commands.iterator();
        this.activeComplete = isComplete;
        this.isDone = false;
        this.queuedCommands = null;
        this.queuedComplete = true;
    }

    public synchronized void queueCommands(List<Command> commands, boolean wouldComplete) {
        this.queuedCommands = commands;
        this.queuedComplete = wouldComplete;
    }

    @Override
    public void step() {
        Command commandToRun = null;
        synchronized (this) {
            if (isDone) {
                return;
            }

            // Switch to queued commands if active list is exhausted.
            if (!activeIterator.hasNext()) {
                if (queuedCommands != null) {
                    activeCommands = queuedCommands;
                    activeIterator = queuedCommands.iterator();
                    activeComplete = queuedComplete;
                    queuedCommands = null;
                } else if (activeComplete) {
                    isDone = true;
                    return;
                } else {
                    return;
                }
            }

            if (activeIterator.hasNext()) {
                commandToRun = activeIterator.next();
            } else if (activeComplete) {
                isDone = true;
            }
        }

        if (commandToRun != null) {
            commandToRun.execute();
            if (AbstractDungeon.player != null && AbstractDungeon.player.hand != null) {
                AbstractDungeon.player.hand.refreshHandLayout();
            }
        } else {
            synchronized (this) {
                if (!activeIterator.hasNext() && activeComplete) {
                    isDone = true;
                }
            }
        }
    }

    @Override
    public boolean isDone() {
        return isDone;
    }
}


