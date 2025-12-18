# SpeedTheSpire

SpeedTheSpire is a Slay the Spire mod designed to enable external programs to control the game via TCP Socket communication. It facilitates automated game control and accelerated gameplay by integrating LudicrousSpeed and CommunicationMod, while disabling display rendering for maximum performance.

Based on [SpeedTheSpire by enjoythecode](https://github.com/enjoythecode/SpeedTheSpire).

## Requirements

Ensure you have the following mods installed:

- **ModTheSpire**: [Steam Workshop](https://steamcommunity.com/sharedfiles/filedetails/?id=1605060445)
- **BaseMod**: [Steam Workshop](https://steamcommunity.com/workshop/filedetails/?id=1605833019)
- **CommunicationMod**: [Steam Workshop](https://steamcommunity.com/workshop/filedetails/?id=2131373661)
- **LudicrousSpeed**: [GitHub](https://github.com/boardengineer/LudicrousSpeed)
- **SaveStateMod**: [Steam Workshop](https://steamcommunity.com/sharedfiles/filedetails/?id=2489671162)

**Important Note**: This mod currently requires the **latest GitHub version** of LudicrousSpeed. The Steam Workshop version is not compatible.

## Usage

### 1. Start External Program
External programs should connect to the game via a TCP socket. 
- **Default Address**: `127.0.0.1:5126`
- **Communication Protocol**: Follows the CommunicationMod standard.

For a Python example, refer to [spirecomm](https://github.com/Light1110/spirecomm).

### 2. Launch the Game
Launch the game using ModTheSpire with the required mods enabled.

**Example Command**:
```bash
cd /path/to/game
./jre/bin/java -jar ModTheSpire.jar --mods basemod,stslib,CommunicationMod,SaveStateMod,LudicrousSpeed,SwapTheSpire --skip-intro
```

## Configuration

You can customize the socket connection settings using JVM parameters:

- **Socket Address**: `-DexternalControlHost=<ADDRESS>` (Default: `127.0.0.1`)
- **Socket Port**: `-DexternalControlPort=<PORT>` (Default: `5126`)

**Example with Custom Settings**:
```bash
./jre/bin/java -DexternalControlHost=127.0.0.1 -DexternalControlPort=5126 -jar ModTheSpire.jar ...
```
