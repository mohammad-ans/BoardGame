from fastapi import FastAPI, WebSocket
from fastapi.middleware.cors import CORSMiddleware
import random
import asyncio
from db import session, User
app = FastAPI()

app.add_middleware(
    CORSMiddleware,
        allow_origins=["*"],
        allow_methods=["*"],
        allow_credentials=True,
        allow_headers=["*"]
)

TIMEOUT_SECONDS = 85

class ConnectionManager:
    def __init__(self):
        self.rooms: set[str] = set()
        self.list_conn: dict[str, WebSocket] = {}
        self.random_waiting: set[str] = set()
        self.room_players: dict[str, tuple] = dict()
        self.player_room: dict[str, str] = dict()
        self.player_local: dict[str, str] = dict()
        self.pending_disconnect: dict[str, asyncio.Task] = {}
        self.room_active: dict[str, bool] = {}

    async def add_connection(self, conn : WebSocket):
        await conn.accept()
        message = await conn.receive_json()
        user = message["username"]
        local_username = message["local"]
        self.player_local[user] = local_username
        self.list_conn[user] = conn
        return user

    def assign_room(self, user : str):
        room_code = self.create_room()
        self.room_players[room_code] = (user, None)
        self.player_room[user] = room_code
        return room_code

    async def join_room(self, user : str, room_code : str):
        room_code = room_code.strip().upper()
        if room_code not in self.room_players:
            return -1
        players = self.room_players[room_code]
        if not isinstance(players, tuple) or len(players) < 2 or players[1]:
            return -2
        self.room_players[room_code] = (players[0], user)
        self.player_room[user] = room_code

        local_name = self.player_local[user]
        await self.list_conn[players[0]].send_json({"type" : "username", "username": local_name})
        await self.list_conn[players[0]].send_json({"type" : "player_joined", "status" : "success", "is_initiator" : True})
        return 0, self.player_local[players[0]], room_code
    
    def random_util(self, user1 : str, user2 : str, room_code : str):
        self.room_players[room_code] = (user1, user2)
        self.player_room[user1] = room_code
        self.player_room[user2] = room_code

    async def remove_connection(self, user : str, room_code : str | None, lose : bool = True):
        try:
            if not room_code:
                if user in self.player_room.keys():
                    room_code = self.player_room[user]
                else:
                    return
            players = self.room_players[room_code]
            if lose:
                if players[0] == user and players[1] and players[1] in self.list_conn.keys():
                    await self.list_conn[players[1]].send_json({"type" : "move", "diceVal" : 0})
                elif players[0] and players[0] in self.list_conn.keys():
                    await self.list_conn[players[0]].send_json({"type" : "move", "diceVal" : 0})
            if players[0] in self.player_room:
                self.player_room.pop(players[0], "")
            if players[1] in self.player_room:
                self.player_room.pop(players[1], "")
            self.room_players.pop(room_code)
            self.rooms.discard(room_code)
            return 0
        
        except:
            pass

    async def wait_before_disconnect(self, user : str, room_code : str):
        self.list_conn.pop(user)
        if user in self.random_waiting:
            self.random_waiting.discard(user)
        if not room_code:
            if user in self.player_room.keys():
                room_code = self.player_room.get(user)
            else:
                return
        try:
            players = self.room_players.get(room_code)
            if players[0] == user and players[1] and players[1] in self.list_conn:
                await self.list_conn[players[1]].send_json({"type" : "move", "diceVal" : -3})
            elif players[0] and players[0] in self.list_conn.keys():
                await self.list_conn[players[0]].send_json({"type" : "move", "diceVal" : -3})
            await asyncio.sleep(45)
            await self.remove_connection(user, room_code)
        except asyncio.CancelledError:
            raise

    def create_room(self):
        chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890"
        random_name = ""
        for _ in range(8):
            random_name += chars[random.randint(0, len(chars) - 1)]
        if random_name in self.rooms:
            random_name = self.create_room()
        self.rooms.add(random_name)
        return random_name
    
    async def send_data(self, room_code : str, user : str, move):
        players = self.room_players[room_code]
        if user == players[0]:
            await self.list_conn[players[1]].send_json({"type" : "move", "diceVal" : move})
        else:
            await self.list_conn[players[0]].send_json({"type" : "move", "diceVal" : move})

    async def random_join(self, user : str):
        self.random_waiting.add(user)
        if len(self.random_waiting) > 1:
            turn = random.randint(0, 1)
            room_code = self.create_room()
            self.random_waiting.discard(user)
            player = self.random_waiting.pop()
            self.random_util(user, player, room_code)
            local_name = self.player_local[user]
            await self.list_conn[player].send_json({"type" : "username", "username": local_name})
            await self.list_conn[player].send_json({"type" : "matched", "status" : "Success", "room_code" : room_code, "turn" : int(not turn), "is_initiator" : True})
            return turn, room_code, self.player_local[player]
        else:
            return -1

    async def relay_to_opponent(self, room_code : str, sender : str, payload: dict):
        players = self.room_players.get(room_code)
        if not players:
            return
        opponent = players[1] if players[0] == sender else players[1]
        if opponent and opponent in self.list_conn:
            await self.list_conn[opponent].send_json(payload)

    def rejoin(self, room_code : str, user: str):
        if room_code not in self.rooms or room_code not in self.room_players:
            return -1
        pending_task = self.pending_disconnect.pop(user, -1)
        if isinstance(pending_task, int):
            return -1
        else:
            pending_task.cancel()
        return 0
