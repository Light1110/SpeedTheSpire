package swapthespire.networking;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import communicationmod.CommunicationMod;
import ludicrousspeed.simulator.commands.CardCommand;
import ludicrousspeed.simulator.commands.CardRewardSelectCommand;
import ludicrousspeed.simulator.commands.Command;
import ludicrousspeed.simulator.commands.EndCommand;
import ludicrousspeed.simulator.commands.GridSelectCommand;
import ludicrousspeed.simulator.commands.GridSelectConfrimCommand;
import ludicrousspeed.simulator.commands.HandSelectCommand;
import ludicrousspeed.simulator.commands.HandSelectConfirmCommand;
import ludicrousspeed.simulator.commands.PotionCommand;
import swapthespire.controller.SocketCommandController;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import com.badlogic.gdx.Gdx;

/**
 * Single socket that handles both CommunicationMod text commands and Ludicrous command lists.
 *
 * <p>The protocol is JSON based. Messages we send:
 * <ul>
 *     <li>{"type":"game_state","mode":"communication|ludicrous","payload":<string>}</li>
 * </ul>
 * Messages we expect to receive:
 * <ul>
 *     <li>{"type":"comm_command","command":"start ironclad"}</li>
 *     <li>{"type":"ludi_commands","complete":true,"commands":[ ... ]}</li>
 * </ul>
 * </p>
 */
public class ExternalControlSocket {
    private static final Logger logger = LogManager.getLogger(ExternalControlSocket.class);
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 5126;

    private final SocketCommandController controller;
    private final String host;
    private final int port;

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    // Socket 命令转交到主线程执行，避免多线程访问游戏状态导致 NPE
    private final BlockingQueue<String> commandQueue = new LinkedBlockingQueue<>();
    private final Object commandQueueLock = new Object();

    public ExternalControlSocket(SocketCommandController controller) {
        this(controller, System.getProperty("externalControlHost", DEFAULT_HOST),
                Integer.parseInt(System.getProperty("externalControlPort", String.valueOf(DEFAULT_PORT))));
    }

    public ExternalControlSocket(SocketCommandController controller, String host, int port) {
        this.controller = controller;
        this.host = host;
        this.port = port;
        connect();
    }

    private void connect() {
        new Thread(() -> {
            while (true) {
                try {
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(host, port));
                    in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                    out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                    listen();
                } catch (IOException e) {
                    try {
                        if (socket != null) {
                            socket.close();
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    } finally {
                        socket = null;
                        in = null;
                        out = null;
                    }
                    sleep(2000);
                }
            }
        }, "ExternalControlSocket-connector").start();
    }

    private void listen() {
        try {
            while (socket != null && socket.isConnected()) {
                String payload = in.readUTF();
                handleIncoming(payload);
            }
        } catch (IOException e) {
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            } finally {
                socket = null;
                in = null;
                out = null;
            }
        }
    }

    private void handleIncoming(String payload) {
        try {
            JsonObject obj = JsonParser.parseString(payload).getAsJsonObject();
            String type = obj.get("type").getAsString();
            switch (type) {
                case "comm_command":
                    // 将命令投递到主线程执行
                    synchronized (commandQueueLock) {
                        commandQueue.offer(obj.get("command").getAsString());
                    }
                    if (Gdx.app != null) {
                        Gdx.app.postRunnable(() -> {
                            synchronized (commandQueueLock) {
                                while (!commandQueue.isEmpty()) {
                                    CommunicationMod.executeMessage(commandQueue.poll());
                                }
                            }
                        });
                    }
                    break;
                case "ludi_commands":
                    JsonArray array = obj.get("commands").getAsJsonArray();
                    boolean complete = !obj.has("complete") || obj.get("complete").getAsBoolean();
                    List<Command> commands = parseCommands(array);
                    if (obj.has("append") && obj.get("append").getAsBoolean()) {
                        controller.queueCommands(commands, complete);
                    } else {
                        controller.replaceCommands(commands, complete);
                    }
                    break;
                case "ping":
                    sendJson(obj);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            logger.error("Failed to handle incoming payload", e);
        }
    }

    public synchronized void sendGameState(String mode, String gameStateJson) {
        if (out == null) {
            return;
        }
        JsonObject wrapper = new JsonObject();
        wrapper.addProperty("type", "game_state");
        wrapper.addProperty("mode", mode);
        wrapper.addProperty("payload", gameStateJson);
        sendJson(wrapper);
    }

    private synchronized void sendJson(JsonObject object) {
        if (out == null) {
            return;
        }
        try {
            String serialized = object.toString();
            out.writeUTF(serialized);
            out.flush();
        } catch (IOException e) {
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            } finally {
                socket = null;
                in = null;
                out = null;
            }
        }
    }

    private static List<Command> parseCommands(JsonArray array) {
        List<Command> commands = new ArrayList<>();
        for (JsonElement element : array) {
            Command parsed = parseCommand(element);
            if (parsed != null) {
                commands.add(parsed);
            }
        }
        return commands;
    }

    private static Command parseCommand(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        JsonObject asObj = element.getAsJsonObject();
        if (!asObj.has("command") || asObj.get("command").isJsonNull()) {
            return null;
        }
        JsonObject obj = JsonParser.parseString(asObj.get("command").getAsString()).getAsJsonObject();
        String type = obj.get("type").getAsString();
        switch (type) {
            case "CARD":
                if (asObj.has("state")) {
                    return new CardCommand(obj.toString(), asObj.get("state").getAsString());
                }
                return new CardCommand(obj.toString());
            case "POTION":
                if (asObj.has("state")) {
                    return new PotionCommand(obj.toString(), asObj.get("state").getAsString());
                }
                return new PotionCommand(obj.toString());
            case "END":
                if (asObj.has("state")) {
                    return new EndCommand(obj.toString(), asObj.get("state").getAsString());
                }
                return new EndCommand(obj.toString());
            case "HAND_SELECT":
                if (asObj.has("state")) {
                    return new HandSelectCommand(obj.toString(), asObj.get("state").getAsString());
                }
                return new HandSelectCommand(obj.toString());
            case "HAND_SELECT_CONFIRM":
                if (asObj.has("state")) {
                    return new HandSelectConfirmCommand(asObj.get("state").getAsString());
                }
                return HandSelectConfirmCommand.INSTANCE;
            case "GRID_SELECT":
                return new GridSelectCommand(obj.toString());
            case "GRID_SELECT_CONFIRM":
                return GridSelectConfrimCommand.INSTANCE;
            case "CARD_REWARD_SELECT":
                return new CardRewardSelectCommand(obj.toString());
            default:
                return null;
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
        }
    }
}


