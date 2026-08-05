# Snake Ladder Game
## Motivation
I was learning android mobile app development in **Kotlin** and in my learning processes I completed the apps from the google android teaching program and when I completed I was seeing the simple apps that I created along the way and among them was an app called **dice roller** in which we just clicked a button and it displayed a random number between 1 and 6. I just thought of creating a nice dice roller so I searched for dice images used those to create a dice that actually shows rolling(though that is a simple animation using rotation to create the effect of rolling). After that I thought of creating ludo but that was very common so I accidentally remembered this snake ladder game I used to play in childhood and thus my journey began. Later after developing the basic game, I added nearby, online connections with voice IO and also the reconnection logic that bit was all inspired by learning to learn the logics at a lower level which I plan to use in my future projects hopefully.
## Features
- The game supports both landscape and portrait modes without losing information when activity is destroyed while rotating.
- You can talk to your opponent using mic, but even though while reconnecting voice reconnection is reestablished but that has less attempts than actual reconnection.
- Reconnecting between opponent and self in case connection is lost, after reconnecting data is fetched from the player that was online to sync. **Note that** reconnection is only supported under 15s and for opponent there is a drawback opponent has to wait a little longer if the disconnection is not rejoined due to trade offs made between reconnection time.
- You can also play with a robot.
- You can change your username.
- The online matches stats are displayed in profile area  fetched from database
- There is a leaderboard where top players are displayed.
- The turns take timer, if time runs out player losses.
- Room functionality in online connections to play with your friends.
- If a player resets a game, they are forfeiting and thus lose the game.
- For celebrations, there is fireworks display.

## Tech
- For the app **Kotlin** with **XML** is used, it only supports android phones thus, **okhttp** is used for communication with backend.
- The **backend** of the application is written in **Fastapi** with **Sqlalchemy** using **Sql lite** for database to store players data.

## Journey
The journey of creating this app was smooth at first as I knew everything I was creating like I did not need to use AI, but then I had to learn about voice connections for nearby devices and WebRTC for online connections, getting device stats to know if bluetooth and location services are enabled, but all that was fine. Main difficulty was when I decided to add reconnection  logic and it was a nightmare and I still cannot say current reconnection logic is best as it has faults too. But I learned a lot and yes I had to finally use AI for my reconnection logic and sorting out some stuff as I did not know much about the android design patterns. Overall the journey was a good learning curve teaching me things I did not know.

### AI Usage
AI was used as helping tool in reconnection logic debugging and using viewmodel standard for android. All development was done by me except some drawables(resources, svgs) which were truly converted from images to vectors using AI tools.
