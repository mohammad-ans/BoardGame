from fastapi import FastAPI, WebSocket
from fastapi.middleware.cors import CORSMiddleware
import random
app = FastAPI()

app.add_middleware(
    CORSMiddleware,
        allow_origins=["*"],
        allow_methods=["*"],
        allow_credentials=True,
        allow_headers=["*"]
)

class ConnectionManager:
    def __init__(self):
        self.rooms: set[str] = set()
        self.list_conn: dict[str, WebSocket] = {}
        self.random_waiting: set[str] = set()
        self.room_players: dict[str, tuple] = dict()
        self.player_room: dict[str, str] = dict()

    async def add_connection(self, conn : WebSocket):
        await conn.accept()
        message = await conn.receive_json()
        user = message["username"]
        self.list_conn[user] = conn
        return user

    def assign_room(self, user : str):
        room_code = self.create_room()
        self.room_players[room_code] = (user, None)
        self.player_room[user] = room_code
        return room_code

    def join_room(self, user : str, room_code : str):
        if room_code not in self.room_players:
            return -1
        players = self.room_players[room_code]
        if not isinstance(players, tuple) or len(players) < 2 or players[1]:
            return -2
        self.room_players[room_code] = (players[0], user)
        self.player_room[user] = room_code
        return 0
    
    def random_util(self, user1 : str, user2 : str, room_code : str):
        self.room_players[room_code] = (user1, user2)
        self.player_room[user1] = room_code
        self.player_room[user2] = room_code

    async def remove_connection(self, user : str, room_code : str | None):
        try:
            self.list_conn.pop(user)
            if user in self.random_waiting:
                self.random_waiting.discard(user)
            if not room_code:
                if user in self.player_room:
                    room_code = self.player_room[user]
                else:
                    return

            players = self.room_players[room_code]
            if players[0] == user and players[1]:
                await self.list_conn[players[1]].send_json({"leave": "User left"})
            elif players[0]:
                await self.list_conn[players[0]].send_json({"leave": "User left"})

            self.room_players.pop(room_code)
            self.player_room.pop(players[0], "")
            self.player_room.pop(players[1], "")
            self.rooms.discard(room_code)
            return 0
        
        except:
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
        if len(self.random_waiting) > 0:
            room_code = self.create_room()
            player = self.random_waiting.pop()
            self.random_util(user, player, room_code)
            await self.list_conn[player].send_json({"type" : "matched", "status" : "Success", "room_code" : room_code})
            return 0, room_code
        else:
            self.random_waiting.add(user)
            return -1
            
manager =  ConnectionManager()

@app.websocket("/ws")
async def main_websoc(user : WebSocket):
    username = await manager.add_connection(user)
    try:
        while True:
            data = await user.receive_json()
            if "type" in data:
                match data["type"]:

                    case "create_room":
                        room_code = manager.assign_room(username)
                        await user.send_json({"type" : "room_created", "room_code" : room_code, "status" : "success"})

                    case "join_room":
                        status = manager.join_room(username, data["room_code"])
                        if status == 0:
                            await user.send_json({"type" : "join_room", "status" : "success"})
                        elif status == -1:
                            await user.send_json({"type" : "join_room", "status" : "Invalid room code"})
                        elif status == -2:
                            await user.send_json({"type" : "join_room", "status" : "Room is full"})

                    case "find_random_match":
                        status = await manager.random_join(username)
                        if status == -1:
                            await user.send_json({"type" : "waiting_for_match", "status" : "Waiting for other players"})
                        else:
                            await user.send_json({"type" : "matched", "status" : "Success", "room_code" : status[1]})

                    case "move":
                        room_code = data["room_code"]
                        move = data["move"]
                        await manager.send_data(room_code, username, move)

                    case "leave":
                        room_code = data["room_code"]
                        await manager.remove_connection(username, room_code)
                        break
                    
    except Exception as e:
        print(e)
        await manager.remove_connection(username, None)