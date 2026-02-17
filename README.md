## Local run
1. In IDEA's top menu, select Run -> Edit Configurations...

2. Select your application.

3. In the Environment variables field, click the icon and add: 
```
DB_PASSWORD=my_password;DB_URL=jdbc:postgresql://localhost:5432/my_database;DB_USER=my_user;TELEGRAM_BOT_NAME=JoinMyTripBot;TELEGRAM_BOT_TOKEN=my_token
```

## Doker run
1. Run it through the terminal:
```
DB_NAME=my_database DB_USER=my_user DB_PASSWORD=my_password TELEGRAM_BOT_TOKEN=my_token TELEGRAM_BOT_NAME=JoinMyTripBot docker-compose up --build

```
