# SpeedTheSpire External Control Documentation

SpeedTheSpire uses a unified TCP socket to send game state to external programs and receive commands.

## Socket Protocol

- Default connection target: `127.0.0.1:5126`, can be overridden via JVM parameters
  `-DexternalControlHost=...` and `-DexternalControlPort=...`.
- Game → External Program: When CommunicationMod produces a state, it sends
  `{"type":"game_state","mode":"communication|ludicrous","payload":<JSON string>}`.
- External Program → Game:
  - `{"type":"comm_command","command":"start ironclad"}`: Forwards text commands to CommunicationMod.
  - `{"type":"ludi_commands","commands":[...],"complete":true,"append":false}`:
    Converts the command list into a `ludicrousspeed.simulator.commands.Command` list, which is
    executed sequentially by `SocketCommandController` during combat.

## Runtime Control Switching

- `SwapTheSpire` uses `InControl` to determine control. During non-combat phases, `LudicrousSpeedMod.controller` is set to null, and actual commands must be pushed by the external program in the form of `comm_command`.
- During combat phases, `CommunicationMod.sendGameState()` is called periodically to ensure the external program continuously receives state updates, and operations are sent via `ludi_commands` messages.

## Known Issues / TODO
- error when a game ends