manager =  ConnectionManager()

@app.websocket("/ws")
async def main_websoc(user : WebSocket):
    username = await manager.add_connection(user)
    try:
        while True:
            data = await asyncio.wait_for(user.receive_json(), timeout=TIMEOUT_SECONDS)
            if "type" in data:
                match data["type"]:
                    case "create_room":
                        room_code = manager.assign_room(username)
                        await user.send_json({"type" : "room_created", "room_code" : room_code, "status" : "success"})

                    case "join_room":
                        status = await manager.join_room(username, data["room_code"])
                        if isinstance(status, tuple) and status[0] == 0:
                            local_name = status[1]
                            room_code = status[2]
                            await user.send_json({"type" : "username", "username": local_name})
                            await user.send_json({"type" : "join_room", "status" : "success", "is_initiator" : False})
                        if status == -1:
                            await user.send_json({"type" : "join_room", "status" : "Invalid room code"})
                        elif status == -2:
                            await user.send_json({"type" : "join_room", "status" : "Room is full"})
                    case "find_random_match":
                        status = await manager.random_join(username)
                        if isinstance(status, int):
                            await user.send_json({"type" : "waiting_for_match", "status" : "Waiting for other players"})
                        else:
                            local_name = status[2]
                            await user.send_json({"type" : "username", "username": local_name})
                            await user.send_json({"type" : "matched", "status" : "Success", "room_code" : status[1], "turn" : status[0], "is_initiator" : False})
                            
                    case "voice_offer" | "voice_answer" | "voice_ice_candidate":
                        room_code = data["room_code"]
                        if room_code:
                            await manager.relay_to_opponent(room_code, username, data)

                    case "move":
                        room_code = data["room_code"]
                        move = data["dice_val"]
                        await manager.send_data(room_code, username, move)

                    case "rejoin":
                        room_code = data["room_code"]
                        status = manager.rejoin(room_code, username)
                        if status == -1:
                            await user.send_json({"type" : "move", "diceVal" : -4})
                        else:
                            await user.send_json({"type" : "rejoined"})
                            await manager.relay_to_opponent(room_code, username, {"type" : "rejoined_opponent"})
                    case "rejoin_data":
                        room_code = manager.player_room[username]
                        await manager.relay_to_opponent(room_code, username, data)
                        await user.send_json({"type" : "stop_loading"})
                    case "leave":
                        room_code = data["room_code"]
                        await manager.remove_connection(username, room_code)
                        break
                    case "game_over":
                        room_code = data["room_code"]
                        winner_name = data["winner"]
                        players = manager.room_players[room_code]
                        if players:
                            loser_name = players[1] if players[0] == winner_name else players[0]
                            try:
                                db = session()
                                record_result(db, winner_name, loser_name)
                            finally:
                                db.close()
                    case "max_wait_leave":
                        await manager.remove_connection(username, None, False)
                
    except Exception as e:
        print(e)
    finally:
        task = asyncio.create_task(manager.wait_before_disconnect(username, None))
        manager.pending_disconnect[username] = task


def record_result(db, winner_name: str | None, loser_name: str | None):
    for player_name, is_winner in ((winner_name, True), (loser_name, False)):
        if not player_name:
            continue
        user = db.query(User).filter(User.username == player_name).first()
        if not user:
            continue
        if is_winner:
            user.wins += 1
        else:
            user.losses += 1
    db.commit()

@app.get("/profile/{player_name}")
def user_data(player_name: str):
    db = session()
    try:
        user = db.query(User).filter(User.username == player_name).first()
        if not user:
            return {"error" : "Player details not found"}
        return {
            "local_name" : user.local_name,
            "wins" : user.wins,
            "losses" : user.losses
        }
    finally:
        db.close()

@app.get("/leaderboard")
def leaderboard_data():
    db = session()
    try:
        users = db.query(User).order_by(User.wins.desc()).limit(10).all()
        return users
    finally:
        db.close()