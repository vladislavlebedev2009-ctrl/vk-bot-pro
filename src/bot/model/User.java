package bot.model;

/**
 * Иммутабельный снимок строки таблицы users.
 * Полный профиль пользователя, включая статистику VK Bot PRO 2.0.
 */
public class User {

    private final long id;

    private final int balance;

    private final int rep;

    private final String rank;

    private final boolean banned;

    private final long muteEnd;

    private final int warns;

    private final String name;

    private final String callSign;

    private final long registeredAt;

    private final long lastActivity;

    private final int messagesCount;

    private final int commandsCount;

    private final int punishmentsReceived;

    private final int punishmentsGiven;


    public User(
            long id,
            int balance,
            int rep,
            String rank,
            boolean banned,
            long muteEnd,
            int warns,
            String name,
            String callSign,
            long registeredAt,
            long lastActivity,
            int messagesCount,
            int commandsCount,
            int punishmentsReceived,
            int punishmentsGiven
    ) {

        this.id =
                id;

        this.balance =
                balance;

        this.rep =
                rep;

        this.rank =
                rank;

        this.banned =
                banned;

        this.muteEnd =
                muteEnd;

        this.warns =
                warns;

        this.name =
                name;

        this.callSign =
                callSign;

        this.registeredAt =
                registeredAt;

        this.lastActivity =
                lastActivity;

        this.messagesCount =
                messagesCount;

        this.commandsCount =
                commandsCount;

        this.punishmentsReceived =
                punishmentsReceived;

        this.punishmentsGiven =
                punishmentsGiven;
    }


    public long getId() {

        return id;
    }

    public int getBalance() {

        return balance;
    }

    public int getRep() {

        return rep;
    }

    public String getRank() {

        return rank;
    }

    public Rank getRankEnum() {

        return Rank.fromString(
                rank
        );
    }

    public boolean isBanned() {

        return banned;
    }

    public long getMuteEnd() {

        return muteEnd;
    }

    public boolean isMuted() {

        return muteEnd
                > System.currentTimeMillis();
    }

    public int getWarns() {

        return warns;
    }

    public String getName() {

        return name;
    }

    public String getCallSign() {

        return callSign;
    }

    public long getRegisteredAt() {

        return registeredAt;
    }

    public long getLastActivity() {

        return lastActivity;
    }

    public int getMessagesCount() {

        return messagesCount;
    }

    public int getCommandsCount() {

        return commandsCount;
    }

    public int getPunishmentsReceived() {

        return punishmentsReceived;
    }

    public int getPunishmentsGiven() {

        return punishmentsGiven;
    }
}