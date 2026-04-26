# DS25-ExplodingKittens
A distributed base implementation of the popular card game Exploding Kittens.
Exploding Kittens is a card game for 2 - 4 players. On each turn, a player either plays action cards from their hand or draws from the deck. 
Draw an Exploding Kitten and you're out unless you have a Defuse card to save yourself.
The last player standing wins.

## Cards available in this version
- 💣 Exploding Kitten: You explode Unless you have a Defuse.
- 🛡️ Defuse: Neutralizes an Exploding Kitten and puts it back in the deck.
- ⏭️ Skip: End your turn without drawing.
- ⚔️ Attack: End your turn and force the next player to take two turns.
- 🔀 Shuffle: Shuffle the draw pile.
- 👁️ See the Future: Peek at the top 3 cards of the deck.
- 🐱 Cat Cards: Play as pairs to steal a random card from another player.

## Team members
- Alessandra Versari - alessandra.versari2@studio.unibo.it
- Margherita Balzoni - margherita.balzoni@studio.unibo.it


## Requirements
- Java 21 or higher
- 2 to 4 players connected on the same network

## How to start the game
The game uses a server/client architecture. One player acts as the host and runs the server,
the others connect as clients. A backup server is also available for fault tolerance.

### Step 1 — Download
Download and extract the latest release zip from the  Releases page.

### Step 2 — Start the Server (host only)
One player must launch the server first.

**Windows:**
Double-click `ServerMain.bat`

**Mac/Linux:**
```bash
chmod +x ServerMain.sh   
./ServerMain.sh
```

### Step 3 — Start the Backup Server (host only)
On a second terminal  launch the backup server.

**Windows:**
Double-click `BackupServerMain.bat`

**Mac/Linux:**
```bash
./BackupServerMain.sh
```

### Step 4 — Each player launches the Client
Every player (2 to 4) must run the client on their own machine.

**Windows:**
Double-click `ClientMain.bat`

**Mac/Linux:**
```bash
./ClientMain.sh
```

Remember to:
- Always start the server **before** the clients.
- All players must be on the **same network**.
- Do **not** move the `.jar` file out of the folder — the scripts must stay next to it.